package com.quine.core.gateway

import com.quine.core.common.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenAiChunkParserTest {
    private val parser = OpenAiChunkParser()

    @Test
    fun `parses content delta`() {
        val events = parser.parse(
            """{"choices":[{"delta":{"content":"你"},"finish_reason":null}]}""",
        )
        assertEquals(listOf(LlmEvent.TextDelta("你")), events)
    }

    @Test
    fun `parses tool call fragments`() {
        val events = parser.parse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"fs_read","arguments":"{\"path\":"}}]}}]}""",
        )
        assertEquals(1, events.size)
        val delta = events.first() as LlmEvent.ToolCallDelta
        assertEquals(0, delta.index)
        assertEquals("call_1", delta.id)
        assertEquals("fs_read", delta.name)
        assertEquals("{\"path\":", delta.argumentsDelta)
    }

    @Test
    fun `parses finish reason and usage`() {
        val events = parser.parse(
            """{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":3,"total_tokens":15}}""",
        )
        assertTrue(events.contains(LlmEvent.Finish("stop")))
        assertTrue(events.contains(LlmEvent.Usage(12, 3, 15)))
    }

    @Test
    fun `done payload finishes the stream`() {
        assertEquals(listOf(LlmEvent.Finish("done")), parser.parse("[DONE]"))
        assertEquals(listOf(LlmEvent.Finish("done")), parser.parse(" [DONE] "))
    }

    @Test
    fun `blank payload yields nothing`() {
        assertTrue(parser.parse("   ").isEmpty())
    }

    @Test
    fun `error payload maps to auth error`() {
        try {
            parser.parse("""{"error":{"message":"Invalid API key provided","type":"auth_error"}}""")
            fail("expected LlmException")
        } catch (error: LlmException) {
            assertEquals(ErrorKind.AUTH, error.error.kind)
        }
    }

    @Test
    fun `parses full non-stream response`() {
        val events = parser.parseFull(
            """{"choices":[{"message":{"role":"assistant","content":"你好"}}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}""",
        )
        assertEquals(LlmEvent.TextDelta("你好"), events.first())
        assertTrue(events.any { it is LlmEvent.Finish })
        assertTrue(events.any { it is LlmEvent.Usage })
    }

    @Test
    fun `parses full response with tool calls`() {
        val events = parser.parseFull(
            """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"fs_read","arguments":"{\"path\":\"a.md\"}"}}]}}]}""",
        )
        val delta = events.filterIsInstance<LlmEvent.ToolCallDelta>().first()
        assertEquals("fs_read", delta.name)
        assertEquals("{\"path\":\"a.md\"}", delta.argumentsDelta)
    }
}
