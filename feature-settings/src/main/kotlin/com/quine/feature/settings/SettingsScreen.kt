package com.quine.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quine.core.common.IconVariant
import com.quine.core.common.ReasoningEffort
import com.quine.core.common.TrustLevel
import com.quine.core.design.component.QuineChip
import com.quine.core.design.component.QuineDivider
import com.quine.core.design.component.QuinePrimaryButton
import com.quine.core.design.component.QuineSecondaryButton
import com.quine.core.design.component.QuineTextButton
import com.quine.core.design.component.QuineTextField
import com.quine.core.design.component.QuineTrustPill
import com.quine.core.design.theme.QuineTheme
import kotlinx.coroutines.delay

@Composable
fun SettingsRoute(
    deps: SettingsDeps,
    onBack: () -> Unit,
    /** 跳去首启屏二（重新搭建沙箱）。屏二搭完会自己回主界面。 */
    onRebuildSandbox: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(deps))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
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

    SettingsScreen(
        state = state,
        onBack = onBack,
        onSelectPreset = viewModel::selectPreset,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onModelChange = viewModel::onModelChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onTestConnection = viewModel::testConnection,
        onSave = viewModel::save,
        onPickDirectory = { directoryPicker.launch(null) },
        onClearDirectory = { viewModel.onSafPicked(null) },
        onSelectIcon = viewModel::selectIcon,
        onSelectTrust = viewModel::selectTrust,
        onSelectReasoningEffort = viewModel::selectReasoningEffort,
        onRefreshModels = viewModel::refreshModels,
        onConsumeMessage = viewModel::consumeMessage,
        onRebuildSandbox = onRebuildSandbox,
    )

    // 从屏二返回时刷新：沙箱可能刚搭好（或刚被清掉），init 不会重跑。
    LaunchedEffect(Unit) { viewModel.refreshSandbox() }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onPickDirectory: () -> Unit,
    onClearDirectory: () -> Unit,
    onSelectIcon: (IconVariant) -> Unit,
    onSelectTrust: (TrustLevel) -> Unit,
    onSelectReasoningEffort: (ReasoningEffort) -> Unit,
    onRefreshModels: () -> Unit,
    onConsumeMessage: () -> Unit,
    onRebuildSandbox: () -> Unit,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = dimens.pageMargin, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("返回", style = typography.footnote, color = colors.textSecondary)
            }
            Spacer(Modifier.width(4.dp))
            Text("设置", style = typography.headline, color = colors.textPrimary)
        }
        QuineDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.pageMargin),
        ) {
            state.message?.let { text ->
                Spacer(Modifier.height(dimens.sectionGap))
                NoticeCard(text = text)
                LaunchedEffect(text) {
                    delay(3000)
                    onConsumeMessage()
                }
            }

            // ---- 模型 ----
            SectionHeader("模型", "OpenAI 兼容接口")

            Spacer(Modifier.height(dimens.grid * 3))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.presets.forEach { preset ->
                    QuineChip(
                        text = preset.displayName,
                        selected = preset.id == state.presetId,
                        onClick = { onSelectPreset(preset.id) },
                    )
                }
            }

            Spacer(Modifier.height(dimens.sectionGap))

            if (state.isCustom) {
                QuineTextField(
                    value = state.baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = "baseUrl",
                    placeholder = "https://example.com/v1",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (state.isInsecureEndpoint) {
                    Spacer(Modifier.height(dimens.grid * 2))
                    InsecureEndpointNotice()
                }
                Spacer(Modifier.height(dimens.sectionGap))
            }

            QuineTextField(
                value = state.model,
                onValueChange = onModelChange,
                label = "模型名",
                placeholder = "例如 deepseek-chat",
                singleLine = true,
            )

            // 可用模型：拉到了就直接点选，不用手抄模型名。
            Spacer(Modifier.height(dimens.grid * 3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        state.loadingModels -> "获取中"
                        state.modelsError != null -> "获取失败"
                        state.availableModels.isNullOrEmpty() -> "可用模型"
                        else -> "可用模型（${state.availableModels.size}）"
                    },
                    style = typography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                QuineTextButton(text = "刷新", onClick = onRefreshModels)
            }

            state.modelsError?.let { error ->
                Spacer(Modifier.height(dimens.grid))
                Text(
                    text = error,
                    style = typography.caption,
                    color = colors.textTertiary,
                )
            }

            state.availableModels?.takeIf { it.isNotEmpty() }?.let { models ->
                Spacer(Modifier.height(dimens.grid * 2))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    models.forEach { id ->
                        QuineChip(
                            text = id,
                            selected = id == state.model,
                            onClick = { onModelChange(id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(dimens.sectionGap))

            QuineTextField(
                value = state.apiKeyInput,
                onValueChange = onApiKeyChange,
                label = "API Key",
                isError = false,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )

            Spacer(Modifier.height(dimens.grid * 2))
            Text(
                text = state.keyHint,
                style = typography.caption,
                color = colors.textTertiary,
            )

            Spacer(Modifier.height(dimens.sectionGap))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuineSecondaryButton(
                    text = if (state.checking) "测试中" else "测试连接",
                    onClick = onTestConnection,
                    enabled = !state.checking,
                    modifier = Modifier.weight(1f),
                )
                QuinePrimaryButton(
                    text = if (state.saving) "保存中" else "保存",
                    onClick = onSave,
                    enabled = state.canSave,
                    busy = state.saving,
                    modifier = Modifier.weight(1f),
                )
            }

            state.checkResult?.let { result ->
                Spacer(Modifier.height(dimens.sectionGap))
                when (result) {
                    CheckResult.Ok -> NoticeCard(text = "连接成功，Key 有效。")
                    is CheckResult.Failed -> NoticeCard(text = result.text, danger = true)
                }
            }

            SectionGap()

            // ---- 工作目录 ----
            SectionHeader(
                title = "工作目录",
                hint = "当前：${state.workspaceLabel.ifBlank { "私有工作区" }}",
            )
            Spacer(Modifier.height(dimens.grid * 3))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuineSecondaryButton(
                    text = if (state.safTreeUri == null) "选一个目录" else "换一个目录",
                    onClick = onPickDirectory,
                    modifier = Modifier.weight(1f),
                )
                if (state.safTreeUri != null) {
                    QuineSecondaryButton(
                        text = "恢复默认",
                        onClick = onClearDirectory,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionGap()

            // ---- 外观 · 应用图标（docs/page-specs.md §4）----
            SectionHeader(title = "外观", hint = "应用图标")

            Spacer(Modifier.height(dimens.grid * 3))

            IconOption(
                label = "浅色",
                description = "白底 >_",
                selected = state.iconVariant == IconVariant.LIGHT,
                onClick = { onSelectIcon(IconVariant.LIGHT) },
            )
            Spacer(Modifier.height(dimens.grid * 2))
            IconOption(
                label = "深色",
                description = "黑底 >_",
                selected = state.iconVariant == IconVariant.DARK,
                onClick = { onSelectIcon(IconVariant.DARK) },
            )

            SectionGap()

            // ---- 本地工作间（Linux 沙箱）----
            // 首启屏二允许「暂时跳过搭建」—— 这里是那扇门的回头路。
            SectionHeader(
                title = "本地工作间",
                hint = if (state.sandboxReady) "已搭好" else "还没搭好",
            )

            Spacer(Modifier.height(dimens.grid * 3))
            Text(
                text = if (state.sandboxReady) {
                    "已就绪，可执行命令。"
                } else {
                    "未初始化，命令执行不可用。"
                },
                style = typography.footnote,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(dimens.grid * 3))
            QuineSecondaryButton(
                text = if (state.sandboxReady) "重新搭建" else "现在搭建",
                onClick = onRebuildSandbox,
            )

            SectionGap()

            // ---- 思考等级 ----
            SectionHeader(title = "思考等级", hint = "部分模型支持")

            Spacer(Modifier.height(dimens.grid * 3))
            QuineTrustPill(
                labels = ReasoningEffort.entries.map { it.label },
                selectedIndex = state.reasoningEffort.ordinal,
                onSelect = { onSelectReasoningEffort(ReasoningEffort.entries[it]) },
            )

            SectionGap()

            // ---- 权限模式 ----
            SectionHeader(title = "权限模式", hint = "决定哪些操作需要你确认")

            Spacer(Modifier.height(dimens.grid * 3))
            QuineTrustPill(
                labels = TrustLevel.entries.map { it.label },
                selectedIndex = state.trustLevel.ordinal,
                onSelect = { onSelectTrust(TrustLevel.entries[it]) },
            )
            Spacer(Modifier.height(dimens.grid * 2))
            Text(
                text = "审批规则将在后续版本生效，当前仅记录选择。",
                style = typography.caption,
                color = colors.textTertiary,
            )

            SectionGap()

            // ---- 关于 ----
            SectionHeader(title = "关于", hint = null)

            Spacer(Modifier.height(dimens.grid * 3))
            AboutRow(label = "版本", value = state.appVersion)
            AboutRow(label = "许可证", value = "MIT")
            AboutRow(label = "等宽字体", value = "Maple Mono · OFL-1.1")
            AboutRow(label = "仓库", value = "github.com/Felixssss-106/quine")

            Spacer(Modifier.height(dimens.sectionGapLarge))
            Spacer(Modifier.height(dimens.sectionGapLarge))
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String?) {
    val colors = QuineTheme.colors
    Spacer(Modifier.height(QuineTheme.dimens.sectionGapLarge))
    Text(text = title, style = QuineTheme.typography.headline, color = colors.textPrimary)
    hint?.let {
        Spacer(Modifier.height(QuineTheme.dimens.grid))
        Text(text = it, style = QuineTheme.typography.caption, color = colors.textTertiary)
    }
}

@Composable
private fun SectionGap() {
    Spacer(Modifier.height(QuineTheme.dimens.sectionGapLarge))
}

/**
 * 明文地址的可见警告。
 *
 * 明文是放行的（见 `app/src/main/res/xml/network_security_config.xml` 的取舍说明），
 * 但风险必须让用户看见 —— 文案照旧走三段式：发生了什么 / 影响 / 下一步。
 */
@Composable
private fun InsecureEndpointNotice() {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusCard))
            .background(colors.fill)
            .padding(dimens.cardPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(colors.danger),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "这个地址没有加密。",
                style = typography.footnote,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Key 将以明文传输。公网地址请使用 https。",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun NoticeCard(text: String, danger: Boolean = false) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusCard))
            .background(colors.fill)
            .padding(dimens.cardPadding),
    ) {
        Text(
            text = text,
            style = QuineTheme.typography.footnote,
            color = if (danger) colors.danger else colors.textPrimary,
        )
    }
}

/** 单选行：选中 = ink 勾（docs/page-specs.md §4）。 */
@Composable
private fun IconOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusCard))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) colors.ink else colors.fill)
                .semantics {
                    contentDescription = if (selected) "$label（已选中）" else label
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) CheckGlyph(color = colors.onInk)
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(text = label, style = typography.body, color = colors.textPrimary)
            Text(text = description, style = typography.caption, color = colors.textTertiary)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val colors = QuineTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = QuineTheme.typography.footnote,
            color = colors.textSecondary,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = QuineTheme.typography.monoData,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun CheckGlyph(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val stroke = 1.6.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.1f, size.height * 0.55f),
            end = Offset(size.width * 0.4f, size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.4f, size.height * 0.85f),
            end = Offset(size.width * 0.92f, size.height * 0.15f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
