package com.quine.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.quine.core.common.IconAliasController
import com.quine.core.common.IconVariant

/**
 * 设置内应用图标切换（docs/page-specs.md §4、icons/pixel-final/README.md）。
 *
 * 两个 `activity-alias` 各自带一个 LAUNCHER 入口，靠 `setComponentEnabledSetting` 切换。
 *
 * **顺序是这个实现里唯一不能写错的地方**：先启用目标、再禁用旧的。
 * 反过来的话会出现一个「两个入口都关着」的瞬间 —— 桌面图标会彻底消失，
 * 只能重装应用才能恢复。
 *
 * `DONT_KILL_APP` 能避免进程被杀，但部分 OEM 启动器仍会结束当前任务回到桌面，
 * 这是系统行为，设计文档已明示，UI 侧用小字提前说明。
 */
class ActivityAliasIconController(context: Context) : IconAliasController {

    private val appContext = context.applicationContext

    override fun currentVariant(): IconVariant {
        val state = appContext.packageManager.getComponentEnabledSetting(
            ComponentName(appContext, DARK_ALIAS),
        )
        return if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            IconVariant.DARK
        } else {
            // 未显式设置过时返回 DEFAULT，与 manifest 里「浅色启用、深色禁用」的初值一致。
            IconVariant.LIGHT
        }
    }

    override fun apply(variant: IconVariant) {
        val dark = variant == IconVariant.DARK
        val target = ComponentName(appContext, if (dark) DARK_ALIAS else LIGHT_ALIAS)
        val obsolete = ComponentName(appContext, if (dark) LIGHT_ALIAS else DARK_ALIAS)
        val packageManager = appContext.packageManager

        packageManager.setComponentEnabledSetting(
            target,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        packageManager.setComponentEnabledSetting(
            obsolete,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {
        const val LIGHT_ALIAS = "com.quine.app.LauncherLight"
        const val DARK_ALIAS = "com.quine.app.LauncherDark"
    }
}
