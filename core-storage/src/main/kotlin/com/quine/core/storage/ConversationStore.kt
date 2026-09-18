package com.quine.core.storage

import com.quine.core.common.Ids
import com.quine.core.common.QuineDispatchers
import com.quine.core.common.SystemTime
import com.quine.core.common.TimeProvider
import com.quine.core.gateway.ChatRole
import com.quine.core.gateway.LlmMessage
import com.quine.core.gateway.ToolCallRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
)

/** UI 用的消息模型：比 [MessageEntity] 多一层类型安全，比 [LlmMessage] 多一层持久化元数据。 */
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: ChatRole,
    val text: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val meta: MessageMeta = MessageMeta(),
    val createdAt: Long,
) {
    val isUser: Boolean get() = role == ChatRole.USER

    val isAssistant: Boolean get() = role == ChatRole.ASSISTANT

    val isTool: Boolean get() = role == ChatRole.TOOL

    fun toLlmMessage(): LlmMessage = LlmMessage(
        role = role,
        content = text.ifEmpty { null },
        toolCalls = toolCalls,
        toolCallId = toolCallId,
        name = toolName,
    )
}

/**
 * 会话与消息的读写入口。core-loop 不认识这一层 —— 历史由 app 层取出后
 * 作为参数传进 loop，loop 只负责吐事件。
 */
class ConversationStore(
    private val dao: ConversationDao,
    private val time: TimeProvider = SystemTime,
    @Suppress("unused") private val dispatchers: QuineDispatchers = QuineDispatchers(),
) {

    fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { list -> list.map { it.toDomain() } }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { list -> list.map { it.toDomain() } }

    /** 冷启动时用：优先恢复上次的会话，没有就新建一个。 */
    suspend fun ensureConversation(preferredId: String?, title: String = DEFAULT_TITLE): Conversation {
        preferredId?.let { dao.findConversation(it) }?.let { return it.toDomain() }
        dao.latestConversation()?.let { return it.toDomain() }

        val now = time.nowMillis()
        val entity = ConversationEntity(
            id = Ids.new(),
            title = title,
            createdAt = now,
            updatedAt = now,
            pinned = false,
        )
        dao.upsertConversation(entity)
        return entity.toDomain()
    }

    suspend fun append(
        conversationId: String,
        role: ChatRole,
        text: String,
        toolCalls: List<ToolCallRequest> = emptyList(),
        toolCallId: String? = null,
        toolName: String? = null,
        meta: MessageMeta = MessageMeta(),
    ): ChatMessage {
        val now = time.nowMillis()
        val entity = MessageEntity(
            id = Ids.new(),
            conversationId = conversationId,
            role = role.name,
            content = text.ifEmpty { null },
            toolCallsJson = MessageCodec.encodeToolCalls(toolCalls),
            toolCallId = toolCallId,
            toolName = toolName,
            metaJson = MessageCodec.encodeMeta(meta),
            createdAt = now,
        )
        dao.upsertMessage(entity)
        dao.touch(conversationId, now)

        // 首条用户消息顺带定标题，省一次「让模型起名」的开销。
        if (role == ChatRole.USER && dao.messageCount(conversationId) == 1) {
            dao.retitle(conversationId, deriveTitle(text), now)
        }
        return entity.toDomain()
    }

    suspend fun deleteMessage(id: String) = dao.deleteMessage(id)

    suspend fun deleteConversation(id: String) = dao.deleteConversation(id)

    suspend fun messageCount(conversationId: String): Int = dao.messageCount(conversationId)

    /** 交给 loop 的历史（已按时间升序）。 */
    suspend fun history(conversationId: String): List<LlmMessage> =
        dao.messages(conversationId).map { it.toDomain().toLlmMessage() }

    companion object {
        const val DEFAULT_TITLE = "新对话"

        private const val TITLE_MAX = 20

        fun deriveTitle(text: String): String {
            val flat = text.replace(Regex("\\s+"), " ").trim()
            if (flat.isEmpty()) return DEFAULT_TITLE
            return if (flat.length <= TITLE_MAX) flat else flat.take(TITLE_MAX) + "…"
        }
    }
}

private fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pinned = pinned,
)

private fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = ChatRole.entries.firstOrNull { it.name == role } ?: ChatRole.ASSISTANT,
    text = content.orEmpty(),
    toolCalls = MessageCodec.decodeToolCalls(toolCallsJson),
    toolCallId = toolCallId,
    toolName = toolName,
    meta = MessageCodec.decodeMeta(metaJson),
    createdAt = createdAt,
)
