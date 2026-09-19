package com.quine.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quine.core.design.theme.QuineTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置页的渲染冒烟。
 *
 * 重点是「本地工作间」这一节 —— 它是首启屏二「暂时跳过搭建」的回头路。
 * 跳过是单向门的话（跳过去再也装不回来）就是一个真缺陷，所以这里钉住它。
 *
 * 函数名一律不带空格：minSdk 29 → DEX 039 不允许 SimpleName 含空格。
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val noop: () -> Unit = {}

    private fun render(state: SettingsUiState, onRebuildSandbox: () -> Unit = {}) {
        compose.setContent {
            QuineTheme {
                SettingsScreen(
                    state = state,
                    onBack = noop,
                    onSelectPreset = {},
                    onBaseUrlChange = {},
                    onModelChange = {},
                    onApiKeyChange = {},
                    onTestConnection = noop,
                    onSave = noop,
                    onPickDirectory = noop,
                    onClearDirectory = noop,
                    onSelectIcon = {},
                    onSelectTrust = {},
                    onSelectReasoningEffort = {},
                    onRefreshModels = noop,
                    onConsumeMessage = noop,
                    onRebuildSandbox = onRebuildSandbox,
                )
            }
        }
    }

    /**
     * 设置页是整屏滚动的，「本地工作间」在首屏之外 ——
     * 不先 `performScrollTo()` 就会被判 "component is not displayed"。
     */
    private fun scrollTo(text: String) =
        compose.onNodeWithText(text).performScrollTo()

    @Test fun sandbox_section_shows_not_ready_state() {
        render(SettingsUiState(sandboxReady = false))

        scrollTo("本地工作间").assertIsDisplayed()
        scrollTo("还没搭好").assertIsDisplayed()
        scrollTo("现在搭建").assertIsDisplayed()
    }

    @Test fun sandbox_section_shows_ready_state() {
        render(SettingsUiState(sandboxReady = true))

        scrollTo("已搭好").assertIsDisplayed()
        scrollTo("重新搭建").assertIsDisplayed()
    }

    @Test fun rebuild_button_fires() {
        var clicked = false
        render(SettingsUiState(sandboxReady = false)) { clicked = true }

        scrollTo("现在搭建").performClick()
        assertTrue(clicked)
    }

    @Test fun reasoning_effort_selector_shows_all_levels() {
        render(SettingsUiState(reasoningEffort = com.quine.core.common.ReasoningEffort.OFF))

        scrollTo("思考等级").assertIsDisplayed()
        scrollTo("关闭").assertIsDisplayed()
        scrollTo("低").assertIsDisplayed()
        scrollTo("中").assertIsDisplayed()
        scrollTo("高").assertIsDisplayed()
    }

    @Test fun available_models_render_as_chips() {
        render(SettingsUiState(availableModels = listOf("alpha", "beta")))

        scrollTo("可用模型（2）").assertIsDisplayed()
        scrollTo("alpha").assertIsDisplayed()
        scrollTo("beta").assertIsDisplayed()
    }

    @Test fun models_error_shows_short_hint() {
        render(SettingsUiState(modelsError = "连接失败"))

        scrollTo("获取失败").assertIsDisplayed()
        scrollTo("连接失败").assertIsDisplayed()
    }

    @Test fun not_ready_explains_what_still_works() {
        // 看不出「不能跑命令但改文件照常」的话，用户会以为整个应用废了。
        render(SettingsUiState(sandboxReady = false))
        scrollTo("未初始化，命令执行不可用。")
            .assertIsDisplayed()
    }
}