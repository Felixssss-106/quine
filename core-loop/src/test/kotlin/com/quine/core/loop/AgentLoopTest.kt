package com.quine.core.loop

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.gateway.ChatRole
import com.quine.core.gateway.LlmEvent
import com.quine.core.gateway.LlmMessage
import com.quine.core.gateway.RetryPolicy
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolRegistry
import com.quine.core.tools.builtin.FsReadTool
import com.quine.core.tools.workspace.PrivateWorkspace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val registry = ToolRegistry(listOf(FsReadTool()))

    private fun context() = ToolContext(workspace = PrivateWorkspace(temp.root))

    private fun loop(
        provider: FakeProvider,
        config: LoopConfig = LoopConfig(),
    ) = AgentLoop(provider = provider, tools = registry, config = config, retryPolicy = RetryPolicy())

    private fun states(events: List<LoopEvent>): List<LoopState> =
        events.filterIsInstance<LoopEvent.StateChanged>().map { it.state }

    // ---- 路径 1：正常 ----

    @Test
    fun `正常路径 直接出字并结束`() = runTest {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("你好"), LlmEvent.Finish("stop"))),
        )

        val events = loop(provider).run(emptyList(), "打个招呼", context()).toList()

        assertEquals(
            listOf(LoopState.Planning, LoopState.Streaming, LoopState.Done),
            states(events),
        )
        assertTrue(events.any { it is LoopEvent.TextDelta && it.text == "你好" })
        assertTrue(events.any { it is LoopEvent.AssistantTurn })
        assertFalse(events.any { it is LoopEvent.Failed })
        assertEquals(1, provider.requests.size)
    }

    // ---- 路径 2：工具 ----

    @Test
    fun `工具路径 调用 fs_read 后把结果回灌`() = runTest {
        File(temp.root, "a.txt").writeText("hello")

        val provider = FakeProvider()
            .enqueue(
                FakeProvider.Turn.Emit(
                    listOf(
                        LlmEvent.ToolCallDelta(
                            index = 0,
                            id = "call_1",
                            name = "fs_read",
                            argumentsDelta = "{\"path\":\"a.txt\"}",
                        ),
                        LlmEvent.Finish("tool_calls"),
                    ),
                ),
            )
            .enqueue(
                FakeProvider.Turn.Emit(
                    listOf(LlmEvent.TextDelta("文件里是 hello"), LlmEvent.Finish("stop")),
                ),
            )

        val events = loop(provider).run(emptyList(), "看看 a.txt", context()).toList()

        assertTrue(events.any { it is LoopEvent.StateChanged && it.state == LoopState.ToolCall })

        val started = events.filterIsInstance<LoopEvent.ToolStarted>()
        assertEquals(1, started.size)
        assertEquals("call_1", started.first().callId)
        assertEquals("fs_read", started.first().toolId)

        val finished = events.filterIsInstance<LoopEvent.ToolFinished>()
        assertEquals(1, finished.size)
        assertTrue(finished.first().ok)
        assertTrue(finished.first().output.contains("hello"))

        assertEquals(LoopState.Done, states(events).last())

        // 第二轮请求必须带上 assistant.tool_calls 与对应的 TOOL 结果
        assertEquals(2, provider.requests.size)
        val second = provider.requests[1].messages
        assertTrue(second.any { it.role == ChatRole.ASSISTANT && it.toolCalls.any { c -> c.id == "call_1" } })
        assertTrue(second.any { it.role == ChatRole.TOOL && it.toolCallId == "call_1" })
        assertTrue("工具声明要发给模型", provider.requests[1].tools.isNotEmpty())
    }

    @Test
    fun `工具失败也回灌 让模型自纠`() = runTest {
        val provider = FakeProvider()
            .enqueue(
                FakeProvider.Turn.Emit(
                    listOf(
                        LlmEvent.ToolCallDelta(0, "call_1", "fs_read", "{\"path\":\"nope.txt\"}"),
                        LlmEvent.Finish("tool_calls"),
                    ),
                ),
            )
            .enqueue(
                FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("没找到"), LlmEvent.Finish("stop"))),
            )

        val events = loop(provider).run(emptyList(), "读 nope.txt", context()).toList()

        val finished = events.filterIsInstance<LoopEvent.ToolFinished>().single()
        assertFalse(finished.ok)
        assertTrue(finished.output.isNotEmpty())

        val toolMessage = provider.requests[1].messages.single { it.role == ChatRole.TOOL }
        assertTrue("失败文本要回灌", toolMessage.content!!.isNotEmpty())
    }

    // ---- 路径 3：重试 ----

    @Test
    fun `重试路径 网络错误重试一次后成功`() = runTest {
        val provider = FakeProvider()
            .enqueue(FakeProvider.Turn.Fail(networkError()))
            .enqueue(
                FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("好了"), LlmEvent.Finish("stop"))),
            )

        val events = loop(provider).run(emptyList(), "hi", context()).toList()

        assertFalse("重试成功后不该报失败", events.any { it is LoopEvent.Failed })
        assertEquals(LoopState.Done, states(events).last())
        assertEquals(2, provider.requests.size)
    }

    @Test
    fun `重试预算用尽后升级给用户`() = runTest {
        val provider = FakeProvider()
            .enqueue(FakeProvider.Turn.Fail(networkError()))
            .enqueue(FakeProvider.Turn.Fail(networkError()))
            .enqueue(FakeProvider.Turn.Fail(networkError()))

        val events = loop(provider).run(emptyList(), "hi", context()).toList()

        assertEquals(1, events.filterIsInstance<LoopEvent.Failed>().size)
        // 1 次首发 + 2 次重试
        assertEquals(3, provider.requests.size)
        assertEquals(LoopState.Failed(events.filterIsInstance<LoopEvent.Failed>().first().error), states(events).last())
    }

    @Test
    fun `已经吐过字就不重放`() = runTest {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.EmitThenFail(listOf(LlmEvent.TextDelta("半截话")), networkError()),
        )

        val events = loop(provider).run(emptyList(), "hi", context()).toList()

        // 界面上已经出现「半截话」，重放会让内容重复 —— 宁可把错误升级给用户。
        assertEquals(1, provider.requests.size)
        assertEquals(1, events.filterIsInstance<LoopEvent.Failed>().size)
        assertTrue(events.any { it is LoopEvent.TextDelta && it.text == "半截话" })
    }

    // ---- 路径 4：取消 ----

    @Test
    fun `取消路径 中途停止仍给出已停止事件`() = runTest {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.Hang(listOf(LlmEvent.TextDelta("正在写"))),
        )
        val agentLoop = loop(provider)
        val events = mutableListOf<LoopEvent>()

        val collector = launch {
            agentLoop.run(emptyList(), "写点东西", context()).collect { events += it }
        }

        advanceUntilIdle()
        assertTrue("取消前应该已经收到增量", events.any { it is LoopEvent.TextDelta })

        agentLoop.cancel()
        advanceUntilIdle()
        collector.join()

        assertTrue(events.any { it is LoopEvent.Cancelled })
        assertEquals(LoopState.Cancelled, states(events).last())
    }

    // ---- 路径 5：失败 ----

    @Test
    fun `失败路径 鉴权错误不重试`() = runTest {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.Fail(
                QuineError(
                    kind = ErrorKind.AUTH,
                    message = "模型服务说这个 Key 不认。",
                    impact = "这条消息没有发出去。",
                    nextStep = "去 设置 → 模型 检查 Key。",
                ),
            ),
        )

        val events = loop(provider).run(emptyList(), "hi", context()).toList()

        val failed = events.filterIsInstance<LoopEvent.Failed>().single()
        assertEquals(ErrorKind.AUTH, failed.error.kind)
        assertTrue(failed.error.nextStep.isNotEmpty())
        assertEquals("鉴权错误不该重试", 1, provider.requests.size)
    }

    // ---- 上下文窗口 ----

    @Test
    fun `上下文窗口裁尾并丢弃孤儿 TOOL 消息`() = runTest {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("ok"), LlmEvent.Finish("stop"))),
        )
        val history = listOf(
            LlmMessage(ChatRole.USER, "1"),
            LlmMessage(ChatRole.ASSISTANT, "1"),
            LlmMessage(ChatRole.USER, "2"),
            LlmMessage(ChatRole.ASSISTANT, "2"),
            LlmMessage(ChatRole.TOOL, "结果一", toolCallId = "x", name = "fs_read"),
            LlmMessage(ChatRole.TOOL, "结果二", toolCallId = "y", name = "fs_read"),
        )

        loop(provider, LoopConfig(contextWindowMessages = 3))
            .run(history, "新问题", context())
            .toList()

        val messages = provider.requests.first().messages
        assertEquals(ChatRole.SYSTEM, messages.first().role)
        assertEquals("新问题", messages.last().content)
        assertTrue(
            "窗口首条不能是 TOOL，否则序列非法",
            messages.drop(1).first().role != ChatRole.TOOL,
        )
    }

    @Test
    fun `请求里始终带系统提示`() = runTest {
        val provider = FakeProvider().enqueue(
            FakeProvider.Turn.Emit(listOf(LlmEvent.TextDelta("ok"), LlmEvent.Finish("stop"))),
        )
        loop(provider).run(emptyList(), "hi", context()).toList()

        val system = provider.requests.first().messages.first()
        assertEquals(ChatRole.SYSTEM, system.role)
        assertTrue(system.content!!.isNotBlank())
    }

    private fun networkError() = QuineError(
        kind = ErrorKind.NETWORK,
        message = "网络没连上模型服务。",
        impact = "这条消息没有发出去。",
        nextStep = "检查网络或代理，然后点重试。",
    )
}
