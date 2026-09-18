package com.quine.core.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 组装 /chat/completions 请求体。绝不包含任何日志或 Key。 */
object OpenAiRequestBuilder {
    fun build(json: Json, request: LlmRequest, stream: Boolean = true): String {
        val root = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_tokens", it) }
            if (stream) {
                put(
                    "stream_options",
                    buildJsonObject { put("include_usage", true) },
                )
            }
            if (request.tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        request.tools.forEach { tool ->
                            add(
                                buildJsonObject {
                                    put("type", "function")
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", tool.id)
                                            put("description", tool.description)
                                            put("parameters", tool.parameters)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
                put("tool_choice", "auto")
            }
            put("messages", buildJsonArray { request.messages.forEach { add(messageToJson(it)) } })
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun messageToJson(message: LlmMessage): JsonElement = buildJsonObject {
        put("role", message.role.name.lowercase())
        val content = message.content
        if (content != null) put("content", content)
        if (message.toolCalls.isNotEmpty()) {
            put(
                "tool_calls",
                buildJsonArray {
                    message.toolCalls.forEach { call ->
                        add(
                            buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", call.name)
                                        put("arguments", call.argumentsJson)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        message.toolCallId?.let { put("tool_call_id", it) }
        message.name?.let { put("name", it) }
    }
}
