package com.quine.feature.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quine.core.design.component.QuineBreathingDot
import com.quine.core.design.theme.QuineTheme
import com.quine.core.storage.TaskStepType
import com.quine.core.tools.diff.DiffKind
import com.quine.core.tools.diff.TextDiff

/**
 * 五件套附件块中的两件（page-specs §2-3）。
 *
 * 网页块要浏览器、屏幕块要录屏（M4），这里**不假装支持**：[StepBlock] 里出现
 * 不认识的类型时，[UnknownBlock] 会如实说明，而不是渲染一个空壳。
 */
internal object BlockType {
    const val DIFF = "diff"
    const val TERMINAL = "terminal"
}

/** 折叠时露出几行：够看清改了什么，又不至于把时间线撑爆。 */
private const val COLLAPSED_LINES = 6

/** 展开后 diff / 终端的可见高度上限，超出内滚。 */
private val BLOCK_MAX_HEIGHT = 260.dp

@Composable
internal fun StepBlockView(
    block: StepBlock,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onFullscreen: (StepBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block.type) {
        BlockType.DIFF -> DiffBlock(
            block = block,
            expanded = expanded,
            onExpandChange = onExpandChange,
            modifier = modifier,
        )

        BlockType.TERMINAL -> TerminalBlock(
            block = block,
            expanded = expanded,
            onExpandChange = onExpandChange,
            onFullscreen = onFullscreen,
            modifier = modifier,
        )

        else -> UnknownBlock(block = block, modifier = modifier)
    }
}

/**
 * diff 块（page-specs §2-3a）：等宽；新增 = 「+」+ 极浅 ink 底；
 * 删除 = 「−」+ 灰字删除线。**不用红绿** —— 黑白纪律；`+/−` 前缀是无障碍的
 * 冗余通道，色弱或黑白模式下照样分得清。
 */
