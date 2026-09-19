package com.quine.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quine.core.design.theme.QuineTheme
import kotlinx.coroutines.launch
import com.quine.core.storage.QuineSettings
import com.quine.feature.chat.ChatRoute
import com.quine.feature.onboarding.OnboardingRoute
import com.quine.feature.onboarding.SandboxSetupRoute
import com.quine.feature.settings.SettingsRoute
import com.quine.feature.tasks.TaskDetailRoute
import com.quine.feature.tasks.TasksRoute
import com.quine.feature.tasks.label

private object Routes {
    const val ONBOARDING = "onboarding"
    const val SANDBOX_SETUP = "sandbox_setup"
    const val CHAT = "chat"
    const val TASKS = "tasks"
    const val TASK_DETAIL = "task/{taskId}"
    const val SETTINGS = "settings"

    fun taskDetail(taskId: String) = "task/$taskId"
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

        // 导航动画的时长与位移在组合期算好传进去：
        // NavHost 的 transition lambda 不是 @Composable，在里面读不了 CompositionLocal。
        val navDuration = QuineTheme.motion.duration(QuineTheme.motion.standard, QuineTheme.reducedMotion)
        val navOffset = if (QuineTheme.reducedMotion) 0 else 24

        // 横屏才挂侧边栏：竖屏屏幕窄，挤进去反而两栏都不够用。
        val landscape = isLandscape()
        val scope = rememberCoroutineScope()
        var sidebarExpanded by remember { mutableStateOf(true) }
        var sidebarTab by remember { mutableStateOf(SidebarTab.CONVERSATIONS) }

        // 侧边栏只在「已经进到主界面」之后才出现 —— 首启流程不该被它分心。
        val showSidebar = landscape && current.onboarded

        Row(modifier = Modifier.fillMaxSize()) {
            if (showSidebar) {
                SidebarPane(
                    container = container,
                    expanded = sidebarExpanded,
                    tab = sidebarTab,
                    selectedConversationId = current.activeConversationId,
                    onToggleExpanded = { sidebarExpanded = !sidebarExpanded },
                    onSelectTab = { sidebarTab = it },
                    onSelectConversation = { id ->
                        // 必须先落库再重建聊天页：ChatViewModel 在 init 里读
                        // activeConversationId，写没落地就跳过去会切回原来那个会话。
                        scope.launch {
                            container.settingsStore.setActiveConversationId(id)
                            navController.navigate(Routes.CHAT) {
                                popUpTo(Routes.CHAT) { inclusive = true }
                            }
                        }
                    },
                    onSelectTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                )
            }

        NavHost(
            navController = navController,
            startDestination = if (current.onboarded) Routes.CHAT else Routes.ONBOARDING,
            modifier = Modifier.weight(1f),
            // 页面切换：淡入 + 小幅横向位移。幅度刻意小（24dp）——
            // 这是「面板切换」不是「翻页」，位移一大就显得笨重。
            // reduced-motion 下位移归零，只剩淡入淡出。
            enterTransition = {
                fadeIn(tween(navDuration)) + slideInHorizontally(tween(navDuration)) { navOffset }
            },
            exitTransition = {
                fadeOut(tween(navDuration)) + slideOutHorizontally(tween(navDuration)) { -navOffset }
            },
            popEnterTransition = {
                fadeIn(tween(navDuration)) + slideInHorizontally(tween(navDuration)) { -navOffset }
            },
            popExitTransition = {
                fadeOut(tween(navDuration)) + slideOutHorizontally(tween(navDuration)) { navOffset }
            },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(
                    deps = container.onboardingDeps,
                    // 屏一接完模型 → 屏二搭沙箱（page-specs §1 的两屏顺序）
                    onDone = { navController.navigate(Routes.SANDBOX_SETUP) },
                )
            }

            composable(Routes.SANDBOX_SETUP) {
                // 屏二有两个入口：首启流程（完事进聊天）、设置页（完事回设置页）。
                // 不分辨来源的话，从设置页搭完会被扔到聊天页，用户找不到回去的路。
                val fromSettings = navController.previousBackStackEntry?.destination?.route == Routes.SETTINGS
                SandboxSetupRoute(
                    deps = container.sandboxSetupDeps,
                    onDone = {
                        if (fromSettings) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.CHAT) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    },
                )
            }

            composable(Routes.CHAT) {
                ChatRoute(
                    deps = container.chatDeps,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenTasks = { navController.navigate(Routes.TASKS) },
                )
            }

            composable(Routes.TASKS) {
                TasksRoute(
                    deps = container.tasksDeps,
                    onOpenTask = { navController.navigate(Routes.taskDetail(it)) },
                    onClose = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.TASK_DETAIL,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) { entry ->
                val taskId = entry.arguments?.getString("taskId") ?: return@composable
                TaskDetailRoute(
                    deps = container.tasksDeps,
                    taskId = taskId,
                    onBack = { navController.popBackStack() },
                    onOpenChat = {
                        navController.popBackStack(Routes.CHAT, inclusive = false)
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsRoute(
                    deps = container.settingsDeps,
                    onBack = { navController.popBackStack() },
                    // 跳过去重搭沙箱。屏二「已就绪」时会自己进主界面，
                    // 用户再回设置页时 refreshSandbox 会把状态刷对。
                    onRebuildSandbox = { navController.navigate(Routes.SANDBOX_SETUP) },
                )
            }
        }
        }
    }
}

@Composable
private fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

/** 侧边栏的数据面板：会话与任务各一份，app 层聚合（feature 之间不互相依赖）。 */
@Composable
private fun SidebarPane(
    container: AppContainer,
    expanded: Boolean,
    tab: SidebarTab,
    selectedConversationId: String?,
    onToggleExpanded: () -> Unit,
    onSelectTab: (SidebarTab) -> Unit,
    onSelectConversation: (String) -> Unit,
    onSelectTask: (String) -> Unit,
) {
    val conversations by container.observeConversations()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by container.observeTaskRuns()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    QuineSidebar(
        expanded = expanded,
        tab = tab,
        conversations = conversations.map {
            SidebarItem(id = it.id, title = it.title.ifBlank { "新会话" })
        },
        tasks = tasks.map {
            SidebarItem(
                id = it.id,
                title = it.title,
                subtitle = com.quine.core.storage.TaskStatus.fromId(it.status).label(),
                // label() 是 feature-tasks 的扩展，app 依赖 feature-tasks，可以直接用。
            )
        },
        selectedId = selectedConversationId,
        onToggleExpanded = onToggleExpanded,
        onSelectTab = onSelectTab,
        onSelectItem = { selectedTab, id ->
            when (selectedTab) {
                SidebarTab.CONVERSATIONS -> onSelectConversation(id)
                SidebarTab.TASKS -> onSelectTask(id)
            }
        },
    )
}
