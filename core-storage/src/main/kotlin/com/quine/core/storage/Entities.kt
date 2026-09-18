package com.quine.core.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话（agent-prompt.md §2.6）。
 * M0 只有单会话，但表结构按多会话设计 —— 这样 M1 加会话列表不需要迁移。
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
)

/**
 * 消息。工具调用与结果元数据都以 JSON 字符串列存储（见 [MessageCodec]）。
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String?,
    val toolCallsJson: String?,
    val toolCallId: String?,
    val toolName: String?,
    val metaJson: String?,
    val createdAt: Long,
)
