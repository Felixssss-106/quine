package com.quine.core.design.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quine.core.design.theme.QuineTheme
import kotlinx.coroutines.delay

/** 相邻两项的入场间隔。 */
private const val STAGGER_MILLIS = 24L

/** 间隔总上限：列表很长时不至于最后一项等上一秒。 */
private const val MAX_STAGGER_MILLIS = 160L

private val ENTRANCE_OFFSET: Dp = 8.dp

/**
 * 列表项入场：淡入 + 8dp 上移，按顺序错开。
 *
 * 刻意做得很轻 —— 这是「内容出现了」的提示，不是表演。位移一大、
 * 间隔一长，快速滑动时就会变成一片抖动。
 *
 * reduced-motion 下直接可见、不做动画。
 */
@Composable
fun QuineItemEntrance(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = QuineTheme.motion
    val reduced = QuineTheme.reducedMotion

    var visible by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay((index * STAGGER_MILLIS).coerceAtMost(MAX_STAGGER_MILLIS))
        visible = true
    }

    val duration = motion.duration(motion.standard, reduced)
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = duration),
        label = "entranceAlpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else ENTRANCE_OFFSET,
        animationSpec = tween(durationMillis = duration),
        label = "entranceOffset",
    )

    Box(
        modifier = modifier
            .alpha(alpha)
            .offset(y = offsetY),
    ) {
        content()
    }
}
