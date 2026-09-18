package com.quine.core.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseFrameDecoderTest {
    @Test
    fun `joins multi-line data with newline`() {
        val decoder = SseFrameDecoder()
        assertNull(decoder.onLine("event: message"))
        assertNull(decoder.onLine("data: {\"a\":"))
        assertNull(decoder.onLine("data: 1}"))
        assertEquals("{\"a\":\n1}", decoder.onLine(""))
    }

    @Test
    fun `ignores comments and empty heartbeats`() {
        val decoder = SseFrameDecoder()
        assertNull(decoder.onLine(": ping"))
        assertNull(decoder.onLine(""))
        assertNull(decoder.onLine(": keep-alive"))
    }

    @Test
    fun `handles crlf and colons inside payload`() {
        val decoder = SseFrameDecoder()
        assertNull(decoder.onLine("data: a:b\r"))
        assertEquals("a:b", decoder.onLine("\r"))
    }

    @Test
    fun `handles field without colon`() {
        val decoder = SseFrameDecoder()
        assertNull(decoder.onLine("data"))
        assertEquals("", decoder.onLine(""))
    }

    @Test
    fun `flush returns trailing data`() {
        val decoder = SseFrameDecoder()
        assertNull(decoder.onLine("data: trailing"))
        assertEquals("trailing", decoder.flush())
        assertNull(decoder.flush())
    }
}
