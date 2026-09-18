package com.quine.feature.chat

import com.quine.core.common.TrustLevel
import com.quine.core.gateway.ChatRole
import com.quine.core.gateway.LlmMessage
import com.quine.core.gateway.ToolCallRequest
import com.quine.core.loop.AgentLoop
import com.quine.core.storage.ChatMessage
import com.quine.core.storage.Conversation
import com.quine.core.storage.MessageMeta
import com.quine.core.storage.QuineSettings
import com.quine.core.tools.ToolContext
import kotlinx.coroutines.flow.Flow

/**
 * 聊天面需要的外部能力。由 app 层实现 —— feature 不依赖 app。
 */
interface ChatDeps {

    val settings: Flow<QuineSettings>

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

    suspend fun ensureConversation(preferredId: String?): Conversation

    /** 交给 loop 的历史，按时间升序。 */
    suspend fun history(conversationId: String): List<LlmMessage>

    suspend fun appendMessage(
        conversationId: String,
        role: ChatRole,
        text: String,
        toolCalls: List<ToolCallRequest> = emptyList(),
        toolCallId: String? = null,
        toolName: String? = null,
        meta: MessageMeta = MessageMeta(),
    ): ChatMessage

    suspend fun deleteMessage(id: String)

    /** 每次发送都拿一个全新的 loop（loop 内部带取消状态，不复用）。 */
    suspend fun createLoop(): AgentLoop

    suspend fun toolContext(): ToolContext

    suspend fun setActiveConversationId(id: String)

    suspend fun saveDraft(text: String)

    suspend fun setTrustLevel(level: TrustLevel)
}
