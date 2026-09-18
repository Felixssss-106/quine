package com.quine.core.tools.workspace

import java.io.InputStream

/**
 * 有上限的字节读取：最多读 `maxBytes + 1` 字节，多读的那 1 个字节用来判断是否被截断。
 * 这样即使模型要求读一个 200 MB 的日志，也不会把内存打爆。
 */
internal object LimitedReader {

    class Outcome(val bytes: ByteArray, val truncated: Boolean)

    fun read(input: InputStream, offsetBytes: Long, maxBytes: Int): Outcome {
        val limit = maxBytes.coerceAtLeast(1)
        if (offsetBytes > 0) skipFully(input, offsetBytes)

        val buffer = ByteArray(limit + 1)
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }

        val truncated = filled > limit
        val size = if (truncated) limit else filled
        return Outcome(bytes = buffer.copyOf(size), truncated = truncated)
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) return
            remaining -= skipped
        }
    }

    /**
     * 截断点可能落在一个 UTF-8 多字节字符中间，解码尾部会出现替换字符，
     * 这里把它去掉，避免模型看到「半个字」。
     */
    fun decode(bytes: ByteArray, truncated: Boolean): String {
        var text = String(bytes, Charsets.UTF_8)
        if (truncated && text.isNotEmpty() && text.last() == '\uFFFD') {
            text = text.dropLast(1)
        }
        return text
    }
}
