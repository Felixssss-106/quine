package com.quine.app

import android.content.Context
import com.quine.core.storage.SettingsStore
import com.quine.core.tools.workspace.PrivateWorkspace
import com.quine.core.tools.workspace.SafWorkspace
import com.quine.core.tools.workspace.Workspace
import com.quine.core.tools.workspace.WorkspaceProvider
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * 决定当前生效的工作区：用户授权目录优先，否则回落应用私有工作区。
 *
 * 回落根 = `context.filesDir/workspace`（`/data/data/com.quine.app/files/workspace`），
 * 不需要任何存储权限，卸载即清。回落时 UI 必须给出说明，不得静默。
 *
 * 每次读取都问一次 DataStore：DataStore 自带内存缓存，成本可以忽略，
 * 换来的是「用户在设置页换完目录，下一次工具调用立刻生效」。
 */
class AppWorkspaceProvider(
    context: Context,
    private val settings: SettingsStore,
) : WorkspaceProvider {

    private val appContext = context.applicationContext

    private val privateRoot: File = File(appContext.filesDir, PrivateWorkspace.DIRECTORY_NAME)
        .apply { mkdirs() }

    override suspend fun workspace(): Workspace {
        val raw = settings.settings.first().safTreeUri
        if (raw.isNullOrBlank()) return PrivateWorkspace(privateRoot)

        // 授权可能已被系统回收（换机、清数据、provider 撤销），此时同样回落，不崩。
        return SafWorkspace.fromTreeUri(appContext, raw) ?: PrivateWorkspace(privateRoot)
    }
}
