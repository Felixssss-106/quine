package com.quine.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactionTest {
    @Test
    fun `redacts openai style keys`() {
        val out = LogRedaction.redact("request failed with key sk-abcdef1234567890")
        assertFalse(out.contains("sk-abcdef1234567890"))
        assertTrue(out.contains("[redacted]"))
    }

    @Test
    fun `redacts authorization header`() {
        val out = LogRedaction.redact("Authorization: Bearer abcdef123456")
        assertFalse(out.contains("abcdef123456"))
    }

    @Test
    fun `redacts explicit secrets even without key shape`() {
        val secret = "cn-quantum-key-42"
        val out = LogRedaction.redact("provider said $secret is invalid", listOf(secret))
        assertFalse(out.contains(secret))
    }

    @Test
    fun `redacts emails`() {
        val out = LogRedaction.redact("contact me at dev@example.com")
        assertFalse(out.contains("dev@example.com"))
    }

    @Test
    fun `mask keeps head and tail only`() {
        assertEquals("sk-…7890", LogRedaction.maskKey("sk-abcdefghij7890"))
        assertEquals("", LogRedaction.maskKey(""))
    }

    @Test
    fun `secret never prints plaintext`() {
        val secret = Secret("super-secret-value")
        assertEquals("[redacted]", secret.toString())
        assertEquals("super-secret-value", secret.reveal())
    }
}
