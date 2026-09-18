package com.quine.core.common

/** 信任档位（docs/page-specs.md §3）：M0 只持久化，审批矩阵在 M1 生效。 */
enum class TrustLevel(val label: String) {
    CONSERVATIVE("保守"),
    STANDARD("标准"),
    RUNAWAY("狂奔"),
    ;

    companion object {
        fun fromId(id: String?): TrustLevel = entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: STANDARD
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
