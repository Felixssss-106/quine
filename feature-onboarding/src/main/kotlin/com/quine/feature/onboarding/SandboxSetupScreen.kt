package com.quine.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quine.core.design.component.QuineBreathingDot
import com.quine.core.design.component.QuinePrimaryButton
import com.quine.core.design.component.QuineTextButton
import com.quine.core.design.theme.QuineTheme
import com.quine.core.sandbox.SandboxStage
import com.quine.core.sandbox.SandboxState
import com.quine.core.sandbox.headline
import kotlinx.coroutines.delay

/** 屏二的入口。装好了会自己走「黑白反转 → 进主界面」。 */
@Composable
fun SandboxSetupRoute(
    deps: SandboxSetupDeps,
    onDone: () -> Unit,
) {
    val viewModel: SandboxSetupViewModel = viewModel(factory = SandboxSetupViewModel.factory(deps))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expanded by viewModel.logExpanded.collectAsStateWithLifecycle()
    val flash by viewModel.flash.collectAsStateWithLifecycle()

    // 已经装好了就直接过（"之后秒开"），不必再走一遍仪式
    LaunchedEffect(Unit) {
        if (viewModel.alreadyReady) {
            viewModel.finish()
            onDone()
        }
    }

    LaunchedEffect(flash) {
        if (!flash) return@LaunchedEffect
        // motion §2.9：反转 120ms → 回正 → 进主界面（400ms）
        delay(SandboxSetupViewModel.FLASH_MILLIS + 400L)
        onDone()
    }

    SandboxSetupScreen(
        state = state,
        logExpanded = expanded,
        flash = flash,
        onToggleLog = viewModel::toggleLog,
        onRetry = viewModel::retry,
        onClear = viewModel::clear,
    )
}

@Composable
fun SandboxSetupScreen(
    state: SandboxState,
    logExpanded: Boolean,
    flash: Boolean,
    onToggleLog: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography
    val failed = state as? SandboxState.Failed

    val progress = remember(state) { overallProgress(state) }
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 240, easing = LinearEasing),
        label = "setupProgress",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .invertIf(flash)
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = dimens.pageMargin)
            .padding(top = dimens.sectionGapLarge, bottom = dimens.sectionGap),
    ) {
        Text(
            text = "搭建你的本地工作间。",
            style = typography.display,
            color = colors.textPrimary,
        )

        Spacer(Modifier.height(dimens.sectionGapLarge))

        StageList(state = state)

        Spacer(Modifier.height(dimens.sectionGap))

        // 进度条：2pt 细条。失败就停在原地并转成 danger。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.line, RoundedCornerShape(1.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(
                        if (failed != null) colors.danger else colors.ink,
                        RoundedCornerShape(1.dp),
                    ),
            )
        }

        Spacer(Modifier.height(dimens.sectionGap))

        if (failed != null) {
            Text(
                text = failed.headline(),
                style = typography.headline,
                color = colors.danger,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = failed.error.impact + failed.error.nextStep,
                style = typography.footnote,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(dimens.sectionGap))

        LogPanel(
            lines = when (state) {
                is SandboxState.Working -> state.log
                is SandboxState.Failed -> state.log
                is SandboxState.Ready -> state.log
                SandboxState.Idle -> emptyList()
            },
            expanded = logExpanded,
            onToggle = onToggleLog,
            modifier = Modifier.weight(1f, fill = false),
        )

        Spacer(Modifier.height(dimens.sectionGap))

        if (failed != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.grid * 3)) {
                QuinePrimaryButton(text = "重试", onClick = onRetry, modifier = Modifier.weight(1f))
                QuineTextButton(text = "清理空间", onClick = onClear)
            }
        } else {
            Text(
                text = "首次约 1–3 分钟，之后秒开。占用约 200 MB。",
                style = typography.caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun StageList(state: SandboxState) {
    val colors = QuineTheme.colors
    val typography = QuineTheme.typography
    val dimens = QuineTheme.dimens
    val done = state.completedStages()
    val current = state.currentStage()
    val failed = state as? SandboxState.Failed

    Column(verticalArrangement = Arrangement.spacedBy(dimens.grid * 3)) {
        for (stage in SandboxStage.entries) {
            val isDone = stage in done
            val isCurrent = stage == current
            val isFailed = failed != null && stage == failed.stage

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                    when {
                        isFailed -> Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colors.danger, androidx.compose.foundation.shape.CircleShape),
                        )

                        isCurrent && !isDone -> QuineBreathingDot()
                        isDone -> Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colors.ink, androidx.compose.foundation.shape.CircleShape),
                        )

                        else -> Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colors.line, androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(dimens.grid * 3))
                Text(
                    text = "${stage.stepMark} ${stage.label}",
                    style = typography.body,
                    color = when {
                        isFailed -> colors.danger
                        isDone || isCurrent -> colors.textPrimary
                        else -> colors.textSecondary
                    },
                )
            }
        }
    }
}

