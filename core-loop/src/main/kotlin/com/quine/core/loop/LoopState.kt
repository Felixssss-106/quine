package com.quine.core.loop

import com.quine.core.common.QuineError
import com.quine.core.gateway.ToolCallRequest

/**
 * agent 状态机（agent-prompt.md §2.3）。
 *
 * `Idle → Planning → Streaming → ToolCall → …（循环）… → Done / Failed / Cancelled`
 *
 * `WaitingApproval` 在 M0 只定义不进入 —— 审批矩阵（信任档位 × 危险级）在 M1 接上 UI。
 */
sealed interface LoopState {
    data object Idle : LoopState

    data object Planning : LoopState

    data object Streaming : LoopState

    data object ToolCall : LoopState

    data class WaitingApproval(val toolId: String, val title: String) : LoopState

    data object Done : LoopState

    data object Cancelled : LoopState

    data class Failed(val error: QuineError) : LoopState
}

/**
 * 流出给 UI 的事件。UI 只做渲染，不做编排。
 *
 * 工具事件带 `callId`：同一条回复里可能连续调用多个工具，
 * UI 靠它把「开始 / 结束」配对成一张活动卡。
 */
sealed interface LoopEvent {
    data class StateChanged(val state: LoopState) : LoopEvent

    /** 模型流式吐字。 */
    data class TextDelta(val text: String) : LoopEvent

    data class ToolStarted(
        val callId: String,
        val toolId: String,
        val title: String,
        /** 模型给的原始参数串，供活动卡展示与摘要使用。 */
        val argumentsJson: String,
    ) : LoopEvent

    data class ToolFinished(
        val callId: String,
        val toolId: String,
        val ok: Boolean,
        val output: String,
        val sourceRef: String?,
        val truncated: Boolean,
    ) : LoopEvent

    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
    ) : LoopEvent

    /**
     * 一轮模型输出结束。
     *
     * 这条事件是给持久化用的：`toolCalls` 里的参数只有在这里才完整，
     * 而且它必须在工具结果之前落库，否则重开会话时消息顺序会错。
     */
    data class AssistantTurn(
        val text: String,
        val toolCalls: List<ToolCallRequest>,
        val reason: String?,
    ) : LoopEvent

    data class Failed(val error: QuineError) : LoopEvent

    data object Cancelled : LoopEvent
}
