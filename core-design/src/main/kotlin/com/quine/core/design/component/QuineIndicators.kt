package com.quine.core.design.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.quine.core.design.theme.QuineTheme

/**
 * 呼吸点（docs/visual-spec.md §4-A、docs/motion-spec.md §2.8）：
 * 1.2s 循环，透明度 .35↔1，缩放 .92↔1。用于「正在干活」的低打扰指示。
 */
@Composable
fun QuineBreathingDot(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val colors = QuineTheme.colors
    val reduced = QuineTheme.reducedMotion

    if (reduced) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.textSecondary),
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "breathingDot")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingDotProgress",
    )

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val alpha = 0.35f + 0.65f * progress
                val scale = 0.92f + 0.08f * progress
                val radius = (this.size.minDimension / 2f) * scale
                drawCircle(color = colors.textSecondary.copy(alpha = alpha), radius = radius)
            },
    )
}

/**
 * 骨架屏（docs/visual-spec.md §4-A、docs/motion-spec.md §2.8）：
 * 1.6s 扫过循环，低对比；深色再降一档。
 */
@Composable
fun QuineSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(QuineTheme.dimens.radiusCard),
) {
    val colors = QuineTheme.colors
    val reduced = QuineTheme.reducedMotion
    val base = colors.fill

    if (reduced) {
        Box(modifier = modifier.clip(shape).background(base))
        return
    }

    // 深色下高光更暗（「深色再降一档」）
    val highlight = if (colors.isDark) colors.line else colors.bgElevated

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val sweep = size.width * 0.6f
                val head = -sweep + (size.width + 2f * sweep) * progress
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(head - sweep, 0f),
                        end = Offset(head + sweep, 0f),
                    ),
                )
            },
    )
}

/**
 * 流式光标（docs/motion-spec.md §2.8）：1s 呼吸块，无打字机抖动。
 */
@Composable
fun QuineStreamingCursor(modifier: Modifier = Modifier) {
    val colors = QuineTheme.colors
    val reduced = QuineTheme.reducedMotion
    val transition = rememberInfiniteTransition(label = "streamingCursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streamingCursorAlpha",
    )
    Box(
        modifier = modifier
            .size(width = 8.dp, height = 18.dp)
            .background(colors.ink.copy(alpha = if (reduced) 1f else alpha)),
    )
}