/**
 * 等宽日志区（page-specs §1：Mono / 低对比 / **可折叠**），
 * motion §2.9 要求日志「缓缓滚出，约 40 字/秒，不抢戏」。
 *
 * 逐字推进会落后于真实日志（一行 40 字就要 1 秒），所以积压超过 [CATCH_UP_CHARS]
 * 就加速追上 —— 否则安装早就完了，日志还在慢慢爬。
 * 开了「减弱动效」时直接整段显示。
 */
@Composable
private fun LogPanel(
    lines: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val typography = QuineTheme.typography
    val dimens = QuineTheme.dimens
    val reduced = QuineTheme.reducedMotion

    val total = remember(lines) { lines.sumOf { it.length + 1 } }
    var shown by remember(lines, reduced) { mutableIntStateOf(if (reduced) Int.MAX_VALUE else 0) }

    LaunchedEffect(lines, reduced, total) {
        if (reduced) {
            shown = Int.MAX_VALUE
            return@LaunchedEffect
        }
        while (shown < total) {
            val backlog = total - shown
            val step = if (backlog > CATCH_UP_CHARS) backlog / 8 else 1
            delay(MS_PER_CHAR)
            shown += step
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .semantics { contentDescription = if (expanded) "收起日志" else "展开日志" }
                .padding(vertical = dimens.grid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "收起日志" else "展开日志（${lines.size} 行）",
                style = typography.caption,
                color = colors.textTertiary,
            )
        }

        if (expanded) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(colors.fill, RoundedCornerShape(dimens.radiusCard))
                    .padding(dimens.grid * 3)
                    .verticalScroll(scroll),
            ) {
                var remaining = if (reduced) Int.MAX_VALUE else shown
                for (line in lines) {
                    if (remaining <= 0) break
                    val visible = line.take(remaining)
                    remaining -= line.length + 1
                    Text(
                        text = visible,
                        style = typography.monoCode,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        } else if (lines.isNotEmpty()) {
            Text(
                text = lines.last(),
                style = typography.monoCode,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 总进度：把「第几步 + 这一步内的进度」折算成 0..1。 */
private fun overallProgress(state: SandboxState): Float {
    if (state is SandboxState.Ready) return 1f
    val current = state.currentStage() ?: return 0f
    val inner = (state as? SandboxState.Working)?.progress ?: 0f
    return ((current.ordinal + inner.coerceIn(0f, 1f)) / SandboxStage.entries.size)
}

/** 黑白反转：完成那一下的仪式感（motion §2.9）。 */
/**
 * 黑白反转。
 *
 * 做法是先画正常内容，再用 Difference 混合模式盖一层白 —— 与白色做差值就是反色。
 * （`graphicsLayer` 的 `colorFilter` 在这个 Compose 版本的 scope 里还没有，故不走那条路。）
 */
private fun Modifier.invertIf(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(color = Color.White, blendMode = BlendMode.Difference)
            }
    }

private const val MS_PER_CHAR = 25L // ≈40 字/秒
private const val CATCH_UP_CHARS = 120
