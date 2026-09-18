package com.quine.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quine.core.design.theme.QuineTheme

/**
 * chip（docs/visual-spec.md §3.1、§4-A）：高度 32–36，圆角药丸。
 * 选中 = ink 底反白（§4.1 首启屏「供应商 chips 行」）。
 * 选中态切换走 120ms 微动效 + light 触感（docs/motion-spec.md §2.6）。
 */
@Composable
fun QuineChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val motion = QuineTheme.motion
    val reduced = QuineTheme.reducedMotion
    val haptics = rememberQuineHaptics()

    val container by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.fill,
        animationSpec = tween(motion.duration(motion.micro, reduced)),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onInk else colors.textSecondary,
        animationSpec = tween(motion.duration(motion.micro, reduced)),
        label = "chipContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.line,
        animationSpec = tween(motion.duration(motion.micro, reduced)),
        label = "chipBorder",
    )

    Box(
        modifier = modifier
            .height(dimens.chipHeight)
            .clip(RoundedCornerShape(dimens.radiusPill))
            .background(container)
            .border(dimens.hairline, borderColor, RoundedCornerShape(dimens.radiusPill))
            .clickable(enabled = enabled) {
                haptics.light()
                onClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = QuineTheme.typography.footnote,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 档位胶囊（docs/visual-spec.md §4-C）：保守 / 标准 / 狂奔 = **文字 + 圆点，不靠颜色**。
 * 圆点位移 120ms + 文字 crossfade（docs/motion-spec.md §2.8）。
 */
@Composable
fun QuineTrustPill(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val haptics = rememberQuineHaptics()
    val shape = RoundedCornerShape(dimens.radiusPill)

    Row(
        modifier = modifier
            .height(dimens.chipHeight)
            .clip(shape)
            .background(colors.fill)
            .border(dimens.hairline, colors.line, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radiusPill))
                    .clickable {
                        haptics.light()
                        onSelect(index)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.ink else colors.textTertiary),
                )
                Text(
                    text = label,
                    style = QuineTheme.typography.caption,
                    color = if (active) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
