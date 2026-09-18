package com.quine.core.design.component

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 触感映射（docs/motion-spec.md §1.3）—— 动画的「回声」，与动效同拍。
 *
 * 轻点 = light · 发消息 = light · 完成 = medium · 错误 = heavy×2 · 危险确认 = double tick
 */
class QuineHapticFeedback internal constructor(private val view: android.view.View) {

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }

    /** 轻点：按压、chip、开关、图标记。 */
    fun light() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    /** 完成：任务完成、Key 校验通过。 */
    fun medium() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perform(HapticFeedbackConstants.CONFIRM)
        } else {
            perform(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** 错误：短促双震。 */
    fun error() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perform(HapticFeedbackConstants.REJECT)
        } else {
            perform(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** 危险确认：double tick。 */
    fun dangerConfirm() {
        perform(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
fun rememberQuineHaptics(): QuineHapticFeedback {
    val view = LocalView.current
    return remember(view) { QuineHapticFeedback(view) }
}
