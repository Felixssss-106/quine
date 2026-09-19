package com.quine.feature.onboarding

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.sandbox.SandboxState
import com.quine.core.sandbox.SandboxStage
import com.quine.core.sandbox.headline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SandboxSetupViewModelTest {

    private class FakeDeps(
        private var ready: Boolean = false,
        private val outcome: SandboxState = SandboxState.Ready(java.io.File("/tmp/rootfs")),
        private val stages: List<SandboxState> = emptyList(),
    ) : SandboxSetupDeps {
        var cleared = 0
        var installs = 0
        var completed = false

        override fun isSandboxReady(): Boolean = ready
        override fun clearSandbox() {
            cleared++
        }

        override suspend fun installSandbox(onState: suspend (SandboxState) -> Unit): SandboxState {
            installs++
            for (stage in stages) {
                onState(stage)
                delay(1)
            }
            onState(outcome)
            return outcome
        }

        override suspend fun completeOnboarding() {
            completed = true
        }
    }

    private val working = SandboxState.Working(SandboxStage.INIT_FILESYSTEM, 0.5f, listOf("[..] 展开"))
    private val failed = SandboxState.Failed(
        stage = SandboxStage.INIT_FILESYSTEM,
        error = QuineError(
            kind = ErrorKind.STORAGE,
            message = "存储空间不够。",
            impact = "工作区没搭起来。",
            nextStep = "清理出空间后点「重试」。",
        ),
        log = listOf("[!!] 存储空间不够"),
    )
    private val ready = SandboxState.Ready(java.io.File("/tmp/rootfs"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun 一上来就自己开始装() = runTest {
        val deps = FakeDeps(stages = listOf(working), outcome = ready)

        SandboxSetupViewModel(deps)
        advanceUntilIdle()

        assertEquals(1, deps.installs)
    }

    @Test
    fun 装好了会标记首启完成() = runTest {
        val deps = FakeDeps(outcome = ready)

        SandboxSetupViewModel(deps)
        advanceUntilIdle()

        assertTrue(deps.completed)
    }

    @Test
    fun 失败时日志自动展开() = runTest {
        val deps = FakeDeps(outcome = failed)

        val vm = SandboxSetupViewModel(deps)
        advanceUntilIdle()

        assertTrue(vm.logExpanded.value)
        assertEquals(failed, vm.state.value)
    }

    @Test
    fun 失败时不会标记首启完成() = runTest {
        val deps = FakeDeps(outcome = failed)

        SandboxSetupViewModel(deps)
        advanceUntilIdle()

        assertFalse(deps.completed)
    }

    @Test
    fun 重试会先清残留再从头装() = runTest {
        val deps = FakeDeps(outcome = failed)

        val vm = SandboxSetupViewModel(deps)
        advanceUntilIdle()
        vm.retry()
        advanceUntilIdle()

        assertEquals(1, deps.cleared)
        assertEquals(2, deps.installs)
    }

    @Test
    fun 清理空间只清残留不自动重跑() = runTest {
        val deps = FakeDeps(outcome = failed)

        val vm = SandboxSetupViewModel(deps)
        advanceUntilIdle()
        vm.clear()
        advanceUntilIdle()

        assertEquals(1, deps.cleared)
        assertEquals(1, deps.installs)
        assertEquals(SandboxState.Idle, vm.state.value)
    }

    @Test
    fun 已经装好了就不再装一遍() = runTest {
        val deps = FakeDeps(ready = true, outcome = ready)

        val vm = SandboxSetupViewModel(deps)
        advanceUntilIdle()

        assertTrue(vm.alreadyReady)
        assertEquals(0, deps.installs)
    }

    @Test
    fun 装完会闪一下黑白反转() = runTest {
        val deps = FakeDeps(outcome = ready)

        val vm = SandboxSetupViewModel(deps)
        advanceUntilIdle()

        assertTrue(vm.flash.value)
    }

    // ---- 状态 → UI 的映射 ----

    @Test
    fun 完成时四步都算走完() {
        assertEquals(SandboxStage.entries.toSet(), ready.completedStages())
    }

    @Test
    fun 失败的那一步不算走完() {
        val done = failed.completedStages()

        assertFalse(done.contains(SandboxStage.INIT_FILESYSTEM))
        assertTrue(done.contains(SandboxStage.CHECK_DEVICE))
        assertEquals(SandboxStage.INIT_FILESYSTEM, failed.currentStage())
    }

    @Test
    fun 失败文案是屏二要的那句() {
        assertTrue(failed.headline().startsWith("卡在第 ③ 步"))
        assertTrue(failed.headline().contains("存储空间不够"))
    }
}
