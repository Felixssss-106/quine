package com.quine.core.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallAssemblerTest {
    @Test
    fun `concatenates fragments across chunks`() {
        val assembler = ToolCallAssembler()
        assembler.accept(LlmEvent.ToolCallDelta(0, "call_1", "fs_read", "{\"path\":"))
        assembler.accept(LlmEvent.ToolCallDelta(0, null, null, "\"notes.md\"}"))
        assertTrue(assembler.hasCalls)
        val calls = assembler.build()
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("fs_read", calls[0].name)
        assertEquals("{\"path\":\"notes.md\"}", calls[0].argumentsJson)
    }

    @Test
    fun `keeps parallel calls sorted by index`() {
        val assembler = ToolCallAssembler()
        assembler.accept(LlmEvent.ToolCallDelta(1, "call_b", "fs_list", "{}"))
        assembler.accept(LlmEvent.ToolCallDelta(0, "call_a", "fs_read", "{\"path\":\"a\"}"))
        val calls = assembler.build()
        assertEquals(listOf("call_a", "call_b"), calls.map { it.id })
    }

    @Test
    fun `ignores nameless partials and defaults empty arguments`() {
        val assembler = ToolCallAssembler()
        assembler.accept(LlmEvent.ToolCallDelta(0, "call_1", null, "{}"))
        assertFalse(assembler.hasCalls)
        assertTrue(assembler.build().isEmpty())

        assembler.accept(LlmEvent.ToolCallDelta(1, "call_2", "fs_read", null))
        assertEquals("{}", assembler.build().single().argumentsJson)
    }
}
