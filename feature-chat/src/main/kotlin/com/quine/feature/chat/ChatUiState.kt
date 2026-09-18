package com.quine.feature.chat

import com.quine.core.common.QuineError
import com.quine.core.common.TrustLevel
import com.quine.core.storage.ChatMessage

data class UsageTotals(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

/**
 * 活动卡（agent-prompt.md §4.2）：一行摘要，可展开；结束后自动折叠。
 * `summary` 一律人话标题，见 [ActivitySummary]。
 */
data class ActivityCard(
    val callId: String,
    val toolId: String,
    val title: String,
    val summary: String,
    val detail: String,
    val arguments: String,
    val running: Boolean,
    val ok: Boolean,
    val sourceRef: String?,
    val truncated: Boolean,
    val expanded: Boolean = false,
)

/** 正在进行的这一轮（尚未落库的部分）。 */
data class LiveTurn(
    val text: String = "",
    val activity: ActivityCard? = null,
)

data class ChatUiState(
    val loaded: Boolean = false,
    val conversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val live: LiveTurn? = null,
    /**
     * 已经落库、但此刻仍由 `live` 渲染的那条 assistant 消息 id。
     * 落库与 UI 更新之间有极小的时间差，靠它避免同一段文字重影。
     */
    val liveAssistantId: String? = null,
    val draft: String = "",
    val streaming: Boolean = false,
    val trustLevel: TrustLevel = TrustLevel.STANDARD,
    val providerReady: Boolean = true,
    val error: QuineError? = null,
    val stopped: Boolean = false,
    val usage: UsageTotals = UsageTotals(),
    /** 最近一次发送的用户输入，供「重试 / 编辑」用。 */
    val lastUserInput: String? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && !streaming && loaded

    /** 渲染用：把仍由 live 承载的那条落库消息藏起来，避免重影。 */
    val visibleMessages: List<ChatMessage>
        get() = liveAssistantId?.let { id -> messages.filterNot { it.id == id } } ?: messages
}
