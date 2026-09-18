package com.quine.core.gateway

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    private val policy = RetryPolicy(maxRetries = 2, baseDelayMillis = 100)

    private fun error(kind: ErrorKind) = QuineError(kind, "m", "i", "n")

    @Test
    fun `retries transient errors twice at most`() {
        assertTrue(policy.shouldRetry(error(ErrorKind.NETWORK), attemptsDone = 0))
        assertTrue(policy.shouldRetry(error(ErrorKind.SERVER), attemptsDone = 1))
        assertFalse(policy.shouldRetry(error(ErrorKind.NETWORK), attemptsDone = 2))
    }

    @Test
    fun `never retries auth or content errors`() {
        assertFalse(policy.shouldRetry(error(ErrorKind.AUTH), attemptsDone = 0))
        assertFalse(policy.shouldRetry(error(ErrorKind.CONTENT_POLICY), attemptsDone = 0))
    }

    @Test
    fun `delay grows exponentially`() {
        assertEquals(100L, policy.delayMillis(0))
        assertEquals(200L, policy.delayMillis(1))
        assertEquals(400L, policy.delayMillis(2))
    }
}
