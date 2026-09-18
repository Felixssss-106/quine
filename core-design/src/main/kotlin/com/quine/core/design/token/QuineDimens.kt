package com.quine.core.design.token

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆角、网格与控件尺寸单一来源（docs/visual-spec.md §3）。
 *
 * 纪律（§0 第 3 条）：圆角即语言 —— 按钮=药丸(999) · 卡片=16 · 抽屉=20 起。
 * 网格 4pt；页面边距 20。
 */
@Immutable
data class QuineDimens(
    /** 4pt 网格基数。 */
    val grid: Dp = 4.dp,
    /** 按钮 / chips：药丸。 */
    val radiusPill: Dp = 999.dp,
    /** 卡片。 */
    val radiusCard: Dp = 16.dp,
    /** 大容器 / 抽屉顶。 */
    val radiusDrawer: Dp = 20.dp,
    /** 输入框。 */
    val radiusInput: Dp = 16.dp,
    /** 用户消息气泡。 */
    val radiusBubble: Dp = 20.dp,
    /** 页面边距。 */
    val pageMargin: Dp = 20.dp,
    /** 区块间距。 */
    val sectionGap: Dp = 24.dp,
    /** 大区块间距。 */
    val sectionGapLarge: Dp = 32.dp,
    /** 列表行最小高度。 */
    val listRowMinHeight: Dp = 56.dp,
    /** 按钮高度（文档区间 52–56）。 */
    val buttonHeight: Dp = 54.dp,
    /** composer 高度（文档区间 48–52）。 */
    val composerHeight: Dp = 50.dp,
    /** 卡片内距（文档区间 16–20）。 */
    val cardPadding: Dp = 16.dp,
    /** 卡片内距（大）。 */
    val cardPaddingLarge: Dp = 20.dp,
    /** chips 高度（文档区间 32–36）。 */
    val chipHeight: Dp = 34.dp,
    /** 毛发分隔线宽度（文档区间 0.5–1）。 */
    val hairline: Dp = 1.dp,
    /** 细进度条厚度。 */
    val progressThickness: Dp = 2.dp,
    /** composer 底部安全区补量。 */
    val composerBottomInset: Dp = 20.dp,
)

val DefaultQuineDimens = QuineDimens()

val LocalQuineDimens = staticCompositionLocalOf { DefaultQuineDimens }
