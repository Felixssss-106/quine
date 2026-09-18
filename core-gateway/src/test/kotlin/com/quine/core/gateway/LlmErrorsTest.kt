package com.quine.core.gateway

import com.quine.core.common.ErrorKind
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmErrorsTest {
    @Test
    fun `maps status codes to human errors`() {
        assertEquals(ErrorKind.AUTH, LlmErrors.http(401, "{}").kind)
        assertEquals(ErrorKind.AUTH, LlmErrors.http(403, "{}").kind)
        assertEquals(ErrorKind.RATE_LIMIT, LlmErrors.http(429, "{}").kind)
        assertEquals(ErrorKind.SERVER, LlmErrors.http(500, "{}").kind)
        assertEquals(ErrorKind.SERVER, LlmErrors.http(503, "{}").kind)
    }

    @Test
    fun `detects content policy rejections`() {
        val error = LlmErrors.http(400, """{"error":{"message":"blocked by content policy"}}""")
        assertEquals(ErrorKind.CONTENT_POLICY, error.kind)
    }

    @Test
    fun `keeps invalid model as server problem`() {
        val error = LlmErrors.http(400, """{"error":{"message":"invalid model name"}}""")
        assertEquals(ErrorKind.SERVER, error.kind)
    }

    @Test
    fun `wraps io errors as network`() {
        val error = LlmErrors.network(IOException("boom"))
        assertEquals(ErrorKind.NETWORK, error.kind)
    }

    @Test
    fun `every error carries a next step`() {
        val all = listOf(
            LlmErrors.missingKey(),
            LlmErrors.http(400, "{}"),
            LlmErrors.http(401, "{}"),
            LlmErrors.http(429, "{}"),
            LlmErrors.http(500, "{}"),
            LlmErrors.http(418, "{}"),
            LlmErrors.network(IOException()),
            LlmErrors.malformed(),
        )
        all.forEach { error ->
            assertFalse(error.message.isBlank())
            assertFalse(error.impact.isBlank())
            assertTrue(error.nextStep.length > 4)
        }
    }
}
