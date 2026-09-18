package com.quine.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quine.core.design.theme.QuineTheme

/**
 * 主按钮 = ink 药丸（docs/visual-spec.md §3.1、§4-A）。
 * 高度 52–56；圆角药丸；标签走 Headline 17/24。
 */
@Composable
fun QuinePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val haptics = rememberQuineHaptics()

    Button(
        onClick = {
            haptics.light()
            onClick()
        },
        modifier = modifier.height(dimens.buttonHeight),
        enabled = enabled && !busy,
        shape = RoundedCornerShape(dimens.radiusPill),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.ink,
            contentColor = colors.onInk,
            disabledContainerColor = colors.fill,
            disabledContentColor = colors.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.onInk,
                )
            }
            Text(text = text, style = QuineTheme.typography.headline)
        }
    }
}

/** 次按钮 = 描边（docs/visual-spec.md §4-A）。 */
@Composable
fun QuineSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val haptics = rememberQuineHaptics()

    OutlinedButton(
        onClick = {
            haptics.light()
            onClick()
        },
        modifier = modifier.height(dimens.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(dimens.radiusPill),
        border = BorderStroke(dimens.hairline, colors.line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text = text, style = QuineTheme.typography.headline)
    }
}

/** 文字按钮 = accent（docs/visual-spec.md §4-A）。用于「怎么拿 Key？」这类弱链接。 */
@Composable
fun QuineTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = QuineTheme.colors
    val haptics = rememberQuineHaptics()

    TextButton(
        onClick = {
            haptics.light()
            onClick()
        },
        modifier = modifier.defaultMinSize(minHeight = 32.dp),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.accent,
            disabledContentColor = colors.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(text = text, style = QuineTheme.typography.footnote)
    }
}
