package com.quine.feature.chat

import com.quine.core.common.QuineError
import com.quine.core.common.TrustLevel
import com.quine.core.gateway.ChatRole
import com.quine.core.gateway.LlmEvent
import com.quine.core.gateway.LlmException
import com.quine.core.gateway.LlmMessage
import com.quine.core.gateway.LlmProvider
import com.quine.core.gateway.LlmRequest
import com.quine.core.gateway.RetryPolicy
import com.quine.core.gateway.ToolCallRequest
import com.quine.core.loop.AgentLoop
import com.quine.core.loop.LoopConfig
import com.quine.core.storage.ChatMessage
import com.quine.core.storage.Conversation
import com.quine.core.storage.MessageMeta
import com.quine.core.storage.QuineSettings
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolRegistry
import com.quine.core.tools.builtin.FsReadTool
import com.quine.core.tools.workspace.PrivateWorkspace
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.io.File

/** 按脚本产出的假 provider。 */
class FakeProvider : LlmProvider {

    sealed interface Turn {
        data class Emit(val events: List<LlmEvent>) : Turn

        data class Fail(val error: QuineError) : Turn

        data class EmitThenFail(val events: List<LlmEvent>, val error: QuineError) : Turn

        data class Hang(val events: List<LlmEvent>) : Turn
    }

    private val script = ArrayDeque<Turn>()

    val requests: MutableList<LlmRequest> = mutableListOf()

    fun enqueue(turn: Turn): FakeProvider = apply { script.addLast(turn) }

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        requests += request
        when (val turn = script.removeFirstOrNull() ?: Turn.Emit(emptyList())) {
            is Turn.Emit -> turn.events.forEach { emit(it) }
            is Turn.Fail -> throw LlmException(turn.error)
            is Turn.EmitThenFail -> {
                turn.events.forEach { emit(it) }
                throw LlmException(turn.error)
            }

            is Turn.Hang -> {
                turn.events.forEach { emit(it) }
                awaitCancellation()
            }
        }
    }

    override suspend fun checkKey(): Result<Unit> = Result.success(Unit)
}

/** 内存版 ChatDeps：把 Room / DataStore 换成列表与 MutableStateFlow。 */
class FakeChatDeps(
    private val provider: FakeProvider,
    private val workspaceRoot: File,
) : ChatDeps {

    private val stored = mutableListOf<ChatMessage>()
    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val settingsFlow = MutableStateFlow(QuineSettings(onboarded = true))

    var savedDraft: String = ""
        private set

    var trustLevel: TrustLevel = TrustLevel.STANDARD
        private set

    val persisted: List<ChatMessage> get() = stored.toList()

    override val settings: Flow<QuineSettings> = settingsFlow

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = messages

    override suspend fun ensureConversation(preferredId: String?): Conversation =
        Conversation(id = CONVERSATION_ID, title = "新对话", createdAt = 0, updatedAt = 0, pinned = false)

    override suspend fun history(conversationId: String): List<LlmMessage> =
        stored.map { it.toLlmMessage() }

    override suspend fun appendMessage(
        conversationId: String,
        role: ChatRole,
        text: String,
        toolCalls: List<ToolCallRequest>,
        toolCallId: String?,
        toolName: String?,
        meta: MessageMeta,
    ): ChatMessage {
        val message = ChatMessage(
            id = "m${stored.size + 1}",
            conversationId = conversationId,
            role = role,
            text = text,
            toolCalls = toolCalls,
            toolCallId = toolCallId,
            toolName = toolName,
            meta = meta,
            createdAt = stored.size.toLong(),
        )
        stored += message
        messages.value = stored.toList()
        return message
    }

    override suspend fun deleteMessage(id: String) {
        stored.removeAll { it.id == id }
        messages.value = stored.toList()
    }

    override suspend fun createLoop(): AgentLoop = AgentLoop(
        provider = provider,
        tools = ToolRegistry(listOf(FsReadTool())),
        config = LoopConfig(),
        retryPolicy = RetryPolicy(),
    )

    override suspend fun toolContext(): ToolContext =
        ToolContext(
            workspace = PrivateWorkspace(workspaceRoot),
            // 聊天面用例不写文件；拒绝式快照可确保万一有写入会被拦下。
            snapshots = com.quine.core.tools.DenyingSnapshots,
        )

    override suspend fun setActiveConversationId(id: String) = Unit

    override suspend fun saveDraft(text: String) {
        savedDraft = text
    }

    override suspend fun setTrustLevel(level: TrustLevel) {
        trustLevel = level
    }

    companion object {
        const val CONVERSATION_ID = "conversation-1"
    }
}
