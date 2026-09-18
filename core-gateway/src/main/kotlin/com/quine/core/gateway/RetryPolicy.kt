package com.quine.core.gateway

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError

/**
 * 重试预算：单步最多 2 次自动重试（简报 §2.3）。
 * 只重试瞬时错误；且一旦已经有内容流到 UI，就绝不重放（避免重复内容）。
 */
data class RetryPolicy(
    val maxRetries: Int = 2,
    val baseDelayMillis: Long = 600,
) {
    private val retryable = setOf(ErrorKind.NETWORK, ErrorKind.SERVER, ErrorKind.RATE_LIMIT)

    fun shouldRetry(error: QuineError, attemptsDone: Int): Boolean =
        attemptsDone < maxRetries && error.kind in retryable

    fun delayMillis(attemptsDone: Int): Long = baseDelayMillis * (1L shl attemptsDone.coerceAtMost(4))
}
