package com.quine.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quine.core.common.IconVariant
import com.quine.core.common.ReasoningEffort
import com.quine.core.common.TrustLevel
import com.quine.core.gateway.LlmException
import com.quine.core.gateway.ProviderConfig
import com.quine.core.gateway.ProviderPreset
import com.quine.core.gateway.ProviderPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CheckResult {
    data object Ok : CheckResult

    data class Failed(val text: String) : CheckResult
}

data class SettingsUiState(
    val loaded: Boolean = false,
    val presets: List<ProviderPreset> = ProviderPresets.all,
    val presetId: String = ProviderPresets.all.first().id,
    val baseUrl: String = "",
    val model: String = "",
    /** 用户新粘贴的 Key；留空表示沿用已存的那把。 */
    val apiKeyInput: String = "",
    val maskedKey: String? = null,
    val checking: Boolean = false,
    val checkResult: CheckResult? = null,
    val saving: Boolean = false,
    val iconVariant: IconVariant = IconVariant.LIGHT,
    val trustLevel: TrustLevel = TrustLevel.STANDARD,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.OFF,
    /** 拉取到的模型 id 列表；null = 还没拉过。 */
    val availableModels: List<String>? = null,
    val loadingModels: Boolean = false,
    /** 模型列表拉取失败的提示（简短）；null = 没有失败。 */
    val modelsError: String? = null,
    val safTreeUri: String? = null,
    val workspaceLabel: String = "",
    val appVersion: String = "",
    /** Linux 沙箱是否就绪；没搭好 = 不能跑命令（文件工具不受影响）。 */
    val sandboxReady: Boolean = false,
    val message: String? = null,
) {
    val isCustom: Boolean get() = ProviderPresets.byId(presetId).isCustom

    /**
     * baseUrl 走明文 http。
     *
     * 明文是放行的（自建 / 局域网模型服务通常只有 http），但**必须让用户看见**：
     * 静默放行等于替他做了风险决定，静默拦截又会让这个能力不可用。
     */
    val isInsecureEndpoint: Boolean
        get() = baseUrl.startsWith("http://", ignoreCase = true)

    val canSave: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && (apiKeyInput.isNotBlank() || maskedKey != null)

    val keyHint: String
        get() = maskedKey?.let { "已保存：$it" } ?: "未保存"
}

