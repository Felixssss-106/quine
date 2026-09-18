package com.quine.feature.chat

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.gateway.ChatRole
import com.quine.core.gateway.LlmEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(provider: FakeProvider): Pair<ChatViewModel, FakeChatDeps> {
        val deps = FakeChatDeps(provider, temp.root)
        return ChatViewModel(deps) to deps
    }

    @Test
    fun `发送后用户消息与回复都落库`() = runTest(dispatcher) {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("你好，我是 Quine。"), LlmEvent.Finish("stop"))),
        )
        val (viewModel, deps) = newViewModel(provider)
        advanceUntilIdle()

        viewModel.onDraftChange("在吗")
        viewModel.send()
        advanceUntilIdle()

        val user = deps.persisted.single { it.isUser }
        assertEquals("在吗", user.text)

        val assistant = deps.persisted.single { it.isAssistant }
        assertEquals("你好，我是 Quine。", assistant.text)

        assertFalse(viewModel.state.value.streaming)
        assertFalse(viewModel.state.value.canSend)
        assertEquals("", viewModel.state.value.draft)
    }

    @Test
    fun `工具调用产出活动卡摘要与工具消息`() = runTest(dispatcher) {
        File(temp.root, "a.txt").writeText("hello")

        val provider = FakeProvider()
            .enqueue(
                FakeProvider.Turn.Emit(
                    listOf(
                        LlmEvent.ToolCallDelta(0, "call_1", "fs_read", "{\"path\":\"a.txt\"}"),
                        LlmEvent.Finish("tool_calls"),
                    ),
                ),
            )
            .enqueue(
                FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("文件里是 hello"), LlmEvent.Finish("stop"))),
            )

        val (viewModel, deps) = newViewModel(provider)
        advanceUntilIdle()

        viewModel.onDraftChange("看看 a.txt")
        viewModel.send()
        advanceUntilIdle()

        val tool = deps.persisted.single { it.isTool }
        assertEquals("fs_read", tool.toolName)
        assertEquals("call_1", tool.toolCallId)
        assertEquals("读了 a.txt", tool.meta.summary)
        assertTrue(tool.meta.ok == true)
        assertTrue(tool.text.contains("hello"))

        // 顺序必须是 assistant(tool_calls) → tool → assistant
        val roles = deps.persisted.map { it.role }
        assertEquals(
            listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT),
            roles,
        )
        assertTrue(deps.persisted[1].toolCalls.any { it.id == "call_1" })
    }

    @Test
    fun `失败时错误上屏并把已吐出的部分落库`() = runTest(dispatcher) {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.EmitThenFail(listOf(LlmEvent.TextDelta("写到一半")), networkError()),
        )
        val (viewModel, deps) = newViewModel(provider)
        advanceUntilIdle()

        viewModel.onDraftChange("hi")
        viewModel.send()
        advanceUntilIdle()

        val error = viewModel.state.value.error
        assertNotNull(error)
        assertEquals(ErrorKind.NETWORK, error!!.kind)
        assertTrue("错误文案必须带下一步动作", error.nextStep.isNotEmpty())

        // 「已完成的部分还在」
        val assistant = deps.persisted.single { it.isAssistant }
        assertEquals("写到一半", assistant.text)
    }

    @Test
    fun `停止会把已吐出的部分落库并给出已停止状态`() = runTest(dispatcher) {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.Hang(listOf(LlmEvent.TextDelta("正在写"))),
        )
        val (viewModel, deps) = newViewModel(provider)
        advanceUntilIdle()

        viewModel.onDraftChange("写点东西")
        viewModel.send()

        // 不能用 advanceUntilIdle：这一轮永远挂起，而流式 ticker 每 80ms 重排一次，
        // 虚拟时间会被无限推进，测试直接卡死。这里只推进有限的一小段时间。
        advanceTimeBy(300)
        runCurrent()

        assertTrue("取消前应当处于流式中", viewModel.state.value.streaming)

        viewModel.stop()
        // 取消之后 ticker 会被停掉，此时 advanceUntilIdle 是安全的。
        advanceUntilIdle()

        assertTrue(viewModel.state.value.stopped)
        assertFalse(viewModel.state.value.streaming)
        assertEquals("正在写", deps.persisted.single { it.isAssistant }.text)
    }

    @Test
    fun `重试不会重复追加用户消息`() = runTest(dispatcher) {
        val provider = FakeProvider()
            .enqueue(FakeProvider.Turn.Fail(networkError()))
            .enqueue(FakeProvider.Turn.Fail(networkError()))
            .enqueue(FakeProvider.Turn.Fail(networkError()))
            .enqueue(
                FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("这次好了"), LlmEvent.Finish("stop"))),
            )

        val (viewModel, deps) = newViewModel(provider)
        advanceUntilIdle()

        viewModel.onDraftChange("hi")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(1, deps.persisted.count { it.isUser })
        assertNotNull(viewModel.state.value.error)

        viewModel.retry()
        advanceUntilIdle()

        assertEquals("重试不该再写一条用户消息", 1, deps.persisted.count { it.isUser })
        assertEquals("这次好了", deps.persisted.single { it.isAssistant }.text)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `编辑把最后一条用户消息退回输入框并清掉后续`() = runTest(dispatcher) {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("答"), LlmEvent.Finish("stop"))),
        )
        val (viewModel, deps) = newViewModel(provider)
        advanceUntilIdle()

        viewModel.onDraftChange("原问题")
        viewModel.send()
        advanceUntilIdle()

        viewModel.editLast()
        advanceUntilIdle()

        assertEquals("原问题", viewModel.state.value.draft)
        assertTrue(deps.persisted.isEmpty())
    }

    @Test
    fun `草稿会落库`() = runTest(dispatcher) {
        val provider = FakeProvider()
        val (viewModel, deps) = newViewModel(provider)
        advanceUntilIdle()

        viewModel.onDraftChange("半句话")
        advanceUntilIdle()

        assertEquals("半句话", deps.savedDraft)
    }

    @Test
    fun `档位切换会落库`() = runTest(dispatcher) {
        val provider = FakeProvider()
        val (viewModel, deps) = newViewModel(provider)
        advanceUntilIdle()

        viewModel.setTrustLevel(com.quine.core.common.TrustLevel.RUNAWAY)
        advanceUntilIdle()

        assertEquals(com.quine.core.common.TrustLevel.RUNAWAY, deps.trustLevel)
        assertEquals(com.quine.core.common.TrustLevel.RUNAWAY, viewModel.state.value.trustLevel)
    }

    private fun networkError() = QuineError(
        kind = ErrorKind.NETWORK,
        message = "网络没连上模型服务。",
        impact = "这条消息没有发出去。",
        nextStep = "检查网络或代理，然后点重试。",
    )
}
