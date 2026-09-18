package com.quine.core.loop

/**
 * loop 的可调参数。默认值即 M0 的取舍（agent-prompt.md §2.3）。
 */
data class LoopConfig(
    /** 上下文滑动窗口：只带最近 N 条消息，更早的内容留给 M1 的会话摘要。 */
    val contextWindowMessages: Int = 20,
    /** 单次用户输入最多允许几轮工具调用。 */
    val maxToolRounds: Int = 8,
    /** 单步自动重试预算（网络 / 瞬态错误）。 */
    val maxTurnRetries: Int = 2,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) {
    companion object {
        val DEFAULT_SYSTEM_PROMPT: String = """
            你是 Quine，一个跑在用户手机上的本地 agent。

            行为准则：
            - 能动手就不要只描述。需要看文件内容时，直接调用 fs_read 读取，不要猜。
            - 工具参数必须严格符合声明的 JSON Schema；路径一律用相对工作区根的相对路径。
            - 回答用中文，短句、克制，不寒暄、不卖萌、不堆感叹号。
            - 失败时讲清三件事：发生了什么、影响是什么、下一步怎么办。
            - 不确定就说不确定，不要编造文件内容或执行结果。
        """.trimIndent()
    }
}
