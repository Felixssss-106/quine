package com.quine.feature.tasks

import com.quine.core.storage.SnapshotEntity
import com.quine.core.storage.TaskOrigin
import com.quine.core.storage.TaskRunEntity
import com.quine.core.storage.TaskStatus
import com.quine.core.storage.TaskStepEntity
import com.quine.core.storage.TaskStepType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun run(
        title: String,
        status: TaskStatus,
        startedAt: Long? = null,
        endedAt: Long? = null,
        conversationId: String? = null,
        error: String? = null,
    ) = TaskRunEntity(
        id = title,
        title = title,
        status = status.name,
        originType = TaskOrigin.CONVERSATION.name,
        originConversationId = conversationId,
        createdAt = 1_700_000_000_000L,
        startedAt = startedAt,
        endedAt = endedAt,
        errorJson = error,
    )

    private class FakeDeps(
        private val runs: List<TaskRunEntity> = emptyList(),
        private val steps: List<TaskStepEntity> = emptyList(),
        private val snapshots: List<SnapshotEntity> = emptyList(),
        private val titles: Map<String, String> = emptyMap(),
    ) : TasksDeps {
        val cancelled = mutableListOf<String>()

        override fun observeRuns(): Flow<List<TaskRunEntity>> = flowOf(runs)
        override fun observeSteps(taskId: String): Flow<List<TaskStepEntity>> =
            flowOf(steps.filter { it.taskId == taskId })

        override suspend fun findRun(id: String): TaskRunEntity? = runs.firstOrNull { it.id == id }
        override suspend fun conversationTitle(id: String?): String? = id?.let { titles[it] }
        override suspend fun artifacts(taskId: String): List<ArtifactUi> =
            snapshots.filter { it.taskId == taskId }.map { it.toArtifact() }

        override suspend fun cancel(id: String) {
            cancelled += id
        }
    }

    @Test fun `列表按状态分成四组_排队归到在干活`() = runTest {
        val deps = FakeDeps(
            runs = listOf(
                run("r1", TaskStatus.RUNNING),
                run("q1", TaskStatus.QUEUED),
                run("a1", TaskStatus.NEEDS_APPROVAL),
                run("s1", TaskStatus.SUCCEEDED),
                run("f1", TaskStatus.FAILED),
                run("c1", TaskStatus.CANCELLED),
            ),
        )
        val vm = TasksViewModel(deps)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("r1", "q1"), state.running.map { it.id })
        assertEquals(listOf("a1"), state.needsApproval.map { it.id })
        assertEquals(listOf("s1"), state.finished.map { it.id })
        assertEquals(listOf("f1", "c1"), state.failed.map { it.id })
    }

    @Test fun `一个任务都没有时是空态`() = runTest {
        val vm = TasksViewModel(FakeDeps())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        advanceUntilIdle()
        assertTrue(vm.state.value.isEmpty)
    }

    @Test fun `停止会真的去取消`() = runTest {
        val deps = FakeDeps(runs = listOf(run("r1", TaskStatus.RUNNING)))
        val vm = TasksViewModel(deps)
        vm.cancel("r1")
        advanceUntilIdle()
        assertEquals(listOf("r1"), deps.cancelled)
    }

    @Test fun `详情页带出会话标题与产物`() = runTest {
        val deps = FakeDeps(
            runs = listOf(
                run(
                    title = "整理周报",
                    status = TaskStatus.SUCCEEDED,
                    startedAt = 1_000_000L,
                    endedAt = 1_042_000L,
                    conversationId = "c1",
                ),
            ),
            titles = mapOf("c1" to "帮我整理周报"),
            snapshots = listOf(
                SnapshotEntity(
                    id = "snap1",
                    path = "notes/todo.md",
                    blobRef = "blob/sha256/abc",
                    root = "私有工作区",
                    taskId = "整理周报",
                    createdAt = 1_700_000_000_000L,
                ),
            ),
        )
        val vm = TasksViewModel(deps)
        vm.loadDetail("整理周报")
        advanceUntilIdle()

        val detail = vm.detail.value
        assertEquals("整理周报", detail.run?.title)
        assertEquals("00:42", detail.run?.durationLabel)
        assertEquals("来自会话《帮我整理周报》", detail.run?.originLabel)
        assertEquals(listOf("notes/todo.md"), detail.artifacts.map { it.name })
    }

    @Test fun `payload 坏了也不该让时间线崩`() = runTest {
        val deps = FakeDeps(
            steps = listOf(
                TaskStepEntity(
                    id = "s1",
                    taskId = "t",
                    idx = 0,
                    type = TaskStepType.TOOL.name,
                    title = "改了 3 个文件",
                    payloadJson = "{ 这不是 json",
                    ts = 1_700_000_000_000L,
                ),
            ),
        )
        val vm = TasksViewModel(deps)
        val items = mutableListOf<List<TimelineItemUi>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.timeline("t").collect { items += it } }
        advanceUntilIdle()

        val item = items.last().single()
        assertEquals("改了 3 个文件", item.title)
        assertTrue(item.blocks.isEmpty())
    }

    @Test fun `时间线解析出 diff 与终端块`() = runTest {
        val payload = """
            {"summary":"改了 1 个文件","blocks":[
              {"type":"diff","path":"a.txt","before":"one\ntwo","after":"one\n2"},
              {"type":"terminal","title":"npm test","text":"ok"}
            ]}
        """.trimIndent()
        val deps = FakeDeps(
            steps = listOf(
                TaskStepEntity(
                    id = "s1",
                    taskId = "t",
                    idx = 0,
                    type = TaskStepType.TOOL.name,
                    title = "改了 1 个文件",
                    payloadJson = payload,
                    ts = 1_700_000_000_000L,
                ),
            ),
        )
        val vm = TasksViewModel(deps)
        val items = mutableListOf<List<TimelineItemUi>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.timeline("t").collect { items += it } }
        advanceUntilIdle()

        val blocks = items.last().single().blocks
        assertEquals(listOf("diff", "terminal"), blocks.map { it.type })
        assertEquals("one\ntwo", blocks[0].before)
        assertEquals("ok", blocks[1].text)
    }

    @Test fun `状态文案是人话`() {
        assertEquals("排队中", TaskStatus.QUEUED.label())
        assertEquals("运行中", TaskStatus.RUNNING.label())
        assertEquals("需确认", TaskStatus.NEEDS_APPROVAL.label())
        assertEquals("完成", TaskStatus.SUCCEEDED.label())
        assertEquals("失败", TaskStatus.FAILED.label())
        assertEquals("已取消", TaskStatus.CANCELLED.label())
    }
}
