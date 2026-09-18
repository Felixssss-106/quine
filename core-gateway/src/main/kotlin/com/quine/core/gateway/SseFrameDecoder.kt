package com.quine.core.gateway

/**
 * SSE 帧解码：按行喂入，遇到空行时输出一个完整 `data` 负载。
 * 覆盖 CRLF、注释行（心跳）、多行 data、字段无冒号等边界。
 */
class SseFrameDecoder {
    private val data = StringBuilder()

    /**
     * 「是否见过 data 字段」必须单独记，不能拿 `data.isEmpty()` 当哨兵：
     * 裸 `data`（无冒号）按 SSE 规范是「字段 data、值为空串」，也是合法的一帧。
     */
    private var hasData = false

    /** 返回非 null 表示一个帧结束（负载可能为空字符串）。 */
    fun onLine(rawLine: String): String? {
        val line = rawLine.removeSuffix("\r")
        if (line.isEmpty()) {
            if (!hasData) return null
            return take()
        }
        if (line.startsWith(":")) return null

        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)

        if (field == "data") {
            if (hasData) data.append('\n')
            data.append(value)
            hasData = true
        }
        return null
    }

    /** 流结束时把残留的 data 作为最后一帧输出。 */
    fun flush(): String? = if (hasData) take() else null

    private fun take(): String {
        val payload = data.toString()
        data.clear()
        hasData = false
        return payload
    }
}
