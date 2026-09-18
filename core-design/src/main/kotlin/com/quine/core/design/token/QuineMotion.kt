package com.quine.core.design.token

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 动效令牌单一来源（docs/motion-spec.md §1）。
 *
 * 纪律（§0「优雅」）：**全局只有两套弹簧、三档时长**，不逐处调参 —— 这是代码审查项。
 * 纪律（§4「降级」）：每处动画都要有 reduced-motion 分支，位移 / 缩放替换为 ≤120ms 交叉淡化。
 */
@Immutable
data class QuineMotion(
    /** 微：按压、chip、开关、图标变换。 */
    val micro: Int = 120,
    /** 标准：抽屉、页面切换、卡片展开收起、消息进入。 */
    val standard: Int = 240,
    /** 大：双面切换、容器变换、完成收束、首启仪式。 */
    val large: Int = 400,
) {
    /**
     * 位移类走弹簧；纯淡入淡出走 [standard] 标准缓动。
     * reduced-motion 下时长一律压到 [micro] 以内。
     */
    fun duration(baseMillis: Int, reducedMotion: Boolean = false): Int =
        if (reducedMotion) minOf(baseMillis, micro) else baseMillis

    /** 标准弹簧：利落收口（≈240ms 落定）。用于导航、抽屉、按钮。 */
    fun <T> standardSpring(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 380f)

    /** 柔和弹簧：呼吸落座（≈400ms 落定）。用于大容器、卡片、流式光标。 */
    fun <T> softSpring(): SpringSpec<T> = spring(dampingRatio = 0.95f, stiffness = 200f)

    companion object {
        val Default = QuineMotion()
    }
}

val LocalQuineMotion = staticCompositionLocalOf { QuineMotion.Default }

/** 系统「移除动画」是否开启。由 `QuineTheme` 注入。 */
val LocalReducedMotion = staticCompositionLocalOf { false }
