package com.quine.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quine.core.design.component.QuineBreathingDot
import com.quine.core.design.component.QuineItemEntrance
import com.quine.core.design.component.QuineDivider
import com.quine.core.design.theme.QuineTheme
import com.quine.core.storage.TaskStatus

@Composable
fun TasksRoute(
    deps: TasksDeps,
    onOpenTask: (String) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel: TasksViewModel = viewModel(factory = TasksViewModel.factory(deps))
    val state by viewModel.state.collectAsStateWithLifecycle()

    TasksScreen(
        state = state,
        onOpenTask = onOpenTask,
        onClose = onClose,
        onCancel = viewModel::cancel,
    )
}

@Composable
fun TasksScreen(
    state: TasksUiState,
    onOpenTask: (String) -> Unit,
    onClose: () -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = dimens.pageMargin, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "任务",
                style = QuineTheme.typography.headline,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "关闭",
                style = QuineTheme.typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier
                    .clickable(onClickLabel = "返回聊天") { onClose() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        QuineDivider()

        if (state.isEmpty) {
            EmptyTasks(modifier = Modifier.fillMaxSize())
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = dimens.sectionGap),
        ) {
            if (state.running.isNotEmpty()) {
                TaskSection(
                    title = "进行中",
                    tasks = state.running,
                    onOpenTask = onOpenTask,
                    onCancel = onCancel,
                )
            }
            if (state.needsApproval.isNotEmpty()) {
                TaskSection(
                    title = "待确认",
                    tasks = state.needsApproval,
                    onOpenTask = onOpenTask,
                    onCancel = onCancel,
                )
            }
            if (state.finished.isNotEmpty()) {
                TaskSection(
                    title = "已完成",
                    tasks = state.finished,
                    onOpenTask = onOpenTask,
                    onCancel = onCancel,
                )
            }
            if (state.failed.isNotEmpty()) {
                TaskSection(
                    title = "未完成",
                    tasks = state.failed,
                    onOpenTask = onOpenTask,
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun EmptyTasks(modifier: Modifier = Modifier) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    Column(
        modifier = modifier.padding(horizontal = dimens.pageMargin),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "还没有任务",
            style = QuineTheme.typography.title,
            color = colors.textPrimary,
        )
        Text(
            text = "任务记录将显示在此。",
            style = QuineTheme.typography.footnote,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TaskSection(
    title: String,
    tasks: List<TaskRunUi>,
    onOpenTask: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    Text(
        text = title,
        style = QuineTheme.typography.caption,
        color = colors.textTertiary,
        modifier = Modifier.padding(
            start = dimens.pageMargin,
            end = dimens.pageMargin,
            top = dimens.sectionGap,
            bottom = 8.dp,
        ),
    )
    Column {
        tasks.forEachIndexed { index, task ->
            QuineItemEntrance(index = index) {
                TaskRow(
                    task = task,
                    onClick = { onOpenTask(task.id) },
                    onCancel = if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED) {
                        { onCancel(task.id) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskRunUi,
    onClick: () -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开任务") { onClick() }
            .padding(horizontal = dimens.pageMargin, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = task.title,
                style = QuineTheme.typography.headline,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TaskStatusPill(status = task.status)
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildList {
                    add(task.createdAtLabel)
                    task.durationLabel?.let { add("耗时 $it") }
                }.joinToString(" · "),
                style = QuineTheme.typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            if (onCancel != null) {
                Text(
                    text = "停止",
                    style = QuineTheme.typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clickable(onClickLabel = "停止任务") { onCancel() }
                        .padding(start = 12.dp),
                )
            }
        }
        task.errorText?.let {
            Text(
                text = it,
                style = QuineTheme.typography.caption,
                color = colors.danger,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 状态胶囊（page-specs §2-1）：运行中 = 呼吸点；其余静态描边。
 * **不靠颜色区分状态** —— 颜色只留给危险（失败/已取消用 danger）。
 */
@Composable
internal fun TaskStatusPill(status: TaskStatus, modifier: Modifier = Modifier) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val running = status == TaskStatus.RUNNING
    val shape = RoundedCornerShape(dimens.radiusPill)

    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.fill)
            .border(dimens.hairline, colors.line, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (running) {
            QuineBreathingDot(size = 6.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            TaskStatus.FAILED, TaskStatus.CANCELLED -> colors.danger
                            TaskStatus.NEEDS_APPROVAL -> colors.accent
                            else -> colors.textTertiary
                        },
                    ),
            )
        }
        Text(
            text = status.label(),
            style = QuineTheme.typography.caption,
            color = if (status == TaskStatus.FAILED || status == TaskStatus.CANCELLED) {
                colors.danger
            } else {
                colors.textSecondary
            },
        )
    }
}
