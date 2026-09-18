package com.quine.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quine.core.design.theme.QuineTheme

/**
 * 毛发分隔线（docs/visual-spec.md §4-A）：层级靠留白、字重、毛发分隔线，不靠投影。
 * 宽度 0.5–1dp。
 */
@Composable
fun QuineDivider(modifier: Modifier = Modifier) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.hairline)
            .background(colors.line),
    )
}
