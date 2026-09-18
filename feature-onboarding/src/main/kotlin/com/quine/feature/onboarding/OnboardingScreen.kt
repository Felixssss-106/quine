package com.quine.feature.onboarding

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quine.core.design.component.QuineChip
import com.quine.core.design.component.QuinePrimaryButton
import com.quine.core.design.component.QuineSecondaryButton
import com.quine.core.design.component.QuineTextButton
import com.quine.core.design.component.QuineTextField
import com.quine.core.design.theme.QuineTheme

/**
 * 首启屏一的入口：负责 SAF 目录选择器与导航回调，UI 在 [OnboardingScreen]。
 */
@Composable
fun OnboardingRoute(
    deps: OnboardingDeps,
    onDone: () -> Unit,
) {
    val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(deps))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // 持久化授权：先试读写，只读 provider 会抛 SecurityException，退到只读。
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.recoverCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.onSafPicked(uri?.toString())
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    OnboardingScreen(
        state = state,
        onSelectPreset = viewModel::selectPreset,
        onApiKeyChange = viewModel::onApiKeyChange,
        onToggleKeyHelp = viewModel::toggleKeyHelp,
        onContinue = { directoryPicker.launch(null) },
        onDismissFallback = viewModel::dismissFallbackNotice,
        onSkip = viewModel::skipWithLimitedFeatures,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onSelectPreset: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyHelp: () -> Unit,
    onContinue: () -> Unit,
    onDismissFallback: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = QuineTheme.colors
    val typography = QuineTheme.typography
    val dimens = QuineTheme.dimens
    val reduced = QuineTheme.reducedMotion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.pageMargin),
    ) {
        Spacer(Modifier.height(dimens.sectionGapLarge + dimens.sectionGap))

        StaggeredEntrance(index = 0, reducedMotion = reduced) {
            Text(text = "你好。", style = typography.display, color = colors.textPrimary)
        }

        Spacer(Modifier.height(dimens.sectionGap))

        StaggeredEntrance(index = 1, reducedMotion = reduced) {
            Text(
                text = "我住在你的手机里：改文件、搜资料、跑命令、做自动化。文件不出这台设备。",
                style = typography.body,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(dimens.sectionGapLarge + dimens.sectionGap))

        StaggeredEntrance(index = 2, reducedMotion = reduced) {
            Column {
                Text(text = "先接上你的模型", style = typography.headline, color = colors.textPrimary)
                Spacer(Modifier.height(dimens.grid))
                Text(
                    text = "OpenAI 兼容接口，Key 只存本机",
                    style = typography.caption,
                    color = colors.textTertiary,
                )
            }
        }

        Spacer(Modifier.height(dimens.sectionGap))

        StaggeredEntrance(index = 3, reducedMotion = reduced) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.presets.forEach { preset ->
                    QuineChip(
                        text = preset.displayName,
                        selected = preset.id == state.selectedPresetId,
                        onClick = { onSelectPreset(preset.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(dimens.sectionGap))

        StaggeredEntrance(index = 4, reducedMotion = reduced) {
            QuineTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                label = "粘贴 API Key",
                isError = state.keyError != null,
                errorText = state.keyError,
                shakeSignal = state.shakeSignal,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailing = {
                    when {
                        state.checking -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.textSecondary,
                        )

                        state.keyValid -> Text(
                            text = "✓",
                            style = typography.footnote,
                            color = colors.textPrimary,
                            modifier = Modifier.semantics { contentDescription = "Key 可用" },
                        )

                        else -> Unit
                    }
                },
            )
        }

        if (state.keyDetail != null) {
            Spacer(Modifier.height(dimens.grid))
            Text(
                text = state.keyDetail,
                style = typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = dimens.grid),
            )
        }

        Spacer(Modifier.height(dimens.sectionGap))

        StaggeredEntrance(index = 5, reducedMotion = reduced) {
            Column {
                QuineTextButton(text = "怎么拿 Key？", onClick = onToggleKeyHelp)
                Spacer(Modifier.height(dimens.grid))
                Text(
                    text = "Key 只存本机（加密），不经过我们的服务器。",
                    style = typography.caption,
                    color = colors.textTertiary,
                )
            }
        }

        Spacer(Modifier.height(dimens.sectionGapLarge + dimens.sectionGap))

        QuinePrimaryButton(
            text = "继续",
            onClick = onContinue,
            enabled = state.keyValid,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(dimens.grid))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QuineTextButton(text = "先随便逛逛（功能受限）", onClick = onSkip)
        }

        Spacer(Modifier.height(dimens.sectionGapLarge))
    }

    if (state.showKeyHelp) {
        KeyHelpDialog(onDismiss = onToggleKeyHelp)
    }

    if (state.showFallbackNotice) {
        // 「不静默失败」：拒绝授权也要说清楚发生了什么、影响是什么、下一步怎么办。
        AlertDialog(
            onDismissRequest = onDismissFallback,
            title = { Text(text = "没选目录也可以", style = QuineTheme.typography.headline) },
            text = {
                Text(
                    text = "我会先用应用私有工作区（${state.workspaceLabel}），" +
                        "文件照样不出这台设备。想换成你自己的目录，随时去 设置 → 工作目录。",
                    style = QuineTheme.typography.body,
                    color = QuineTheme.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissFallback) { Text("知道了，开始") }
            },
        )
    }
}

@Composable
private fun KeyHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "怎么拿 Key？", style = QuineTheme.typography.headline) },
        text = {
            Text(
                text = "去你选的供应商后台，注册后创建一个 API Key，复制粘贴到这里。\n\n" +
                    "· DeepSeek：platform.deepseek.com\n" +
                    "· Kimi：platform.moonshot.cn\n" +
                    "· 智谱 GLM：open.bigmodel.cn\n" +
                    "· 通义千问：bailian.console.aliyun.com",
                style = QuineTheme.typography.footnote,
                color = QuineTheme.colors.textSecondary,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好") } },
    )
}

/**
 * 首启仪式（docs/motion-spec.md §2.9）：标题逐行上浮（+8px 淡入，320ms，stagger 60ms）。
 * reduced-motion 下退化为 120ms 交叉淡入。
 */
@Composable
private fun StaggeredEntrance(
    index: Int,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(if (reducedMotion) 0L else index * 60L)
        started = true
    }

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 120 else 320),
        label = "onboardingEntrance",
    )

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 8.dp.toPx()
        },
    ) {
        content()
    }
}
