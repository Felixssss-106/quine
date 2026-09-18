package com.quine.core.storage

import com.quine.core.gateway.ToolCallRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {

    @Test
    fun `工具调用往返`() {
        val calls = listOf(
            ToolCallRequest(id = "call_1", name = "fs_read", argumentsJson = "{\"path\":\"a.txt\"}"),
            ToolCallRequest(id = "call_2", name = "fs_read", argumentsJson = "{}"),
        )

        val decoded = MessageCodec.decodeToolCalls(MessageCodec.encodeToolCalls(calls))
        assertEquals(calls, decoded)
    }

    @Test
    fun `没有工具调用时不占列`() {
        assertNull(MessageCodec.encodeToolCalls(emptyList()))
        assertTrue(MessageCodec.decodeToolCalls(null).isEmpty())
        assertTrue(MessageCodec.decodeToolCalls("").isEmpty())
    }

    @Test
    fun `坏数据退化成空列表而不是崩`() {
        assertTrue(MessageCodec.decodeToolCalls("{不是 JSON").isEmpty())
        assertTrue(MessageCodec.decodeToolCalls("{\"unexpected\":1}").isEmpty())
    }

    @Test
    fun `元数据往返`() {
        val meta = MessageMeta(
            ok = false,
            sourceRef = "授权目录:a.txt#bytes:0-5",
            truncated = true,
            durationMillis = 42,
            promptTokens = 10,
            completionTokens = 20,
            totalTokens = 30,
        )
        assertEquals(meta, MessageCodec.decodeMeta(MessageCodec.encodeMeta(meta)))
    }

    @Test
    fun `默认元数据不占列`() {
        assertNull(MessageCodec.encodeMeta(MessageMeta()))
        assertEquals(MessageMeta(), MessageCodec.decodeMeta(null))
        assertEquals(MessageMeta(), MessageCodec.decodeMeta("坏数据"))
    }
}
