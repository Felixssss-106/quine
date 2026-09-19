package com.quine.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quine.core.design.theme.QuineTheme
import com.quine.core.storage.QuineSettings
import com.quine.feature.chat.ChatRoute
import com.quine.feature.onboarding.OnboardingRoute
import com.quine.feature.onboarding.SandboxSetupRoute
import com.quine.feature.settings.SettingsRoute

private object Routes {
    const val ONBOARDING = "onboarding"
    const val SANDBOX_SETUP = "sandbox_setup"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

/**
 * 应用根组合：主题宿主 + 导航图。
 *
 * DataStore 首次读取完成前不渲染任何东西 —— 让窗口底色（Theme.Quine 的
 * `windowBackground`，深浅各一套）先顶上，避免白闪；同时也保证导航起点
 * 一定是根据真实设置算出来的。
 */
@Composable
fun QuineApp(container: AppContainer) {
    var settings by remember { mutableStateOf<QuineSettings?>(null) }
    LaunchedEffect(Unit) {
        container.settingsStore.settings.collect { settings = it }
    }

    val current = settings ?: return

    QuineTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = if (current.onboarded) Routes.CHAT else Routes.ONBOARDING,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(
                    deps = container.onboardingDeps,
                    // 屏一接完模型 → 屏二搭沙箱（page-specs §1 的两屏顺序）
                    onDone = { navController.navigate(Routes.SANDBOX_SETUP) },
                )
            }

            composable(Routes.SANDBOX_SETUP) {
                SandboxSetupRoute(
                    deps = container.sandboxSetupDeps,
                    onDone = {
                        navController.navigate(Routes.CHAT) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.CHAT) {
                ChatRoute(
                    deps = container.chatDeps,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsRoute(
                    deps = container.settingsDeps,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
