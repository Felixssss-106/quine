package com.quine.feature.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quine.core.design.theme.QuineTheme
import com.quine.core.storage.TaskStatus
import com.quine.core.storage.TaskStepType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * 任务面的渲染测试。
 *
 * **函数名一律不带空格**：minSdk 29 → DEX 039，不允许 SimpleName 含空格
 * （`src/test` 的 JVM 单测没这个限制，反引号随便用）。
 */
@RunWith(AndroidJUnit4::class)
class TasksScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val noop: () -> Unit = {}

    private fun task(
        id: String,
        title: String,
        status: TaskStatus,
        duration: String? = null,
        error: String? = null,
    ) = TaskRunUi(
        id = id,
        title = title,
        status = status,
        statusLabel = status.label(),
        durationLabel = duration,
        originLabel = null,
        createdAtLabel = "14:03:11",
        errorText = error,
    )

    @Test fun empty_list_shows_placeholder() {
        compose.setContent {
            QuineTheme {
                TasksScreen(
                    state = TasksUiState(),
                    onOpenTask = {},
                    onClose = noop,
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("还没有任务").assertIsDisplayed()
    }

    @Test fun list_groups_by_status_and_shows_row() {
        compose.setContent {
            QuineTheme {
                TasksScreen(
                    state = TasksUiState(
                        running = listOf(task("r1", "整理周报", TaskStatus.RUNNING)),
                        finished = listOf(task("s1", "修好登录", TaskStatus.SUCCEEDED, duration = "00:42")),
                    ),
                    onOpenTask = {},
                    onClose = noop,
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("进行中").assertIsDisplayed()
        compose.onNodeWithText("已完成").assertIsDisplayed()
        compose.onNodeWithText("整理周报").assertIsDisplayed()
        compose.onNodeWithText("修好登录").assertIsDisplayed()
        compose.onNodeWithText("14:03:11 · 耗时 00:42").assertIsDisplayed()
    }

    @Test fun detail_renders_header_timeline_and_artifacts() {
        val detail = TaskDetailUiState(
            run = task("t1", "整理周报", TaskStatus.SUCCEEDED, duration = "00:42"),
            artifacts = listOf(ArtifactUi(snapshotId = "s", name = "notes/todo.md", timeLabel = "14:03:11")),
        )
        val timeline = listOf(
            TimelineItemUi(
                id = "step1",
                timeLabel = "14:03:20",
                title = "改了 1 个文件",
                summary = "把 two 改成 2",
                kind = TaskStepType.TOOL,
                blocks = listOf(
                    StepBlock(type = "diff", path = "a.txt", before = "one\ntwo", after = "one\n2"),
                ),
            ),
            TimelineItemUi(
                id = "step2",
                timeLabel = "14:03:30",
                title = "跑了 npm test",
                summary = null,
                kind = TaskStepType.TOOL,
                blocks = listOf(
                    StepBlock(type = "terminal", title = "npm test", text = "ok"),
                ),
            ),
        )

        compose.setContent {
            QuineTheme {
                TaskDetailScreen(
                    detail = detail,
                    timeline = timeline,
                    onBack = noop,
                    onOpenChat = noop,
                    onCancel = noop,
                )
            }
        }

        compose.onNodeWithText("整理周报").assertIsDisplayed()
        compose.onNodeWithText("耗时 00:42 · 14:03:11 开始").assertIsDisplayed()
        compose.onNodeWithText("改了 1 个文件").assertIsDisplayed()
        compose.onNodeWithText("跑了 npm test").assertIsDisplayed()
        compose.onNodeWithText("a.txt").assertIsDisplayed()
        compose.onNodeWithText("产物").assertIsDisplayed()
        compose.onNodeWithText("notes/todo.md").assertIsDisplayed()
    }

    @Test fun detail_running_offers_stop() {
        val detail = TaskDetailUiState(run = task("t1", "整理周报", TaskStatus.RUNNING))
        compose.setContent {
            QuineTheme {
                TaskDetailScreen(
                    detail = detail,
                    timeline = emptyList(),
                    onBack = noop,
                    onOpenChat = noop,
                    onCancel = noop,
                )
            }
        }
        compose.onNodeWithText("停止").assertIsDisplayed()
        compose.onNodeWithText("去聊天里看看").assertIsDisplayed()
    }

    @Test fun detail_gone_task_says_so() {
        compose.setContent {
            QuineTheme {
                TaskDetailScreen(
                    detail = TaskDetailUiState(),
                    timeline = emptyList(),
                    onBack = noop,
                    onOpenChat = noop,
                    onCancel = noop,
                )
            }
        }
        compose.onNodeWithText("这个任务已经不在了。").assertIsDisplayed()
    }
}
