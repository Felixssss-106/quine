package com.quine.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quine.core.design.component.QuineDivider
import com.quine.core.design.component.QuineSecondaryButton
import com.quine.core.design.component.QuineTextButton
import com.quine.core.design.theme.QuineTheme
import com.quine.core.storage.TaskStatus

/** 时间线一次露多少条（page-specs §2：长任务每 20 条折叠一组）。 */
private const val TIMELINE_PAGE = 20

@Composable
fun TaskDetailRoute(
    deps: TasksDeps,
    taskId: String,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val viewModel: TasksViewModel = viewModel(factory = TasksViewModel.factory(deps))
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline(taskId).collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(taskId) { viewModel.loadDetail(taskId) }

    TaskDetailScreen(
        detail = detail,
        timeline = timeline,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onCancel = { viewModel.cancel(taskId) },
    )
}

@Composable
fun TaskDetailScreen(
    detail: TaskDetailUiState,
    timeline: List<TimelineItemUi>,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    // 「简洁 ↔ 详细」：控制条目默认展开程度（page-specs §2 状态与边界）。
    var verbosity by rememberSaveable { mutableFloatStateOf(1f) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf<StepBlock?>(null) }

    val filtered = remember(timeline, filter) {
        val wanted = TimelineFilter.entries[filter]
        if (wanted == TimelineFilter.ALL) timeline else timeline.filter { it.blocks.any { b -> b.type == wanted.blockType } }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "返回",
                    style = QuineTheme.typography.footnote,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clickable(onClickLabel = "返回任务列表") { onBack() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            QuineDivider()

            val run = detail.run
            if (run == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "任务不存在",
                        style = QuineTheme.typography.footnote,
                        color = colors.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = dimens.sectionGap),
                ) {
                    item { TaskHeaderCard(run = run) }
                    item {
                        FilterChipRow(
                            selected = filter,
                            // 换筛选就收回折叠：原来展开的那批跟新筛选没关系了。
                            onSelect = { filter = it; showAll = false },
                        )
                    }
                    item {
                        VerbositySlider(
                            value = verbosity,
                            onValueChange = { verbosity = it },
                        )
                    }
                    item { QuineDivider() }

                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "当前筛选无内容：${TimelineFilter.entries[filter].label}这次没有内容。",
                                style = QuineTheme.typography.caption,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(dimens.pageMargin),
                            )
                        }
                    } else {
                        val visible = if (showAll) filtered else filtered.take(TIMELINE_PAGE)
                        items(visible, key = { it.id }) { item ->
                            val defaultExpanded = when (verbosity.toInt()) {
                                0 -> false
                                2 -> true
                                // 适中：只展开最新一条 —— 正在看的就是它
                                else -> item.id == visible.last().id
                            }
                            TimelineEntry(
                                item = item,
                                defaultExpanded = defaultExpanded,
                                onFullscreen = { fullscreen = it },
                            )
                        }
                        if (!showAll && filtered.size > TIMELINE_PAGE) {
                            item {
                                Text(
                                    text = "…还有 ${filtered.size - TIMELINE_PAGE} 条",
                                    style = QuineTheme.typography.caption,
                                    color = colors.textTertiary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClickLabel = "展开全部") { showAll = true }
                                        .padding(dimens.pageMargin),
                                )
                            }
                        }
                    }

                    if (detail.artifacts.isNotEmpty()) {
                        item { ArtifactSection(artifacts = detail.artifacts) }
                    }
                }

                ActionBar(
                    status = run.status,
                    errorText = run.errorText,
                    onCancel = onCancel,
                    onOpenChat = onOpenChat,
                )
            }
        }

        // 整屏终端：从终端块右上「全屏」抬起来，盖住整个详情页。
        fullscreen?.let { block ->
            FullscreenTerminal(
                block = block,
                onClose = { fullscreen = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 顶部过滤 chips：全部 / 文件 / 终端 / 网页 / 屏幕（page-specs §2-2）。 */
private enum class TimelineFilter(val label: String, val blockType: String?) {
    ALL("全部", null),
    FILE("文件", BlockType.DIFF),
    TERMINAL("终端", BlockType.TERMINAL),
    WEB("网页", "web"),
    SCREEN("屏幕", "screen"),
}

@Composable
private fun FilterChipRow(selected: Int, onSelect: (Int) -> Unit) {
    val dimens = QuineTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pageMargin, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineFilter.entries.forEachIndexed { index, entry ->
            Chip(
                text = entry.label,
                selected = index == selected,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusPill)
    Text(
        text = text,
        style = QuineTheme.typography.caption,
        color = if (selected) colors.onInk else colors.textSecondary,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.ink else colors.fill)
            .border(dimens.hairline, if (selected) colors.ink else colors.line, shape)
            .clickable(onClickLabel = text) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * 「简洁 ↔ 详细」滑杆（page-specs §2 状态与边界）：
 * 简洁 = 全折叠；适中 = 只展开最新一条；详细 = 全展开。
 */
@Composable
private fun VerbositySlider(value: Float, onValueChange: (Float) -> Unit) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "简洁", style = QuineTheme.typography.caption, color = colors.textTertiary)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..2f,
            steps = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = colors.ink,
                activeTrackColor = colors.ink,
                inactiveTrackColor = colors.line,
            ),
        )
        Text(text = "详细", style = QuineTheme.typography.caption, color = colors.textTertiary)
    }
}

