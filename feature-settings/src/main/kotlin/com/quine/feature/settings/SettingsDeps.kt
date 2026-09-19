package com.quine.feature.settings

import com.quine.core.common.IconVariant
import com.quine.core.common.TrustLevel
import com.quine.core.gateway.ProviderConfig
import com.quine.core.storage.QuineSettings
import kotlinx.coroutines.flow.Flow

/**
 * 设置页需要的外部能力。由 app 层实现 —— feature 不依赖 app。
 */
interface SettingsDeps {

    val settings: Flow<QuineSettings>

    val appVersion: String

    /** 探连通性；`apiKey` 为 null 表示用已存的那把（由 app 层从 Keystore 取，明文不进 UI 层）。 */
    suspend fun checkConnection(provider: ProviderConfig, apiKey: String?): Result<Unit>

    /** 保存供应商配置与 Key。 */
    suspend fun saveProvider(provider: ProviderConfig, apiKey: String?)

    /** 已存 Key 的展示形态（前 3 后 4），没有则返回 null。 */
    suspend fun maskedApiKey(ref: String): String?

    suspend fun setIconVariant(variant: IconVariant)

    /** 真正切换桌面入口。系统行为，可能把当前任务弹回桌面。 */
    suspend fun applyIconVariant(variant: IconVariant)

    suspend fun setTrustLevel(level: TrustLevel)

    suspend fun saveSafTreeUri(uri: String?)

    /** 当前生效的工作区名字。 */
    suspend fun workspaceLabel(): String

    /**
     * Linux 沙箱是否已搭好。
     *
     * 首启屏二有「暂时跳过搭建」的出口 —— 跳过的用户需要一个回来的地方，
     * 否则那是一次单向门：跳过去就再也装不上了。
     */
    fun isSandboxReady(): Boolean
}
