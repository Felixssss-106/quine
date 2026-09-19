package com.quine.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quine.core.common.TrustLevel
import com.quine.core.design.theme.QuineTheme
import com.quine.core.gateway.ChatRole
import com.quine.core.storage.ChatMessage
import com.quine.core.storage.MessageMeta
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 聊天面的渲染冒烟：只喂状态、不接依赖，验证关键元素真的画出来了。
 * 行为（发送 → 流式 → 工具回显）由 `ChatViewModelTest` 在 JVM 上覆盖。
 */
@RunWith(AndroidJUnit4::class)
class ChatScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val noop: () -> Unit = {}

    private fun render(state: ChatUiState) {
        compose.setContent {
            QuineTheme {
                ChatScreen(
                    state = state,
                    onDraftChange = {},
                    onSend = noop,
                    onStop = noop,
                    onRetry = noop,
                    onEdit = noop,
                    onToggleActivity = noop,
                    onDismissError = noop,
                    onTrustLevel = {},
                    onOpenSettings = noop,
                    onOpenTasks = noop,
                )
            }
        }
    }

    @Test
    fun 渲染用户气泡_活动卡摘要与composer占位() {
        render(
            ChatUiState(
                loaded = true,
                messages = listOf(
                    message(id = "m1", role = ChatRole.USER, text = "看看 a.txt"),
                    message(
                        id = "m2",
                        role = ChatRole.TOOL,
                        text = "hello",
                        toolName = "fs_read",
                        meta = MessageMeta(ok = true, summary = "读了 a.txt", arguments = "{\"path\":\"a.txt\"}"),
                    ),
                ),
                draft = "",
            ),
        )

        compose.onNodeWithText("看看 a.txt").assertIsDisplayed()
        compose.onNodeWithText("读了 a.txt").assertIsDisplayed()
        compose.onNodeWithText("让它干点什么…").assertIsDisplayed()
        compose.onNodeWithText("标准").assertIsDisplayed()
    }

    @Test
    fun 渲染流式文本与呼吸光标() {
        render(
            ChatUiState(
                loaded = true,
                messages = emptyList(),
                live = LiveTurn(text = "正在读文件"),
                streaming = true,
                draft = "",
            ),
        )

        // assistant 文本走的是 Markdown 组件：它要先把文本解析成节点才画得出来，
        // 首帧未必就绪（另外两个用例断言的都是普通 Text，没有这层）。
        // 这里等它出现再断言，而不是赌时序。
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText("正在读文件", substring = true).assertExists()
            }.isSuccess
        }
        compose.onNodeWithText("正在读文件", substring = true).assertIsDisplayed()
    }

    @Test
    fun 错误条必须带上下一步动作() {
        render(
            ChatUiState(
                loaded = true,
                messages = emptyList(),
                draft = "",
                error = com.quine.core.common.QuineError(
                    kind = com.quine.core.common.ErrorKind.NETWORK,
                    message = "网络没连上模型服务。",
                    impact = "这条消息没有发出去。",
                    nextStep = "检查网络或代理，然后点重试。",
                ),
            ),
        )

        compose.onNodeWithText("网络没连上模型服务。").assertIsDisplayed()
        compose.onNodeWithText("这条消息没有发出去。").assertIsDisplayed()
        compose.onNodeWithText("检查网络或代理，然后点重试。").assertIsDisplayed()
        compose.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun 档位胶囊显示当前档位() {
        render(
            ChatUiState(loaded = true, messages = emptyList(), draft = "", trustLevel = TrustLevel.RUNAWAY),
        )

        compose.onNodeWithText("狂奔").assertIsDisplayed()
        compose.onNodeWithText("保守").assertIsDisplayed()
    }

    private fun message(
        id: String,
        role: ChatRole,
        text: String,
        toolName: String? = null,
        meta: MessageMeta = MessageMeta(),
    ) = ChatMessage(
        id = id,
        conversationId = "c1",
        role = role,
        text = text,
        toolName = toolName,
        meta = meta,
        createdAt = 0,
    )
}
