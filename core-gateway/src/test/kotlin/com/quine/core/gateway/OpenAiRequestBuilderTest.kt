package com.quine.core.gateway

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiRequestBuilderTest {
    private val messages = listOf(
        LlmMessage(ChatRole.SYSTEM, "你是 Quine。"),
        LlmMessage(ChatRole.USER, "读一下 notes.md"),
        LlmMessage(
            role = ChatRole.ASSISTANT,
            content = null,
            toolCalls = listOf(ToolCallRequest("call_1", "fs_read", "{\"path\":\"notes.md\"}")),
        ),
        LlmMessage(ChatRole.TOOL, "文件内容", toolCallId = "call_1", name = "fs_read"),
    )

    private val tools = listOf(
        ToolSchema(
            id = "fs_read",
            description = "读文件",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { put("path", buildJsonObject { put("type", "string") }) })
            },
        ),
    )

    @Test
    fun `builds stream request with tools and lowercase roles`() {
        val payload = OpenAiRequestBuilder.build(QuineJson, LlmRequest(model = "deepseek-chat", messages = messages, tools = tools))
        val root = QuineJson.parseToJsonElement(payload) as JsonObject

        assertEquals("deepseek-chat", (root["model"] as JsonPrimitive).content)
        assertEquals(true, (root["stream"] as JsonPrimitive).content.toBoolean())
        assertTrue(root.containsKey("stream_options"))

        val toolList = root["tools"] as JsonArray
        val function = ((toolList[0] as JsonObject)["function"] as JsonObject)
        assertEquals("fs_read", (function["name"] as JsonPrimitive).content)

        val messageList = root["messages"] as JsonArray
        val roles = messageList.map { ((it as JsonObject)["role"] as JsonPrimitive).content }
        assertEquals(listOf("system", "user", "assistant", "tool"), roles)

        val assistant = messageList[2] as JsonObject
        assertFalse(assistant.containsKey("content"))
        val call = ((assistant["tool_calls"] as JsonArray)[0] as JsonObject)
        assertEquals("call_1", (call["id"] as JsonPrimitive).content)

        val toolMessage = messageList[3] as JsonObject
        assertEquals("call_1", (toolMessage["tool_call_id"] as JsonPrimitive).content)
    }

    @Test
    fun `never carries api key in body`() {
        val payload = OpenAiRequestBuilder.build(QuineJson, LlmRequest(model = "m", messages = messages, tools = tools))
        assertFalse(payload.contains("sk-"))
        assertFalse(payload.contains("Bearer"))
    }

    @Test
    fun `non-stream request omits stream options`() {
        val payload = OpenAiRequestBuilder.build(
            QuineJson,
            LlmRequest(model = "m", messages = listOf(LlmMessage(ChatRole.USER, "hi")), maxTokens = 1),
            stream = false,
        )
        val root = QuineJson.parseToJsonElement(payload) as JsonObject
        assertEquals("false", (root["stream"] as JsonPrimitive).content)
        assertFalse(root.containsKey("stream_options"))
        assertEquals(1, (root["max_tokens"] as JsonPrimitive).content.toInt())
    }
}
