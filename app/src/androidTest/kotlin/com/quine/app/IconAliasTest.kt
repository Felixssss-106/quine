package com.quine.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quine.core.common.IconVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 图标 alias 切换的硬约束：**任何时刻都必须恰好有一个桌面入口**。
 * 两个都关掉 = 图标消失，只能重装；两个都开 = 桌面出现两个 Quine。
 */
@RunWith(AndroidJUnit4::class)
class IconAliasTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun 主Activity不带启动入口_只有alias提供() {
        val targets = launcherActivities()
        assertEquals("桌面入口必须恰好一个", 1, targets.size)
        val name = targets.first().activityInfo.name
        assertTrue(
            "入口必须是 alias，而不是 MainActivity（否则会出现双图标）",
            name == ActivityAliasIconController.LIGHT_ALIAS ||
                name == ActivityAliasIconController.DARK_ALIAS,
        )
    }

    @Test
    fun 切换后目标启用旧的禁用且入口始终唯一() {
        val controller = ActivityAliasIconController(context)
        val packageManager = context.packageManager

        try {
            controller.apply(IconVariant.DARK)

            assertEquals(IconVariant.DARK, controller.currentVariant())
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                packageManager.getComponentEnabledSetting(
                    ComponentName(context, ActivityAliasIconController.DARK_ALIAS),
                ),
            )
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                packageManager.getComponentEnabledSetting(
                    ComponentName(context, ActivityAliasIconController.LIGHT_ALIAS),
                ),
            )
            assertEquals(1, launcherActivities().size)

            controller.apply(IconVariant.LIGHT)

            assertEquals(IconVariant.LIGHT, controller.currentVariant())
            assertEquals(1, launcherActivities().size)
        } finally {
            // 别把设备留在测试状态
            controller.apply(IconVariant.LIGHT)
        }
    }

    @Test
    fun 重复切换同一个变体是幂等的() {
        val controller = ActivityAliasIconController(context)
        try {
            controller.apply(IconVariant.DARK)
            controller.apply(IconVariant.DARK)
            assertEquals(1, launcherActivities().size)
            assertEquals(IconVariant.DARK, controller.currentVariant())
        } finally {
            controller.apply(IconVariant.LIGHT)
        }
    }

    @Suppress("DEPRECATION")
    private fun launcherActivities() = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName),
        0,
    )
}