class SettingsViewModel(private val deps: SettingsDeps) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = deps.settings.first()
            _state.update {
                it.copy(
                    loaded = true,
                    presetId = settings.provider.presetId,
                    baseUrl = settings.provider.baseUrl,
                    model = settings.provider.model,
                    maskedKey = deps.maskedApiKey(settings.provider.apiKeyRef),
                    iconVariant = settings.iconVariant,
                    trustLevel = settings.trustLevel,
                    reasoningEffort = settings.reasoningEffort,
                    safTreeUri = settings.safTreeUri,
                    workspaceLabel = deps.workspaceLabel(),
                    appVersion = deps.appVersion,
                    sandboxReady = deps.isSandboxReady(),
                )
            }
            // 模型名为空时自动取第一个可用模型：省掉一次「我该填什么」的困惑。
            if (settings.provider.model.isBlank()) autoPickModel()
        }
        viewModelScope.launch {
            deps.settings.collect { settings ->
                _state.update {
                    it.copy(
                        iconVariant = settings.iconVariant,
                        trustLevel = settings.trustLevel,
                        safTreeUri = settings.safTreeUri,
                    )
                }
            }
        }
    }

    fun selectPreset(id: String) {
        val preset = ProviderPresets.byId(id)
        _state.update {
            it.copy(
                presetId = preset.id,
                baseUrl = preset.baseUrl,
                model = preset.defaultModel,
                checkResult = null,
            )
        }
    }

    fun onBaseUrlChange(value: String) =
        _state.update { it.copy(baseUrl = value.trim(), checkResult = null) }

    fun onModelChange(value: String) =
        _state.update { it.copy(model = value.trim(), checkResult = null) }

    fun onApiKeyChange(value: String) =
        _state.update { it.copy(apiKeyInput = value.filterNot { c -> c.isWhitespace() }, checkResult = null) }

    fun testConnection() {
        val current = _state.value
        if (current.checking) return
        if (current.baseUrl.isBlank() || current.model.isBlank()) {
            _state.update {
                it.copy(checkResult = CheckResult.Failed("请填写接口地址与模型名。"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(checking = true, checkResult = null) }
            // 输入框为空 = 用已存的那把；Key 本身不经过 UI，由 app 层从 Keystore 取。
            val result = deps.checkConnection(
                provider = current.toProviderConfig(),
                apiKey = current.apiKeyInput.takeIf { it.isNotBlank() },
            )
            _state.update {
                it.copy(
                    checking = false,
                    checkResult = result.fold(
                        onSuccess = { CheckResult.Ok },
                        onFailure = { error -> CheckResult.Failed(describe(error)) },
                    ),
                )
            }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave || current.saving) return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            deps.saveProvider(
                provider = current.toProviderConfig(),
                apiKey = current.apiKeyInput.takeIf { it.isNotBlank() },
            )
            _state.update {
                it.copy(
                    saving = false,
                    apiKeyInput = "",
                    maskedKey = deps.maskedApiKey(ProviderConfig.DEFAULT_API_KEY_REF) ?: it.maskedKey,
                    message = "已保存",
                )
            }
        }
    }

    fun selectIcon(variant: IconVariant) {
        if (_state.value.iconVariant == variant) return
        viewModelScope.launch {
            deps.setIconVariant(variant)
            deps.applyIconVariant(variant)
            _state.update { it.copy(iconVariant = variant) }
        }
    }

    fun selectTrust(level: TrustLevel) {
        if (_state.value.trustLevel == level) return
        viewModelScope.launch {
            deps.setTrustLevel(level)
            _state.update { it.copy(trustLevel = level) }
        }
    }

    fun onSafPicked(uri: String?) {
        viewModelScope.launch {
            deps.saveSafTreeUri(uri)
            _state.update {
                it.copy(
                    safTreeUri = uri,
                    workspaceLabel = deps.workspaceLabel(),
                    message = if (uri == null) "已恢复默认工作区" else "工作目录已更新",
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * 拉取模型列表。失败时只留一句简短提示 ——
     * 列表拿不到不影响手动填写模型名，所以这里不阻塞、不弹窗。
     */
    fun refreshModels() {
        if (_state.value.loadingModels) return
        viewModelScope.launch {
            _state.update { it.copy(loadingModels = true, modelsError = null) }
            val result = deps.fetchModels()
            _state.update {
                it.copy(
                    loadingModels = false,
                    availableModels = result.getOrNull() ?: it.availableModels,
                    modelsError = result.exceptionOrNull()?.let(::describe),
                )
            }
        }
    }

    /** 列表里有就用列表里的第一个，省得用户猜模型名。 */
    fun autoPickModel() {
        viewModelScope.launch {
            if (_state.value.loadingModels) return@launch
            _state.update { it.copy(loadingModels = true, modelsError = null) }
            val result = deps.fetchModels()
            val picked = result.getOrNull()?.firstOrNull()
            _state.update {
                it.copy(
                    loadingModels = false,
                    availableModels = result.getOrNull() ?: it.availableModels,
                    modelsError = result.exceptionOrNull()?.let(::describe),
                    model = it.model.ifBlank { picked.orEmpty() },
                )
            }
            // 自动选中也算一次保存：否则下次进来又要重选。
            if (picked != null && _state.value.canSave) save()
        }
    }

    fun selectReasoningEffort(effort: ReasoningEffort) {
        if (_state.value.reasoningEffort == effort) return
        viewModelScope.launch {
            deps.setReasoningEffort(effort)
            _state.update { it.copy(reasoningEffort = effort) }
        }
    }

    /**
     * 重新读一次沙箱状态。
     *
     * 从屏二搭完沙箱返回设置页时，`init` 不会重跑 —— 不刷一次就会一直显示
     * 「还没搭好」，把刚装好的说成没装。
     */
    fun refreshSandbox() = _state.update { it.copy(sandboxReady = deps.isSandboxReady()) }

    private fun SettingsUiState.toProviderConfig(): ProviderConfig {
        val preset = ProviderPresets.byId(presetId)
        return ProviderConfig(
            presetId = preset.id,
            displayName = preset.displayName,
            baseUrl = baseUrl,
            model = model,
            apiKeyRef = ProviderConfig.DEFAULT_API_KEY_REF,
            isCustom = preset.isCustom,
        )
    }

    private fun describe(error: Throwable): String {
        val quine = (error as? LlmException)?.error ?: return error.message ?: "连接失败"
        return quine.message + quine.nextStep
    }

    companion object {
        fun factory(deps: SettingsDeps): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(deps) }
        }
    }
}
