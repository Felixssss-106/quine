package com.quine.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quine.core.gateway.LlmException
import com.quine.core.gateway.ProviderPreset
import com.quine.core.gateway.ProviderPresets
import com.quine.core.gateway.toConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val presets: List<ProviderPreset> = OnboardingViewModel.SELECTABLE_PRESETS,
    val selectedPresetId: String = ProviderPresets.all.first().id,
    val apiKey: String = "",
    val checking: Boolean = false,
    val keyValid: Boolean = false,
    /** 输入框下方的红字（docs/page-specs.md §1 原文）。 */
    val keyError: String? = null,
    /** 具体原因与下一步，作为次级说明。 */
    val keyDetail: String? = null,
    val shakeSignal: Int = 0,
    val showFallbackNotice: Boolean = false,
    val showKeyHelp: Boolean = false,
    val workspaceLabel: String = "",
    val finished: Boolean = false,
)

/**
 * 首启屏一的状态机（docs/page-specs.md §1）。
 *
 * 顺序刻意如此：Key 校验通过**当场落库**（Keystore + DataStore），
 * 这样即使用户在随后的目录选择里杀掉应用，Key 也不会白填。
 */
class OnboardingViewModel(private val deps: OnboardingDeps) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(workspaceLabel = deps.workspaceLabel()) }
        }
    }

    fun selectPreset(id: String) {
        if (_state.value.selectedPresetId == id) return
        _state.update { it.copy(selectedPresetId = id, keyValid = false, keyError = null, keyDetail = null) }
        scheduleCheck()
    }

    fun onApiKeyChange(value: String) {
        // Key 里绝不会有空格；顺手去掉粘贴带来的换行。
        val cleaned = value.filterNot { it.isWhitespace() }
        _state.update { it.copy(apiKey = cleaned, keyValid = false, keyError = null, keyDetail = null) }
        scheduleCheck()
    }

    fun toggleKeyHelp() = _state.update { it.copy(showKeyHelp = !it.showKeyHelp) }

    /** 「继续」：Key 已校验通过，交由 UI 去弹目录选择器。 */
    fun canContinue(): Boolean = _state.value.keyValid

    fun onSafPicked(uri: String?) {
        viewModelScope.launch {
            if (uri == null) {
                // 用户拒绝授权：不报错、不静默 —— 明确说明会回落私有工作区。
                _state.update { it.copy(showFallbackNotice = true) }
                return@launch
            }
            deps.saveSafTreeUri(uri)
            complete()
        }
    }

    /** 看完「会回落私有工作区」的说明后继续。 */
    fun dismissFallbackNotice() {
        viewModelScope.launch {
            deps.saveSafTreeUri(null)
            complete()
        }
    }

    /** 「先随便逛逛（功能受限）」：不接模型也能进去看看。 */
    fun skipWithLimitedFeatures() {
        viewModelScope.launch { complete() }
    }

    private suspend fun complete() {
        deps.completeOnboarding()
        _state.update { it.copy(finished = true) }
    }

    private fun scheduleCheck() {
        checkJob?.cancel()
        if (_state.value.apiKey.length < MIN_KEY_LENGTH) return
        checkJob = viewModelScope.launch {
            delay(CHECK_DEBOUNCE_MILLIS)
            check()
        }
    }

    private suspend fun check() {
        val current = _state.value
        if (current.apiKey.length < MIN_KEY_LENGTH) return

        _state.update { it.copy(checking = true, keyError = null, keyDetail = null) }
        val config = presetOf(current.selectedPresetId).toConfig()
        val result = deps.checkKey(config, current.apiKey)

        result.fold(
            onSuccess = {
                // 校验通过就落库，避免用户在后面的目录选择里中断导致白填。
                deps.saveProvider(config, current.apiKey)
                _state.update { it.copy(checking = false, keyValid = true, keyError = null, keyDetail = null) }
            },
            onFailure = { error ->
                val quine = (error as? LlmException)?.error
                _state.update {
                    it.copy(
                        checking = false,
                        keyValid = false,
                        keyError = KEY_ERROR_TEXT,
                        keyDetail = quine?.let { detail -> detail.message + detail.nextStep }
                            ?: error.message,
                        shakeSignal = it.shakeSignal + 1,
                    )
                }
            },
        )
    }

    private fun presetOf(id: String): ProviderPreset = ProviderPresets.byId(id)

    companion object {
        /** 首启只列 OpenAI 兼容且无需额外填 baseUrl 的预设；「自定义」在设置页里做。 */
        val SELECTABLE_PRESETS: List<ProviderPreset> =
            ProviderPresets.all.filter { it.baseUrl.isNotBlank() }

        const val KEY_ERROR_TEXT = "Key 好像不对，再检查一下？"

        private const val MIN_KEY_LENGTH = 8
        private const val CHECK_DEBOUNCE_MILLIS = 400L

        fun factory(deps: OnboardingDeps): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(deps) }
        }
    }
}
