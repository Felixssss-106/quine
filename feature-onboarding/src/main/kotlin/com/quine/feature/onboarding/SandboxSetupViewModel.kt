package com.quine.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quine.core.sandbox.SandboxStage
import com.quine.core.sandbox.SandboxState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 屏二的驱动。
 *
 * 「切后台状态保持」（page-specs §1）靠两层：
 * - 配置变更（旋转 / 深色切换）：状态在 ViewModel 里，天然活着；
 * - 进程被杀：重建时 [SandboxSetupDeps.isSandboxReady] 会告诉我们要不要重来。
 *
 * 完成时的「整屏黑白反转 120ms」也由这里控制（[flash]）—— 时长是设计定的，
 * 不该散落在 UI 里各写一份。
 */
class SandboxSetupViewModel(private val deps: SandboxSetupDeps) : ViewModel() {

    private val _state = MutableStateFlow<SandboxState>(SandboxState.Idle)
    val state: StateFlow<SandboxState> = _state.asStateFlow()

    /** 日志区是否展开。失败时会被强制展开（文档要求：失败时日志区自动展开）。 */
    private val _logExpanded = MutableStateFlow(false)
    val logExpanded: StateFlow<Boolean> = _logExpanded.asStateFlow()

    /** 装完那一下的黑白反转。 */
    private val _flash = MutableStateFlow(false)
    val flash: StateFlow<Boolean> = _flash.asStateFlow()

    /** 已经装好了：UI 直接走「秒进」那条路。 */
    val alreadyReady: Boolean = deps.isSandboxReady()

    init {
        if (!alreadyReady) start()
    }

    fun start() {
        viewModelScope.launch {
            _flash.value = false
            val final = deps.installSandbox { _state.value = it }
            when (final) {
                is SandboxState.Failed -> _logExpanded.value = true
                is SandboxState.Ready -> {
                    _flash.value = true
                    delay(FLASH_MILLIS)
                    deps.completeOnboarding()
                }

                else -> Unit
            }
        }
    }

    /** 失败态的「重试」：先清残留，再从头来一遍。 */
    fun retry() {
        deps.clearSandbox()
        _state.value = SandboxState.Idle
        _logExpanded.value = false
        start()
    }

    /** 失败态的「清理空间」：只清残留，不自动重跑 —— 用户腾完空间自己点重试。 */
    fun clear() {
        deps.clearSandbox()
        _state.value = SandboxState.Idle
        _logExpanded.value = false
    }

    fun toggleLog() {
        _logExpanded.value = !_logExpanded.value
    }

    fun finish() {
        viewModelScope.launch { deps.completeOnboarding() }
    }

    companion object {
        /** motion §2.9：完成 → 整屏黑白反转 120ms → 回正 → 进入主界面（400ms）。 */
        const val FLASH_MILLIS = 120L

        fun factory(deps: SandboxSetupDeps): ViewModelProvider.Factory = viewModelFactory {
            initializer { SandboxSetupViewModel(deps) }
        }
    }
}

/** 当前停在哪一步；没开始或已就绪时返回 null。 */
fun SandboxState.currentStage(): SandboxStage? = when (this) {
    is SandboxState.Working -> stage
    is SandboxState.Failed -> stage
    else -> null
}

/** 哪些步骤已经走完（决定步骤点从灰变黑）。失败的那一步不算完成。 */
fun SandboxState.completedStages(): Set<SandboxStage> {
    if (this is SandboxState.Ready) return SandboxStage.entries.toSet()
    val current = currentStage() ?: return emptySet()
    val upTo = if (this is SandboxState.Failed) current.ordinal else current.ordinal + 1
    return SandboxStage.entries.take(upTo).toSet()
}
