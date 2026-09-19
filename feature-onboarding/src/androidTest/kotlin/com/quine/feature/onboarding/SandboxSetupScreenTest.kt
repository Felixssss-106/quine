package com.quine.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.design.theme.QuineTheme
import com.quine.core.sandbox.SandboxStage
import com.quine.core.sandbox.SandboxState
import org.junit.Assert.assertTrue
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
                    onSkip = {},
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
    fun 日志默认折叠() {
        render(
            SandboxState.Working(SandboxStage.EXPAND_ROOTFS, 0.4f, listOf("[ok] 收到 48 MB")),
            expanded = false,
        )
        compose.onNodeWithText("展开日志（1 行）").assertIsDisplayed()
    }

    @Test
    fun 日志展开后显示收起标签() {
        // 一个测试里只能 setContent 一次 —— 原来写成同方法两次 render 是 pre-existing bug，
        // 只是那时 runner 配错 → 这个测试方法一次也没真正跑过，现在才发现。
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

    @Test
    fun 失败态有跳过出口() {
        // 屏二的失败态：rootfs 来源没定时重试必然再失败，必须给一个出口，
        // 否则真机冷启动就困在这里进不了聊天（亲历过一次）。
        var skipped = false
        compose.setContent {
            QuineTheme {
                SandboxSetupScreen(
                    state = failed,
                    logExpanded = false,
                    flash = false,
                    onToggleLog = {},
                    onRetry = {},
                    onClear = {},
                    onSkip = { skipped = true },
                )
            }
        }

        compose.onNodeWithText("卡在第 ③ 步：存储空间不够。").assertIsDisplayed()
        compose.onNodeWithText("暂时跳过搭建（不能用跑命令）").assertIsDisplayed()
        compose.onNodeWithText("暂时跳过搭建（不能用跑命令）").performClick()
        assertTrue(skipped)
    }

    @Test
    fun 未失败时不显示跳过按钮() {
        // 跳过只在失败态出现 —— 在跑的时候强行跳过等于中途打断，反而更糟。
        render(SandboxState.Working(SandboxStage.CHECK_DEVICE, 0f, emptyList()))
        compose.onNodeWithText("暂时跳过搭建（不能用跑命令）").assertDoesNotExist()
    }
}
