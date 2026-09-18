package com.quine.core.loop

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.gateway.ChatRole
import com.quine.core.gateway.LlmEvent
import com.quine.core.gateway.LlmException
import com.quine.core.gateway.LlmMessage
import com.quine.core.gateway.LlmProvider
import com.quine.core.gateway.LlmRequest
import com.quine.core.gateway.RetryPolicy
import com.quine.core.gateway.ToolCallAssembler
import com.quine.core.gateway.ToolCallRequest
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 最小 agent loop（agent-prompt.md §2.3）。
 *
 * 职责：状态机 · 流式事件转发 · 工具调用编排 · 取消语义 · 重试预算 · 上下文滑动窗口。
 *
 * 两条刻意的设计：
 * 1. **重试的唯一权威在这里**。装配时请把 provider 工厂的重试预算设为 0
 *    （`RetryPolicy(maxRetries = 0)`），否则两层重试会相乘，一次请求放大成 9 次。
 * 2. **不感知持久化**。loop 只吃 `history` 吐事件，落库由 app 层接线 —— 这样
 *    它可以用假 provider 纯 JVM 单测。
 */
class AgentLoop(
    private val provider: LlmProvider,
    private val tools: ToolRegistry,
    private val config: LoopConfig = LoopConfig(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    private val cancelRequested = AtomicBoolean(false)

    @Volatile
    private var currentTurn: Job? = null

    /** 任何状态都可取消（§2.3）。取消只杀当前这一轮流，外层仍能送出「已停止」。 */
    fun cancel() {
        cancelRequested.set(true)
        currentTurn?.cancel()
    }

    fun run(
        history: List<LlmMessage>,
        input: String,
        context: ToolContext,
    ): Flow<LoopEvent> = runConversation(
        conversation = history + LlmMessage(ChatRole.USER, input),
        context = context,
    )

    /**
     * 用一段**已经包含用户消息**的完整对话开跑。
     * 重试时用它：重试不该再追加一条用户消息。
     */
    fun runConversation(
        conversation: List<LlmMessage>,
        context: ToolContext,
    ): Flow<LoopEvent> = channelFlow {
        cancelRequested.set(false)
        currentTurn = null

        val working = conversation.toMutableList()
        var rounds = 0

        suspend fun emitCancelled() {
            send(LoopEvent.Cancelled)
            send(LoopEvent.StateChanged(LoopState.Cancelled))
        }

        suspend fun emitFailed(error: QuineError) {
            send(LoopEvent.Failed(error))
            send(LoopEvent.StateChanged(LoopState.Failed(error)))
        }

        send(LoopEvent.StateChanged(LoopState.Planning))

        while (true) {
            if (cancelRequested.get()) {
                emitCancelled()
                return@channelFlow
            }

            if (rounds >= config.maxToolRounds) {
                emitFailed(
                    QuineError(
                        kind = ErrorKind.TOOL,
                        message = "工具调用轮次太多，我停下了。",
                        impact = "这次任务没有跑完。",
                        nextStep = "把任务拆小一点再发，或者直接说清你想要的结果。",
                    ),
                )
                return@channelFlow
            }

            send(LoopEvent.StateChanged(LoopState.Streaming))

            // ---- 一轮模型输出（含重试预算） ----
            var attempt = 0
            var text = ""
            var toolCalls: List<ToolCallRequest> = emptyList()
            var finishReason: String? = null
            var failure: QuineError? = null
            var cancelled = false

            while (true) {
                val buffer = StringBuilder()
                val assembler = ToolCallAssembler()
                var emitted = false
                var thrown: Throwable? = null
                finishReason = null
                // 每一轮都要清空上一轮的错误，否则重试成功后会带着旧错误收场。
                failure = null

                val request = LlmRequest(
                    messages = buildMessages(working),
                    tools = tools.schemas(),
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                )

                val turn = launch {
                    try {
                        provider.stream(request).collect { event ->
                            when (event) {
                                is LlmEvent.TextDelta -> {
                                    emitted = true
                                    buffer.append(event.text)
                                    send(LoopEvent.TextDelta(event.text))
                                }

                                is LlmEvent.ToolCallDelta -> {
                                    emitted = true
                                    assembler.accept(event)
                                }

                                is LlmEvent.Usage -> send(
                                    LoopEvent.Usage(
                                        promptTokens = event.promptTokens,
                                        completionTokens = event.completionTokens,
                                        totalTokens = event.totalTokens,
                                    ),
                                )

                                is LlmEvent.Finish -> finishReason = event.reason
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        thrown = error
                    }
                }

                currentTurn = turn
                turn.join()
                currentTurn = null

                if (turn.isCancelled || cancelRequested.get()) {
                    cancelled = true
                    break
                }

                val error = (thrown as? LlmException)?.error
                    ?: thrown?.let { QuineError.unknown(it) }

                if (error == null) {
                    text = buffer.toString()
                    toolCalls = assembler.build()
                    break
                }

                failure = error
                // 只有「一个字都没吐出来」才敢重放，否则会在界面上留下重复内容。
                if (!emitted && attempt < config.maxTurnRetries && retryPolicy.shouldRetry(error, attempt)) {
                    delay(retryPolicy.delayMillis(attempt))
                    attempt++
                    continue
                }
                break
            }

            if (cancelled) {
                emitCancelled()
                return@channelFlow
            }

            failure?.let {
                emitFailed(it)
                return@channelFlow
            }

            // ---- 一轮模型输出结束：先把这一轮交给上层落库，再决定要不要调工具 ----
            send(LoopEvent.AssistantTurn(text = text, toolCalls = toolCalls, reason = finishReason))

            // ---- 没有工具调用：这一轮就是最终答复 ----
            if (toolCalls.isEmpty()) {
                working += LlmMessage(ChatRole.ASSISTANT, content = text)
                send(LoopEvent.StateChanged(LoopState.Done))
                return@channelFlow
            }

            // ---- 有工具调用：执行并把结果回灌，进入下一轮 ----
            working += LlmMessage(
                role = ChatRole.ASSISTANT,
                content = text.ifEmpty { null },
                toolCalls = toolCalls,
            )
            send(LoopEvent.StateChanged(LoopState.ToolCall))

            for (call in toolCalls) {
                if (cancelRequested.get()) {
                    emitCancelled()
                    return@channelFlow
                }
                val tool = tools.find(call.name)
                send(
                    LoopEvent.ToolStarted(
                        callId = call.id,
                        toolId = call.name,
                        title = tool?.spec?.title ?: call.name,
                        argumentsJson = call.argumentsJson,
                    ),
                )
                val result = tools.execute(call.name, call.argumentsJson, context)
                send(
                    LoopEvent.ToolFinished(
                        callId = call.id,
                        toolId = call.name,
                        ok = result.ok,
                        output = result.output,
                        sourceRef = result.sourceRef,
                        truncated = result.truncated,
                    ),
                )
                // 失败也照样回灌：让模型看到错误文本，自己改参数重试。
                working += LlmMessage(
                    role = ChatRole.TOOL,
                    content = result.output,
                    toolCallId = call.id,
                    name = call.name,
                )
            }

            rounds++
        }
    }

    private fun buildMessages(conversation: List<LlmMessage>): List<LlmMessage> =
        listOf(LlmMessage(ChatRole.SYSTEM, config.systemPrompt)) + window(conversation)

    /**
     * 上下文滑动窗口：保留最近 N 条。
     *
     * 若窗口首条是 `TOOL`，说明它的 `assistant.tool_calls` 已经被裁掉了，
     * 这会构成「有工具结果、没有对应调用」的非法序列 —— 继续往前丢，直到首条不是 TOOL。
     */
    private fun window(conversation: List<LlmMessage>): List<LlmMessage> {
        if (conversation.size <= config.contextWindowMessages) return conversation
        val tail = conversation.takeLast(config.contextWindowMessages)
        val trimmed = tail.dropWhile { it.role == ChatRole.TOOL }
        // 极端情况下整窗都是 TOOL：至少保住最后一条，别让请求里没有用户消息。
        return trimmed.ifEmpty { listOf(conversation.last()) }
    }
}
