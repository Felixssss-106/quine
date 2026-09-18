package com.quine.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quine.core.common.QuineError
import com.quine.core.common.TrustLevel
import com.quine.core.gateway.ChatRole
import com.quine.core.gateway.LlmErrors
import com.quine.core.gateway.LlmMessage
import com.quine.core.loop.AgentLoop
import com.quine.core.loop.LoopEvent
import com.quine.core.loop.LoopState
import com.quine.core.storage.ChatMessage
import com.quine.core.storage.MessageMeta
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 聊天面的状态机（docs/page-specs.md §3、agent-prompt.md §4.6）。
 *
 * 三件事值得单独说：
 * 1. **流式节流**：模型吐字按事件到达，但 UI 按 80ms/块 刷新（motion-spec §2.8），
 *    中间先进 [incoming] 缓冲，由 ticker 定时搬到 `live.text`。
 * 2. **重影抑制**：一轮结束时先把 assistant 消息落库，落库到 UI 拿到之间用
 *    `liveAssistantId` 把那条藏起来，避免同一段文字出现两次。
 * 3. **「已完成的部分还在」**：失败或被停止时，把已经吐出来的那截也落库。
 */
class ChatViewModel(private val deps: ChatDeps) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val incoming = StringBuilder()

    private var conversationId: String? = null
    private var loop: AgentLoop? = null
    private var runJob: Job? = null
    private var tickerJob: Job? = null
    private var draftJob: Job? = null

    private var outcome: RunOutcome = RunOutcome.Done

    private enum class RunOutcome { Done, Cancelled, Failed }

    init {
        viewModelScope.launch {
            val settings = deps.settings.first()
            val conversation = deps.ensureConversation(settings.activeConversationId)
            conversationId = conversation.id
            deps.setActiveConversationId(conversation.id)
            _state.update {
                it.copy(
                    loaded = true,
                    conversationId = conversation.id,
                    draft = settings.draft,
                    trustLevel = settings.trustLevel,
                    providerReady = settings.providerReady,
                )
            }

            launch {
                deps.settings.collect { latest ->
                    _state.update {
                        it.copy(trustLevel = latest.trustLevel, providerReady = latest.providerReady)
                    }
                }
            }

            deps.observeMessages(conversation.id).collect(::onMessages)
        }
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text) }
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MILLIS)
            deps.saveDraft(text)
        }
    }

    fun send() {
        val current = _state.value
        val convId = conversationId ?: return
        val text = current.draft.trim()
        if (text.isEmpty() || current.streaming || !current.loaded) return

        if (!current.providerReady) {
            _state.update { it.copy(error = LlmErrors.missingKey()) }
            return
        }

        runJob?.cancel()
        runJob = viewModelScope.launch {
            try {
                // 先取历史再落库，避免把刚写入的这条用户消息重复喂给模型。
                val history = deps.history(convId)
                deps.appendMessage(convId, ChatRole.USER, text)
                deps.saveDraft("")
                startRun(history, input = text, convId = convId)
                finishRun(convId)
            } catch (error: Throwable) {
                failRun(error)
            }
        }
    }

    /** 重试：不重复追加用户消息，直接用现有历史再跑一遍。 */
    fun retry() {
        val convId = conversationId ?: return
        if (_state.value.streaming) return

        runJob?.cancel()
        runJob = viewModelScope.launch {
            try {
                val history = deps.history(convId)
                if (history.isEmpty()) return@launch
                _state.update { it.copy(error = null) }
                startRun(history, input = null, convId = convId)
                finishRun(convId)
            } catch (error: Throwable) {
                failRun(error)
            }
        }
    }

    /** 编辑：把最后一条用户消息退回输入框，并清掉它之后的全部内容。 */
    fun editLast() {
        val convId = conversationId ?: return
        if (_state.value.streaming) return

        viewModelScope.launch {
            val messages = _state.value.messages
            val index = messages.indexOfLast { it.isUser }
            if (index < 0) return@launch
            val target = messages[index]
            messages.drop(index + 1).forEach { deps.deleteMessage(it.id) }
            deps.deleteMessage(target.id)
            _state.update { it.copy(error = null) }
            onDraftChange(target.text)
        }
    }

    /** 停止：交给 loop 处理取消语义，事件流会正常收束为「已停止」。 */
    fun stop() {
        loop?.cancel()
    }

    fun toggleActivity() {
        _state.update { current ->
            val live = current.live ?: return@update current
            val activity = live.activity ?: return@update current
            current.copy(live = live.copy(activity = activity.copy(expanded = !activity.expanded)))
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null, stopped = false) }
    }

    fun setTrustLevel(level: TrustLevel) {
        _state.update { it.copy(trustLevel = level) }
        viewModelScope.launch { deps.setTrustLevel(level) }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        draftJob?.cancel()
        loop?.cancel()
        super.onCleared()
    }

    // ---- 内部 ----

    private fun onMessages(messages: List<ChatMessage>) {
        _state.update { current ->
            val pendingId = current.liveAssistantId
            if (pendingId != null && messages.any { it.id == pendingId }) {
                // 落库那条已经能渲染了：撤掉 live 里的影子，避免重影。
                current.copy(
                    messages = messages,
                    liveAssistantId = null,
                    live = current.live?.copy(text = ""),
                )
            } else {
                current.copy(messages = messages)
            }
        }
    }

    private suspend fun startRun(history: List<LlmMessage>, input: String?, convId: String) {
        incoming.clear()
        outcome = RunOutcome.Done
        _state.update {
            it.copy(
                streaming = true,
                stopped = false,
                error = null,
                draft = if (input != null) "" else it.draft,
                live = LiveTurn(),
                liveAssistantId = null,
                lastUserInput = input ?: it.lastUserInput,
            )
        }
        startTicker()

        val agentLoop = deps.createLoop()
        loop = agentLoop
        try {
            val events = if (input != null) {
                agentLoop.run(history, input, deps.toolContext())
            } else {
                agentLoop.runConversation(history, deps.toolContext())
            }
            events.collect { handleEvent(it, convId) }
        } finally {
            stopTicker()
            flushIncoming()
            loop = null
        }
    }

    private suspend fun finishRun(convId: String) {
        val current = _state.value
        val liveText = current.live?.text.orEmpty()

        // 「已完成的部分还在」：没跑完的那一轮，把已吐出的文字也落库。
        if (outcome != RunOutcome.Done && current.liveAssistantId == null && liveText.isNotEmpty()) {
            deps.appendMessage(convId, ChatRole.ASSISTANT, liveText)
        }

        _state.update {
            it.copy(
                streaming = false,
                live = null,
                liveAssistantId = null,
                stopped = outcome == RunOutcome.Cancelled,
            )
        }
    }

    private suspend fun failRun(error: Throwable) {
        outcome = RunOutcome.Failed
        _state.update { it.copy(error = QuineError.unknown(error), streaming = false, live = null) }
    }

    private suspend fun handleEvent(event: LoopEvent, convId: String) {
        when (event) {
            is LoopEvent.TextDelta -> incoming.append(event.text)

            is LoopEvent.StateChanged -> when (event.state) {
                LoopState.Done -> outcome = RunOutcome.Done
                LoopState.Cancelled -> outcome = RunOutcome.Cancelled
                is LoopState.Failed -> outcome = RunOutcome.Failed
                else -> Unit
            }

            is LoopEvent.AssistantTurn -> {
                val saved = deps.appendMessage(
                    conversationId = convId,
                    role = ChatRole.ASSISTANT,
                    text = event.text,
                    toolCalls = event.toolCalls,
                )
                _state.update { it.copy(liveAssistantId = saved.id) }
            }

            is LoopEvent.ToolStarted -> _state.update { current ->
                current.copy(
                    live = (current.live ?: LiveTurn()).copy(
                        activity = ActivityCard(
                            callId = event.callId,
                            toolId = event.toolId,
                            title = event.title,
                            summary = "正在${event.title}",
                            detail = "",
                            arguments = event.argumentsJson,
                            running = true,
                            ok = true,
                            sourceRef = null,
                            truncated = false,
                            expanded = current.live?.activity?.expanded ?: false,
                        ),
                    ),
                )
            }

            is LoopEvent.ToolFinished -> {
                val previous = _state.value.live?.activity?.takeIf { it.callId == event.callId }
                val arguments = previous?.arguments.orEmpty()
                val title = previous?.title ?: event.toolId
                val expanded = previous?.expanded ?: false
                val summary = ActivitySummary.describe(
                    toolId = event.toolId,
                    argumentsJson = arguments,
                    ok = event.ok,
                    truncated = event.truncated,
                )

                deps.appendMessage(
                    conversationId = convId,
                    role = ChatRole.TOOL,
                    text = event.output,
                    toolCallId = event.callId,
                    toolName = event.toolId,
                    meta = MessageMeta(
                        ok = event.ok,
                        sourceRef = event.sourceRef,
                        truncated = event.truncated,
                        summary = summary,
                        arguments = arguments,
                    ),
                )

                _state.update { current ->
                    current.copy(
                        live = (current.live ?: LiveTurn()).copy(
                            activity = ActivityCard(
                                callId = event.callId,
                                toolId = event.toolId,
                                title = title,
                                summary = summary,
                                detail = event.output,
                                arguments = arguments,
                                running = false,
                                ok = event.ok,
                                sourceRef = event.sourceRef,
                                truncated = event.truncated,
                                expanded = expanded,
                            ),
                        ),
                    )
                }
            }

            is LoopEvent.Usage -> _state.update { current ->
                current.copy(
                    usage = UsageTotals(
                        promptTokens = current.usage.promptTokens + event.promptTokens,
                        completionTokens = current.usage.completionTokens + event.completionTokens,
                        totalTokens = current.usage.totalTokens + event.totalTokens,
                    ),
                )
            }

            is LoopEvent.Failed -> {
                outcome = RunOutcome.Failed
                _state.update { it.copy(error = event.error) }
            }

            LoopEvent.Cancelled -> outcome = RunOutcome.Cancelled
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(STREAM_TICK_MILLIS)
                // 这一轮已经收束就别再空转（收尾时还会补一次 flush）。
                if (!_state.value.streaming) break
                flushIncoming()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun flushIncoming() {
        if (incoming.isEmpty()) return
        val chunk = incoming.toString()
        incoming.clear()
        _state.update { current ->
            val live = current.live ?: return@update current
            current.copy(live = live.copy(text = live.text + chunk))
        }
    }

    companion object {
        /** 流式显现节奏（docs/motion-spec.md §2.8）：80ms/块。 */
        const val STREAM_TICK_MILLIS = 80L

        private const val DRAFT_DEBOUNCE_MILLIS = 300L

        fun factory(deps: ChatDeps): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(deps) }
        }
    }
}
