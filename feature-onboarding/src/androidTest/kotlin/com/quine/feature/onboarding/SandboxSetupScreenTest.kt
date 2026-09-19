package com.quine.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.design.theme.QuineTheme
import com.quine.core.sandbox.SandboxStage
import com.quine.core.sandbox.SandboxState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

/**
 * 屏二的渲染冒烟：只喂状态，不接依赖。
 *
 * 文案是 `page-specs.md` §1 写死的（四步名称、「卡在第 ③ 步」、底部小字），
 * 所以这里钉住它们 —— 改文案应当是有人主动改的，不是顺手改丢的。
 */
@RunWith(AndroidJUnit4::class)
class SandboxSetupScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val failed = SandboxState.Failed(
        stage = SandboxStage.INIT_FILESYSTEM,
        error = QuineError(
            kind = ErrorKind.STORAGE,
            message = "存储空间不够。",
            impact = "工作区没搭起来。",
            nextStep = "清理出空间后点「重试」。",
        ),
        log = listOf("[ok] 目录可写", "[!!] 存储空间不够"),
    )

    private fun render(
        state: SandboxState,
        expanded: Boolean = false,
        flash: Boolean = false,
    ) {
        compose.setContent {
            QuineTheme {
                SandboxSetupScreen(
                    state = state,
                    logExpanded = expanded,
                    flash = flash,
                    onToggleLog = {},
                    onRetry = {},
                    onClear = {},
                )
            }
        }
    }

    @Test
    fun 标题与四步都在() {
        render(SandboxState.Working(SandboxStage.CHECK_DEVICE, 0f, emptyList()))

        compose.onNodeWithText("搭建你的本地工作间。").assertIsDisplayed()
        compose.onNodeWithText("① 检查设备环境").assertIsDisplayed()
        compose.onNodeWithText("② 展开 Linux 工作区").assertIsDisplayed()
        compose.onNodeWithText("③ 初始化文件系统").assertIsDisplayed()
        compose.onNodeWithText("④ 工具链自检").assertIsDisplayed()
    }

    @Test
    fun 进行中显示底部小字() {
        render(SandboxState.Working(SandboxStage.CHECK_DEVICE, 0.2f, emptyList()))

        compose.onNodeWithText("首次约 1–3 分钟，之后秒开。占用约 200 MB。").assertIsDisplayed()
    }

    @Test
    fun 失败时说清卡在第几步并给出出口() {
        render(failed, expanded = true)

        compose.onNodeWithText("卡在第 ③ 步：存储空间不够。").assertIsDisplayed()
        compose.onNodeWithText("重试").assertIsDisplayed()
        compose.onNodeWithText("清理空间").assertIsDisplayed()
    }

    @Test
    fun 失败时不再显示底部小字() {
        render(failed, expanded = true)

        compose.onNodeWithText("首次约 1–3 分钟，之后秒开。占用约 200 MB。").assertDoesNotExist()
    }

    @Test
    fun 日志默认折叠_展开后能看到内容() {
        render(
            SandboxState.Working(SandboxStage.EXPAND_ROOTFS, 0.4f, listOf("[ok] 收到 48 MB")),
            expanded = false,
        )

        compose.onNodeWithText("展开日志（1 行）").assertIsDisplayed()

        render(
            SandboxState.Working(SandboxStage.EXPAND_ROOTFS, 0.4f, listOf("[ok] 收到 48 MB")),
            expanded = true,
        )
        compose.onNodeWithText("收起日志").assertIsDisplayed()
    }

    @Test
    fun 就绪时进度是满的且不显示失败文案() {
        render(SandboxState.Ready(File("/tmp/rootfs"), listOf("[ok] 工具链自检通过")))

        compose.onNodeWithText("④ 工具链自检").assertIsDisplayed()
        compose.onNodeWithText("卡在第 ③ 步：存储空间不够。").assertDoesNotExist()
    }
}
