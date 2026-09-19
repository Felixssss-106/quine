package com.quine.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quine.core.common.IconVariant
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
    val safTreeUri: String? = null,
    val workspaceLabel: String = "",
    val appVersion: String = "",
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
        get() = maskedKey?.let { "已存：$it（留空则继续用它）" } ?: "还没有存过 Key"
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
                    safTreeUri = settings.safTreeUri,
                    workspaceLabel = deps.workspaceLabel(),
                    appVersion = deps.appVersion,
                )
            }
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
                it.copy(checkResult = CheckResult.Failed("先把 baseUrl 和模型名填好，再测。"))
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
                    message = "已保存。",
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
                    message = if (uri == null) "已切回应用私有工作区。" else "工作目录已更新。",
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

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
        val quine = (error as? LlmException)?.error ?: return error.message ?: "连不上。"
        return quine.message + quine.nextStep
    }

    companion object {
        fun factory(deps: SettingsDeps): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(deps) }
        }
    }
}
