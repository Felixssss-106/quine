package com.quine.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quine.core.common.IconVariant
import com.quine.core.common.TrustLevel
import com.quine.core.gateway.ProviderConfig
import com.quine.core.gateway.ProviderPresets
import com.quine.core.gateway.toConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.quineSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "quine_settings")

/** 全量设置快照。UI 只读它，写一律走 [SettingsStore] 的具名方法。 */
data class QuineSettings(
    /** 首启是否已完成（决定导航起点）。 */
    val onboarded: Boolean = false,
    val provider: ProviderConfig = ProviderPresets.byId(DEFAULT_PRESET_ID).toConfig(),
    /** 用户 SAF 授权的目录；null = 未授权，工作区回落私有目录。 */
    val safTreeUri: String? = null,
    val iconVariant: IconVariant = IconVariant.LIGHT,
    val trustLevel: TrustLevel = TrustLevel.STANDARD,
    val activeConversationId: String? = null,
    /** composer 草稿。M0 只有单会话，因此是单值；多会话时按会话分键。 */
    val draft: String = "",
) {
    /** Key 与 baseUrl 都齐了才算「接上模型」。 */
    val providerReady: Boolean
        get() = provider.baseUrl.isNotBlank() && provider.model.isNotBlank()

    companion object {
        const val DEFAULT_PRESET_ID = "deepseek"
    }
}

/**
 * DataStore 设置存储（agent-prompt.md §2.1）。
 * 「状态永不丢」要求草稿、图标变体、授权目录在进程重启后原样回来，因此都落在这里。
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<QuineSettings> = context.quineSettingsDataStore.data
        .catch { error ->
            // 读盘失败不该让 App 起不来：退回默认值，UI 侧仍可正常用。
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }

    suspend fun setOnboarded(value: Boolean) = edit { it[KEY_ONBOARDED] = value }

    suspend fun setProvider(config: ProviderConfig) = edit { prefs ->
        prefs[KEY_PROVIDER_PRESET_ID] = config.presetId
        prefs[KEY_PROVIDER_DISPLAY_NAME] = config.displayName
        prefs[KEY_PROVIDER_BASE_URL] = config.baseUrl
        prefs[KEY_PROVIDER_MODEL] = config.model
        prefs[KEY_PROVIDER_API_KEY_REF] = config.apiKeyRef
        prefs[KEY_PROVIDER_IS_CUSTOM] = config.isCustom
    }

    suspend fun setSafTreeUri(uri: String?) = edit { prefs ->
        if (uri.isNullOrBlank()) prefs.remove(KEY_SAF_TREE_URI) else prefs[KEY_SAF_TREE_URI] = uri
    }

    suspend fun setIconVariant(variant: IconVariant) = edit { it[KEY_ICON_VARIANT] = variant.name }

    suspend fun setTrustLevel(level: TrustLevel) = edit { it[KEY_TRUST_LEVEL] = level.name }

    suspend fun setActiveConversationId(id: String?) = edit { prefs ->
        if (id.isNullOrBlank()) prefs.remove(KEY_ACTIVE_CONVERSATION) else prefs[KEY_ACTIVE_CONVERSATION] = id
    }

    suspend fun setDraft(text: String) = edit { it[KEY_DRAFT] = text }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.quineSettingsDataStore.edit(block)
    }

    private fun Preferences.toSettings(): QuineSettings {
        val preset = ProviderPresets.byId(this[KEY_PROVIDER_PRESET_ID] ?: QuineSettings.DEFAULT_PRESET_ID)
        val fallback = preset.toConfig()
        return QuineSettings(
            onboarded = this[KEY_ONBOARDED] ?: false,
            provider = ProviderConfig(
                presetId = preset.id,
                displayName = this[KEY_PROVIDER_DISPLAY_NAME] ?: fallback.displayName,
                baseUrl = this[KEY_PROVIDER_BASE_URL] ?: fallback.baseUrl,
                model = this[KEY_PROVIDER_MODEL] ?: fallback.model,
                apiKeyRef = this[KEY_PROVIDER_API_KEY_REF] ?: ProviderConfig.DEFAULT_API_KEY_REF,
                isCustom = this[KEY_PROVIDER_IS_CUSTOM] ?: preset.isCustom,
            ),
            safTreeUri = this[KEY_SAF_TREE_URI],
            iconVariant = IconVariant.fromId(this[KEY_ICON_VARIANT]),
            trustLevel = TrustLevel.fromId(this[KEY_TRUST_LEVEL]),
            activeConversationId = this[KEY_ACTIVE_CONVERSATION],
            draft = this[KEY_DRAFT].orEmpty(),
        )
    }

    private companion object {
        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_PROVIDER_PRESET_ID = stringPreferencesKey("provider_preset_id")
        val KEY_PROVIDER_DISPLAY_NAME = stringPreferencesKey("provider_display_name")
        val KEY_PROVIDER_BASE_URL = stringPreferencesKey("provider_base_url")
        val KEY_PROVIDER_MODEL = stringPreferencesKey("provider_model")
        val KEY_PROVIDER_API_KEY_REF = stringPreferencesKey("provider_api_key_ref")
        val KEY_PROVIDER_IS_CUSTOM = booleanPreferencesKey("provider_is_custom")
        val KEY_SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        val KEY_ICON_VARIANT = stringPreferencesKey("icon_variant")
        val KEY_TRUST_LEVEL = stringPreferencesKey("trust_level")
        val KEY_ACTIVE_CONVERSATION = stringPreferencesKey("active_conversation_id")
        val KEY_DRAFT = stringPreferencesKey("draft")
    }
}
