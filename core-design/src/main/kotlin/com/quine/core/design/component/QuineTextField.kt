package com.quine.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.quine.core.design.theme.QuineTheme

/**
 * 描边输入框（docs/visual-spec.md §4-A）：浮动标签，圆角 16。
 *
 * 错误态（docs/motion-spec.md §2.4）：红描边 + 水平轻抖（±6px ×3，320ms），
 * 错误文案 120ms 淡入，触感 heavy×2。
 * reduced-motion 下不抖，只换描边色。
 *
 * @param shakeSignal 递增即可触发一次抖动（0 表示从未抖动）。
 */
@Composable
fun QuineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    shakeSignal: Int = 0,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val motion = QuineTheme.motion
    val reduced = QuineTheme.reducedMotion
    val haptics = rememberQuineHaptics()
    val density = LocalDensity.current
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(shakeSignal, isError) {
        if (shakeSignal <= 0 || !isError) return@LaunchedEffect
        haptics.error()
        if (reduced) return@LaunchedEffect
        val amplitude = with(density) { 6.dp.toPx() }
        val leg = motion.duration(320, reduced) / 6
        repeat(3) {
            shakeOffset.animateTo(-amplitude, tween(leg))
            shakeOffset.animateTo(amplitude, tween(leg))
        }
        shakeOffset.animateTo(0f, tween(leg))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = with(density) { shakeOffset.value.toDp() }),
            enabled = enabled,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            label = { Text(text = label) },
            placeholder = placeholder?.let { { Text(text = it, color = colors.textTertiary) } },
            trailingIcon = trailing,
            textStyle = QuineTheme.typography.body,
            shape = RoundedCornerShape(dimens.radiusInput),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textTertiary,
                focusedContainerColor = colors.bg,
                unfocusedContainerColor = colors.bg,
                disabledContainerColor = colors.bg,
                cursorColor = colors.accent,
                focusedBorderColor = colors.textPrimary,
                unfocusedBorderColor = colors.line,
                disabledBorderColor = colors.line,
                errorBorderColor = colors.danger,
                focusedLabelColor = colors.textSecondary,
                unfocusedLabelColor = colors.textTertiary,
                errorLabelColor = colors.danger,
            ),
        )
        AnimatedVisibility(
            visible = isError && errorText != null,
            enter = fadeIn(tween(motion.duration(motion.micro, reduced))),
            exit = fadeOut(tween(motion.duration(motion.micro, reduced))),
        ) {
            Text(
                text = errorText.orEmpty(),
                style = QuineTheme.typography.caption,
                color = colors.danger,
                modifier = Modifier.padding(start = dimens.grid, top = 6.dp),
            )
        }
    }
}
