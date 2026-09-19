package com.quine.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.quine.core.design.theme.QuineTheme

/**
 * 按下时轻微缩小的反馈（docs/motion-spec.md §2.6）。
 *
 * 返回值里的 [PressScale.interactionSource] **必须由调用方传给它的 clickable /
 * Button**，否则这个 source 收不到按压事件，缩放永远不会发生 —— 那种 bug 的表现是
 * 「写了动画但按下去没反应」，很难看出来。
 *
 * reduced-motion 下直接返回原 Modifier：位移动画一律降级为不动。
 */
@Composable
fun rememberPressScale(enabled: Boolean = true): PressScale {
    val motion = QuineTheme.motion
    val reduced = QuineTheme.reducedMotion
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && !reduced && pressed) PRESSED_SCALE else 1f,
        animationSpec = tween(durationMillis = motion.duration(motion.micro, reduced)),
        label = "pressScale",
    )
    return PressScale(interactionSource, Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    })
}

class PressScale(
    val interactionSource: MutableInteractionSource,
    val modifier: Modifier,
)

private const val PRESSED_SCALE = 0.97f
