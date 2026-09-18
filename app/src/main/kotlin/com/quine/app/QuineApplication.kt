package com.quine.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 应用入口。依赖在 [AppContainer] 里手工装配（M0 不引 DI 框架）。
 */
class QuineApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 把桌面入口与持久化的图标变体对齐。
        // 清数据、恢复备份、或者上次切换到一半被杀，都可能让两者不一致。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val variant = container.settingsStore.settings.first().iconVariant
                if (container.iconAlias.currentVariant() != variant) {
                    container.iconAlias.apply(variant)
                }
            }
        }
    }
}
