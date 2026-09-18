package com.quine.core.design.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quine.core.design.font.MapleMono

/**
 * 字阶单一来源（docs/visual-spec.md §2）。
 *
 * 数值与文档一一对应：Display 36/44 · Title 24/32 · Headline 17/24 · Body 17/26 ·
 * Footnote 15/22 · Caption 13/18；Mono 三档 14/22 · 13/20 · 12/16。
 *
 * 中文纪律（§2）：文档要求「字重降一档、行高 +2、全角标点、中英混排补 0.25em」。
 * 本层已落实全角标点（文案侧）与字重降档的取值基准；**中英混排 0.25em 与中文行高 +2
 * 需要文本变换层，随「中文字体配对」（附录 B 第 5 条）在 M5 一并处理**。
 */
@Immutable
data class QuineTypography(
    /** 首启大标题。 */
    val display: TextStyle,
    /** 页面标题、空状态。 */
    val title: TextStyle,
    /** 按钮、列表主文案。 */
    val headline: TextStyle,
    /** 对话正文。 */
    val body: TextStyle,
    /** 次级说明。 */
    val footnote: TextStyle,
    /** 来源、时间戳。 */
    val caption: TextStyle,
    /** Mono · 代码。 */
    val monoCode: TextStyle,
    /** Mono · 终端流。 */
    val monoTerminal: TextStyle,
    /** Mono · 数据标注（tabular 数字）。 */
    val monoData: TextStyle,
)

val DefaultQuineTypography: QuineTypography = QuineTypography(
    display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    ),
    footnote = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    monoCode = TextStyle(
        fontFamily = MapleMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    monoTerminal = TextStyle(
        fontFamily = MapleMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    // 文档未给 Mono 12 档行高，按 4pt 网格取 16。
    monoData = TextStyle(
        fontFamily = MapleMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
    ),
)

val LocalQuineTypography = androidx.compose.runtime.staticCompositionLocalOf { DefaultQuineTypography }