/** 任务头卡（page-specs §2-1）：任务名 + 状态胶囊 + 读数行 + 弱信息行。 */
@Composable
private fun TaskHeaderCard(run: TaskRunUi) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    Column(modifier = Modifier.padding(horizontal = dimens.pageMargin, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = run.title,
                style = QuineTheme.typography.title,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TaskStatusPill(status = run.status)
        }

        // 读数行：只放真有的数。token / 费用口径还没定（蓝图 §7 是持续风险），
        // 等定了再补 —— 现在编两个数给用户看是骗人。
        Text(
            text = buildList {
                run.durationLabel?.let { add("耗时 $it") }
                add("${run.createdAtLabel} 开始")
            }.joinToString(" · "),
            style = QuineTheme.typography.monoData,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        run.originLabel?.let {
            Text(
                text = it,
                style = QuineTheme.typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (run.status == TaskStatus.QUEUED) {
            Text(
                text = "排队中",
                style = QuineTheme.typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 时间线的一条（page-specs §2-3）：时间戳 · 状态点 · 人话标题 · 摘要 · 附件块。 */
@Composable
private fun TimelineEntry(
    item: TimelineItemUi,
    defaultExpanded: Boolean,
    onFullscreen: (StepBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pageMargin, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.timeLabel,
                style = QuineTheme.typography.caption,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            StepDot(kind = item.kind)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.title,
                style = QuineTheme.typography.headline,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        item.summary?.let {
            Text(
                text = it,
                style = QuineTheme.typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 24.dp, top = 2.dp),
            )
        }

        item.blocks.forEachIndexed { index, block ->
            var expanded by remember(defaultExpanded, item.id, index) { mutableStateOf(defaultExpanded) }
            StepBlockView(
                block = block,
                expanded = expanded,
                onExpandChange = { expanded = it },
                onFullscreen = onFullscreen,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp),
            )
        }
    }
}

/** 产物区（page-specs §2-4）：产物。 */
@Composable
private fun ArtifactSection(artifacts: List<ArtifactUi>) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pageMargin, vertical = 16.dp),
    ) {
        QuineDivider()
        Text(
            text = "产物",
            style = QuineTheme.typography.headline,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        artifacts.forEach { artifact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = artifact.name,
                    style = QuineTheme.typography.monoData,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = artifact.timeLabel,
                    style = QuineTheme.typography.caption,
                    color = colors.textTertiary,
                )
            }
        }
        // 「全部导出 / 分享」要等导出通道定下来（M2），先不放点了没反应的按钮。
    }
}

/**
 * 操作条（page-specs §2-5）。
 *
 * **只放现在真能按的**：停止 = 把状态改成 CANCELLED，DAO 支持。
 * 「重试 / 批准 / 接管」都要等任务执行器接进来才有意义 —— 与其给点了没反应的
 * 按钮，不如把缺什么说清楚。
 */
@Composable
private fun ActionBar(
    status: TaskStatus,
    errorText: String?,
    onCancel: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val live = status == TaskStatus.RUNNING || status == TaskStatus.QUEUED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(horizontal = dimens.pageMargin, vertical = 12.dp),
    ) {
        QuineDivider()
        Spacer(modifier = Modifier.height(12.dp))

        if (status == TaskStatus.NEEDS_APPROVAL) {
            Text(
                text = "审批功能暂未开放。",
                style = QuineTheme.typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (status == TaskStatus.FAILED) {
            Text(
                text = errorText ?: "执行失败",
                style = QuineTheme.typography.caption,
                color = colors.danger,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                QuineSecondaryButton(text = "停止", onClick = onCancel, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
            }
            QuineTextButton(text = "前往会话", onClick = onOpenChat)
        }
    }
}
