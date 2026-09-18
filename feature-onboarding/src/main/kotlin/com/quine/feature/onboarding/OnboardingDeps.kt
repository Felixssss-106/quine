package com.quine.feature.onboarding

import com.quine.core.gateway.ProviderConfig
import com.quine.core.storage.QuineSettings
import kotlinx.coroutines.flow.Flow

/**
 * 首启屏一需要的外部能力。由 app 层实现 —— feature 不依赖 app。
 */
interface OnboardingDeps {

    val settings: Flow<QuineSettings>

    /** 用临时 Key 探一次连通性，不落库。 */
    suspend fun checkKey(provider: ProviderConfig, apiKey: String): Result<Unit>

    /** 保存供应商配置（DataStore）与 Key（Keystore）。 */
    suspend fun saveProvider(provider: ProviderConfig, apiKey: String)

    /** 保存 SAF 授权目录；null 表示用户没选，工作区回落私有目录。 */
    suspend fun saveSafTreeUri(uri: String?)

    /** 标记首启完成。 */
    suspend fun completeOnboarding()

    /** 当前生效的工作区名字，用于「未授权会回落」的说明文案。 */
    suspend fun workspaceLabel(): String
}
