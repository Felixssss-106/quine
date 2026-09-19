package com.quine.core.design.token

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 「双面人」色 token 单一来源（docs/visual-spec.md §1）。
 *
 * 纪律（§0 第 1 条）：黑白灰是主角，颜色是客人 —— 彩色只允许出现在
 * **链接 / 聚焦 / 危险** 三处，因此这里只有 `accent` 与 `danger` 两个彩色 token。
 * diff 增删不引入红绿（docs/page-specs.md §2），由 `ink` / `fill` / `textSecondary` 派生。
 *
 * 镜像规则：`ink` 是唯一发生翻转的 token（浅色黑块 / 深色亮块），其余为「同名异值」。
 * **全项目仅此文件允许出现十六进制色值。**
 */
@Immutable
data class QuineColors(
    /** 主角色：主按钮底 + 反色文字 / 主标题。深色下翻转为亮块。 */
    val ink: Color,
    /** 页面底。 */
    val bg: Color,
    /** 浮层与卡片面（毛玻璃底）。 */
    val bgElevated: Color,
    /** 次级填充：用户气泡 / chip / 输入框底。 */
    val fill: Color,
    /** 毛发分隔线与描边（0.5–1dp）。 */
    val line: Color,
    /** 文字三级 · 第一级。 */
    val textPrimary: Color,
    /** 文字三级 · 第二级。 */
    val textSecondary: Color,
    /** 文字三级 · 第三级。 */
    val textTertiary: Color,
    /** 链接 / 聚焦 / 选中。 */
    val accent: Color,
    /** 错误 / 危险。 */
    val danger: Color,
    /** 落在 `ink` 上的内容色（黑块上用白字，亮块上用黑字）。 */
    val onInk: Color,
    /** 抽屉 / 弹层遮罩。 */
    val scrim: Color,
    /**
     * 终端块底 —— **永久暗面**（page-specs §2-b）：无论深浅主题都是深色，
     * 终端的仪式感。因此这两档**不随主题翻转**，是 colours 里唯一的例外。
     */
    val terminalBg: Color,
    /** 终端正文。 */
    val terminalText: Color,
    /** 终端里的次级信息（提示符、路径）。 */
    val terminalMuted: Color,
    /** 当前是否为深色主题，供个别需要分支的绘制使用。 */
    val isDark: Boolean,
)

/** docs/visual-spec.md §1.2 浅色基准值。 */
val LightQuineColors: QuineColors = QuineColors(
    ink = Color(0xFF0D0D0D),
    bg = Color(0xFFFFFFFF),
    bgElevated = Color(0xB8FFFFFF),
    fill = Color(0xFFF4F4F5),
    line = Color(0xFFE8E8EA),
    textPrimary = Color(0xFF0D0D0D),
    textSecondary = Color(0xFF8A8A8E),
    textTertiary = Color(0xFFB0B0B5),
    accent = Color(0xFF007AFF),
    danger = Color(0xFFFF3B30),
    onInk = Color(0xFFFFFFFF),
    scrim = Color(0x52000000),
    terminalBg = Color(0xFF101012),
    terminalText = Color(0xFFE6E6E8),
    terminalMuted = Color(0xFF7A7A82),
    isDark = false,
)

/** docs/visual-spec.md §1.3 深色基准值。 */
val DarkQuineColors: QuineColors = QuineColors(
    ink = Color(0xFFFFFFFF),
    bg = Color(0xFF000000),
    bgElevated = Color(0xB81C1C1E),
    fill = Color(0xFF1C1C1E),
    line = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF98989E),
    textTertiary = Color(0xFF5A5A5F),
    accent = Color(0xFF0A84FF),
    danger = Color(0xFFFF453A),
    onInk = Color(0xFF0D0D0D),
    scrim = Color(0x8A000000),
    // 与浅色同值：终端永远是暗的。
    terminalBg = Color(0xFF101012),
    terminalText = Color(0xFFE6E6E8),
    terminalMuted = Color(0xFF7A7A82),
    isDark = true,
)

val LocalQuineColors = staticCompositionLocalOf { LightQuineColors }
