package com.quine.core.storage

import com.quine.core.gateway.ToolCallRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 消息的角色 / 工具调用 / 工具结果元数据，落到单列里，避免表结构随 UI 需求膨胀。 */
@Serializable
data class MessageMeta(
    val ok: Boolean? = null,
    val sourceRef: String? = null,
    val truncated: Boolean = false,
    val durationMillis: Long = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    /** 活动卡的一行摘要，重启后原样恢复（不然历史里的卡片会退化成工具名）。 */
    val summary: String? = null,
    /** 模型给的原始参数串，供展开态展示。 */
    val arguments: String? = null,
)

@Serializable
private data class ToolCallDto(
    val id: String,
    val name: String,
    val arguments: String,
)

/**
 * 消息字段的 JSON 往返。工具调用与元数据都以字符串列存储，
 * 避免为每个新字段加一次数据库迁移。
 */
object MessageCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeToolCalls(calls: List<ToolCallRequest>): String? {
        if (calls.isEmpty()) return null
        return json.encodeToString(
            ListSerializer,
            calls.map { ToolCallDto(id = it.id, name = it.name, arguments = it.argumentsJson) },
        )
    }

    fun decodeToolCalls(raw: String?): List<ToolCallRequest> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer, raw).map {
                ToolCallRequest(id = it.id, name = it.name, argumentsJson = it.arguments)
            }
        }.getOrDefault(emptyList())
    }

    fun encodeMeta(meta: MessageMeta): String? =
        if (meta == MessageMeta()) null else json.encodeToString(MessageMeta.serializer(), meta)

    fun decodeMeta(raw: String?): MessageMeta {
        if (raw.isNullOrBlank()) return MessageMeta()
        return runCatching { json.decodeFromString(MessageMeta.serializer(), raw) }
            .getOrDefault(MessageMeta())
    }

    private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(ToolCallDto.serializer())
}
