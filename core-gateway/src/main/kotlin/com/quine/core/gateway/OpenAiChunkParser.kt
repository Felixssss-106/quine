package com.quine.core.gateway

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** 解析 OpenAI 兼容的流式 chunk 与非流式完整响应。 */
class OpenAiChunkParser(private val json: Json = QuineJson) {
    fun parse(payload: String): List<LlmEvent> {
        val text = payload.trim()
        if (text.isEmpty()) return emptyList()
        if (text == DONE) return listOf(LlmEvent.Finish("done"))

        val root = parseObject(text)
        root["error"]?.let { throw LlmException(LlmErrors.providerPayload(json, it)) }

        val events = mutableListOf<LlmEvent>()
        val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObjectOrNull()

        val delta = choice?.get("delta")?.jsonObjectOrNull()
        delta?.get("content")?.asStringOrNull()?.takeIf { it.isNotEmpty() }?.let {
            events += LlmEvent.TextDelta(it)
        }
        (delta?.get("tool_calls") as? JsonArray)?.forEach { element ->
            val call = element.jsonObjectOrNull() ?: return@forEach
            val function = call["function"]?.jsonObjectOrNull()
            events += LlmEvent.ToolCallDelta(
                index = call["index"]?.asIntOrNull() ?: 0,
                id = call["id"]?.asStringOrNull(),
                name = function?.get("name")?.asStringOrNull(),
                argumentsDelta = function?.get("arguments")?.asStringOrNull(),
            )
        }
        choice?.get("finish_reason")?.asStringOrNull()?.let { events += LlmEvent.Finish(it) }
        appendUsage(root, events)
        return events
    }

    /** 兼容忽略 `stream` 参数、直接返回完整 JSON 的服务。 */
    fun parseFull(payload: String): List<LlmEvent> {
        val root = parseObject(payload.trim())
        root["error"]?.let { throw LlmException(LlmErrors.providerPayload(json, it)) }

        val events = mutableListOf<LlmEvent>()
        val message = (root["choices"] as? JsonArray)
            ?.firstOrNull()
            ?.jsonObjectOrNull()
            ?.get("message")
            ?.jsonObjectOrNull()
        message?.get("content")?.asStringOrNull()?.takeIf { it.isNotEmpty() }?.let {
            events += LlmEvent.TextDelta(it)
        }
        (message?.get("tool_calls") as? JsonArray)?.forEachIndexed { index, element ->
            val call = element.jsonObjectOrNull() ?: return@forEachIndexed
            val function = call["function"]?.jsonObjectOrNull()
            events += LlmEvent.ToolCallDelta(
                index = index,
                id = call["id"]?.asStringOrNull(),
                name = function?.get("name")?.asStringOrNull(),
                argumentsDelta = function?.get("arguments")?.asStringOrNull(),
            )
        }
        appendUsage(root, events)
        events += LlmEvent.Finish(null)
        return events
    }

    private fun appendUsage(root: JsonObject, events: MutableList<LlmEvent>) {
        (root["usage"] as? JsonObject)?.let { usage ->
            events += LlmEvent.Usage(
                promptTokens = usage["prompt_tokens"]?.asIntOrNull() ?: 0,
                completionTokens = usage["completion_tokens"]?.asIntOrNull() ?: 0,
                totalTokens = usage["total_tokens"]?.asIntOrNull() ?: 0,
            )
        }
    }

    /**
     * 坏 JSON 必须收敛成 [LlmErrors.malformed] 的人话错误。
     * 否则原始 `SerializationException` 会一路冒到 UI，用户看到的是一串解析器术语，
     * 而不是「发生了什么 / 影响 / 下一步怎么办」。
     */
    private fun parseObject(text: String): JsonObject = try {
        json.parseToJsonElement(text).jsonObjectOrNull()
            ?: throw LlmException(LlmErrors.malformed())
    } catch (error: LlmException) {
        throw error
    } catch (error: SerializationException) {
        throw LlmException(LlmErrors.malformed())
    } catch (error: IllegalArgumentException) {
        throw LlmException(LlmErrors.malformed())
    }

    companion object {
        const val DONE = "[DONE]"
    }
}

internal fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement.asIntOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull
