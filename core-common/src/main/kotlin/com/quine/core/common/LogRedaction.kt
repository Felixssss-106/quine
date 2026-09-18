package com.quine.core.common

/**
 * 日志脱敏。红线：日志里永远不出现 API Key、对话内容明文。
 */
object LogRedaction {
    private const val PLACEHOLDER = "[redacted]"

    private val patterns = listOf(
        // sk-xxx / sk-proj-xxx 之类的常见 Key 形状
        Regex("""\b(?:sk|rk|pk)-[A-Za-z0-9_\-]{8,}"""),
        // Authorization: Bearer xxx
        Regex("""(?i)\bBearer\s+[A-Za-z0-9._\-]{8,}"""),
        // api_key / apiKey / x-api-key: xxx
        Regex("""(?i)("?(?:api[_-]?key|authorization|access[_-]?token)"?\s*[:=]\s*"?)([A-Za-z0-9._\-]{6,})"""),
        // 邮箱形状（对话里常见的隐私噪音，弱脱敏）
        Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"""),
    )

    /** 脱敏文本，可按需附加已知秘密（例如当前 Key）做精确替换。 */
    fun redact(text: String, secrets: Collection<String> = emptyList()): String {
        var out = text
        for (secret in secrets) {
            if (secret.length >= 6) out = out.replace(secret, PLACEHOLDER)
        }
        for (pattern in patterns) {
            out = pattern.replace(out) { match ->
                if (match.groupValues.size > 2 && match.groupValues[1].isNotEmpty()) {
                    match.groupValues[1] + PLACEHOLDER
                } else {
                    PLACEHOLDER
                }
            }
        }
        return out
    }

    /** Key 的展示形态：前 3 后 4，中间省略。 */
    fun maskKey(key: String): String = when {
        key.isEmpty() -> ""
        key.length <= 8 -> "•".repeat(key.length.coerceAtMost(8))
        else -> key.take(3) + "…" + key.takeLast(4)
    }
}

/** 明确标注「这是秘密」，toString 永远不输出明文。 */
@JvmInline
value class Secret(private val value: String) {
    fun reveal(): String = value

    val isEmpty: Boolean get() = value.isEmpty()

    override fun toString(): String = "[redacted]"
}
