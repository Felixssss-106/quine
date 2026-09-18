package com.quine.core.design.font

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.quine.core.design.R

/**
 * 等宽字体 = Maple Mono（docs/visual-spec.md §2），用于代码 / 终端 / diff / 数据 / URL。
 *
 * 打包三个静态字重（Regular / Medium / Bold）而非变量字体：静态字重不依赖
 * `FontVariation` API，渲染更可预测，代价是约 780 KB。
 *
 * 该字体不含 CJK 字形（附录 B 第 5 条「中文字体配对」仍待定），
 * 中文由 Android 平台字体回落链兜住 —— 字形不是等宽，但不会缺字。
 * 许可证 OFL-1.1，见 `licenses/maple-mono-OFL.txt`。
 */
val MapleMono: FontFamily = FontFamily(
    Font(R.font.maple_mono_regular, FontWeight.Normal),
    Font(R.font.maple_mono_medium, FontWeight.Medium),
    Font(R.font.maple_mono_bold, FontWeight.Bold),
)