@Composable
private fun DiffBlock(
    block: StepBlock,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography

    val lines = remember(block) {
        val before = block.before
        val after = block.after
        if (before == null || after == null) emptyList() else TextDiff.diff(before, after)
    }
    val summary = remember(lines) { TextDiff.summary(lines) }
    val visible = if (expanded) lines else lines.take(COLLAPSED_LINES)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusCard))
            .background(colors.fill)
            .clickable(onClickLabel = if (expanded) "收起改动" else "展开改动") {
                onExpandChange(!expanded)
            }
            .animateContentSize()
            .padding(dimens.cardPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = block.path ?: "改动",
                style = typography.monoData,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "＋${summary.added} －${summary.removed}",
                style = typography.monoData,
                color = colors.textSecondary,
            )
        }

        if (lines.isEmpty()) {
            Text(
                text = "这次没留下改动前后的全文，看不了 diff。",
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
            return
        }

        val vertical = rememberScrollState()
        val horizontal = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(max = BLOCK_MAX_HEIGHT)
                .verticalScroll(vertical)
                .horizontalScroll(horizontal),
        ) {
            for (line in visible) {
                val isAdded = line.kind == DiffKind.ADDED
                val isRemoved = line.kind == DiffKind.REMOVED
                Row(
                    // 新增行铺极浅 ink 底（黑白纪律，不用绿）
                    modifier = Modifier.background(
                        if (isAdded) colors.ink.copy(alpha = 0.08f) else Color.Transparent,
                    ),
                ) {
                    Text(
                        text = when (line.kind) {
                            DiffKind.ADDED -> "+ "
                            DiffKind.REMOVED -> "− "
                            DiffKind.UNCHANGED -> "  "
                        },
                        style = typography.monoCode,
                        color = if (isRemoved) colors.textTertiary else colors.textSecondary,
                        softWrap = false,
                    )
                    Text(
                        text = if (line.text.isEmpty()) " " else line.text,
                        style = typography.monoCode,
                        color = when {
                            isRemoved -> colors.textTertiary
                            isAdded -> colors.textPrimary
                            else -> colors.textSecondary
                        },
                        textDecoration = if (isRemoved) TextDecoration.LineThrough else null,
                        softWrap = false,
                    )
                }
            }
        }

        if (!expanded && lines.size > COLLAPSED_LINES) {
            Text(
                text = "…还有 ${lines.size - COLLAPSED_LINES} 行",
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * 终端块（page-specs §2-3b）：等宽 + **永久暗面** —— 无论深浅主题都深色。
 * 颜色 token 里 `terminalBg` 是唯一不随主题翻转的一档（终端的仪式感）。
 * 展开后滚到尾部：命令跑完要看的是结果，不是开头那行 cd。
 */
@Composable
internal fun TerminalBlock(
    block: StepBlock,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onFullscreen: (StepBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography

    val text = block.text.orEmpty()
    val lines = remember(text) { text.split('\n') }
    val visible = if (expanded) lines else lines.take(COLLAPSED_LINES)
    val scroll = rememberScrollState()

    LaunchedEffect(text, expanded) {
        if (expanded) scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusCard))
            .background(colors.terminalBg)
            .animateContentSize()
            .padding(dimens.cardPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.title ?: "终端",
                style = typography.monoData,
                color = colors.terminalMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 右上「全屏」：详情页会把它抬成整屏终端。等宽字形，和终端同一套语言。
            Text(
                text = "⤢",
                style = typography.monoCode,
                color = colors.terminalMuted,
                modifier = Modifier
                    .clickable(onClickLabel = "全屏查看") { onFullscreen(block) }
                    .padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(max = BLOCK_MAX_HEIGHT)
                .verticalScroll(scroll)
                .clickable(onClickLabel = if (expanded) "收起输出" else "展开输出") {
                    onExpandChange(!expanded)
                },
        ) {
            for (line in visible) {
                Text(
                    text = if (line.isEmpty()) " " else line,
                    style = typography.monoTerminal,
                    color = colors.terminalText,
                    softWrap = false,
                )
            }
        }

        if (!expanded && lines.size > COLLAPSED_LINES) {
            Text(
                text = "…还有 ${lines.size - COLLAPSED_LINES} 行",
                style = typography.caption,
                color = colors.terminalMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 网页 / 屏幕这类 M4 才有的块：如实说明，不渲染空壳。 */
@Composable
private fun UnknownBlock(block: StepBlock, modifier: Modifier = Modifier) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    Text(
        text = "「${block.title ?: block.type}」这种附件还显示不了（要等浏览器 / 录屏能力）。",
        style = QuineTheme.typography.caption,
        color = colors.textTertiary,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusCard))
            .background(colors.fill)
            .padding(dimens.cardPadding),
    )
}

/** 被「全屏」抬起来的整屏终端。 */
@Composable
internal fun FullscreenTerminal(
    block: StepBlock,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography
    val scroll = rememberScrollState()

    LaunchedEffect(block) { scroll.animateScrollTo(scroll.maxValue) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.terminalBg)
            .padding(dimens.pageMargin),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = block.title ?: "终端",
                style = typography.monoData,
                color = colors.terminalMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "✕",
                style = typography.monoCode,
                color = colors.terminalText,
                modifier = Modifier.clickable(onClickLabel = "退出全屏") { onClose() },
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .weight(1f)
                .verticalScroll(scroll)
                .horizontalScroll(rememberScrollState()),
        ) {
            for (line in block.text.orEmpty().split('\n')) {
                Text(
                    text = if (line.isEmpty()) " " else line,
                    style = typography.monoTerminal,
                    color = colors.terminalText,
                    softWrap = false,
                )
            }
        }
    }
}

/** 时间线上那颗状态点：思考中呼吸（正在干活），其余静态。 */
@Composable
internal fun StepDot(kind: TaskStepType, modifier: Modifier = Modifier) {
    val colors = QuineTheme.colors
    if (kind == TaskStepType.THINK) {
        QuineBreathingDot(modifier = modifier, size = 8.dp)
        return
    }
    val color = when (kind) {
        TaskStepType.ERROR -> colors.danger
        TaskStepType.APPROVAL -> colors.accent
        else -> colors.textSecondary
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
