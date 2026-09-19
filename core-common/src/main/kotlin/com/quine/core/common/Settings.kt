package com.quine.core.common

/**
 * 权限模式（docs/page-specs.md §3）：M0 只持久化，审批矩阵在 M1 生效。
 *
 * 文案直接说清「要不要问我」，而不是用「保守 / 狂奔」这种性格化比喻 ——
 * 用户要判断的是权限边界，不是这个 AI 胆子大不大。
 */
enum class TrustLevel(val label: String) {
    /** 每次都先问过你。 */
    CONSERVATIVE("请求批准"),

    /** 常规操作自己决定，敏感操作才问。 */
    STANDARD("自动批准"),

    /** 一律不问。 */
    RUNAWAY("完全放行"),
    ;

    companion object {
        fun fromId(id: String?): TrustLevel = entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: STANDARD
    }
}

/**
 * 思考等级（对应 OpenAI 协议的 `reasoning_effort`）。
 *
 * **不是所有模型都支持**：不支持的服务会返回 400 或直接忽略这个字段。
 * 因此 [OFF] 的语义是"不发送该参数"，而不是"发送 minimal" —— 发送一个对方不认识的
 * 取值比不发更容易出错。
 */
enum class ReasoningEffort(val label: String) {
    /** 不发 `reasoning_effort` 参数。 */
    OFF("关闭"),

    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    ;

    /** 发给模型的取值；null = 该参数整个不出现。 */
    val wireValue: String?
        get() = when (this) {
            OFF -> null
            else -> name.lowercase()
        }

    companion object {
        fun fromId(id: String?): ReasoningEffort =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: OFF
    }
}

/** 应用图标变体（settings → 外观 → 应用图标）。 */
enum class IconVariant {
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromId(id: String?): IconVariant = entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: LIGHT
    }
}

/** 由 app 层实现：切换 activity-alias 启动入口。 */
interface IconAliasController {
    fun currentVariant(): IconVariant

    fun apply(variant: IconVariant)
}
