package com.quine.app

import android.app.Application
import com.quine.core.common.IconAliasController
import com.quine.core.common.IconVariant
import com.quine.core.common.LogRedaction
import com.quine.core.common.QuineDispatchers
import com.quine.core.common.TrustLevel
import com.quine.core.gateway.ChatRole
import com.quine.core.gateway.LlmErrors
import com.quine.core.gateway.LlmException
import com.quine.core.gateway.LlmMessage
import com.quine.core.gateway.OpenAiProviderFactory
import com.quine.core.gateway.ProviderConfig
import com.quine.core.gateway.ProviderProbe
import com.quine.core.gateway.RetryPolicy
import com.quine.core.gateway.ToolCallRequest
import com.quine.core.loop.AgentLoop
import com.quine.core.loop.LoopConfig
import com.quine.core.storage.ApiKeyStore
import com.quine.core.storage.ChatMessage
import com.quine.core.storage.Conversation
import com.quine.core.storage.ConversationStore
import com.quine.core.storage.KeystoreApiKeyStore
import com.quine.core.storage.MessageMeta
import com.quine.core.storage.QuineDatabase
import com.quine.core.storage.QuineSettings
import com.quine.core.storage.SettingsStore
import com.quine.core.storage.SnapshotEntity
import com.quine.core.storage.SnapshotStore
import com.quine.core.storage.TaskDao
import com.quine.core.tools.SnapshotRef
import com.quine.core.tools.SnapshotRegistry
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolRegistry
import com.quine.core.tools.builtin.BuiltinTools
import com.quine.feature.chat.ChatDeps
import com.quine.feature.onboarding.OnboardingDeps
import com.quine.feature.settings.SettingsDeps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/**
 * 手工装配（M0 不引 DI 框架）。
 *
 * 两条依赖纪律在这里落地：
 * - `core-loop` 不认识 `core-storage`：历史由这里取出、作为参数传进 loop。
 * - feature 不认识 app：feature 定义 `*Deps` 接口，这里实现。
 */
class AppContainer(private val app: Application) {

    val dispatchers = QuineDispatchers()

    val settingsStore = SettingsStore(app)

    val apiKeyStore: ApiKeyStore = KeystoreApiKeyStore(app, dispatchers.io)

    private val database: QuineDatabase by lazy { QuineDatabase.create(app) }

    val conversations: ConversationStore by lazy {
        ConversationStore(database.conversationDao(), dispatchers = dispatchers)
    }

    val workspaces = AppWorkspaceProvider(app, settingsStore)

    /** 快照 blob 存放处（内容寻址，同一内容只存一份）。 */
    val snapshotStore: SnapshotStore by lazy { SnapshotStore(File(app.filesDir, SNAPSHOT_DIR)) }

    val iconAlias: IconAliasController = ActivityAliasIconController(app)

    // 工具清单集中在 BuiltinTools：逐个 new 的话，漏掉一个既不报错、也不会被测试发现。
    private val toolRegistry = ToolRegistry(BuiltinTools.all)

    /**
     * 重试的**唯一权威在 loop**，所以这里把 provider 的重试预算关掉。
     * 否则两层各重试 2 次，一次请求会放大成 9 次。
     */
    private val providerFactory = OpenAiProviderFactory(
        dispatcher = dispatchers.io,
        retryPolicy = RetryPolicy(maxRetries = 0),
    )

    private val providerProbe = ProviderProbe(providerFactory)

    val onboardingDeps: OnboardingDeps = OnboardingDepsImpl()

    val chatDeps: ChatDeps = ChatDepsImpl()

    val settingsDeps: SettingsDeps = SettingsDepsImpl()

    /** 每次发送都新建一个 loop：loop 内部带取消状态，不复用。 */
    suspend fun newAgentLoop(): AgentLoop {
        val provider = settingsStore.settings.first().provider
        return AgentLoop(
            provider = providerFactory.create(
                baseUrl = provider.baseUrl,
                model = provider.model,
                apiKeyProvider = { apiKeyStore.get(provider.apiKeyRef) },
            ),
            tools = toolRegistry,
            config = LoopConfig(),
            retryPolicy = RetryPolicy(),
        )
    }

    /** 快照登记处：blob 存内容，数据库存「哪个路径、什么时候、回滚用哪个 id」。 */
    val snapshots: SnapshotRegistry by lazy {
        StoreSnapshots(store = snapshotStore, dao = database.taskDao())
    }

    private suspend fun toolContext(): ToolContext = ToolContext(
        workspace = workspaces.workspace(),
        snapshots = snapshots,
    )

    /**
     * 把 `core-tools` 的快照接口接到 `core-storage` 上：
     * 内容进 [SnapshotStore]（内容寻址），元数据进 `snapshots` 表（供回滚查询）。
     *
     * 两条都成功才算数 —— 只写了 blob 没登记，就等于「有备份但找不到」，
     * 回滚时照样救不回来。
     */
    private class StoreSnapshots(
        private val store: SnapshotStore,
        private val dao: TaskDao,
    ) : SnapshotRegistry {

        // 失败返回 null —— 写工具据此拒绝写入，绝不留下没有快照的改动。
        override suspend fun capture(path: String, oldBytes: ByteArray, root: String): SnapshotRef? =
            runCatching {
                val blobRef = store.put(oldBytes)
                val ref = SnapshotRef(
                    id = UUID.randomUUID().toString(),
                    path = path,
                    blobRef = blobRef,
                    root = root,
                    createdAt = System.currentTimeMillis(),
                )
                dao.upsertSnapshot(
                    SnapshotEntity(
                        id = ref.id,
                        path = ref.path,
                        blobRef = ref.blobRef,
                        root = ref.root,
                        createdAt = ref.createdAt,
                    ),
                )
                ref
            }.getOrNull()

        override suspend fun latestFor(path: String): SnapshotRef? =
            runCatching { dao.latestSnapshot(path)?.toRef() }.getOrNull()

        override suspend fun find(id: String): SnapshotRef? =
            runCatching { dao.snapshot(id)?.toRef() }.getOrNull()

        override suspend fun contentOf(blobRef: String): ByteArray? =
            runCatching { store.content(blobRef) }.getOrNull()

        private fun SnapshotEntity.toRef(): SnapshotRef = SnapshotRef(
            id = id,
            path = path,
            blobRef = blobRef,
            root = root,
            createdAt = createdAt,
        )
    }

    private companion object {
        const val SNAPSHOT_DIR = "snapshots"
    }

    private fun appVersion(): String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull().orEmpty()

    // ---- feature 契约的实现 ----

    private inner class OnboardingDepsImpl : OnboardingDeps {
        override val settings: Flow<QuineSettings> = settingsStore.settings

        override suspend fun checkKey(provider: ProviderConfig, apiKey: String): Result<Unit> =
            providerProbe.check(provider, apiKey)

        override suspend fun saveProvider(provider: ProviderConfig, apiKey: String) {
            apiKeyStore.put(provider.apiKeyRef, apiKey)
            settingsStore.setProvider(provider)
        }

        override suspend fun saveSafTreeUri(uri: String?) = settingsStore.setSafTreeUri(uri)

        override suspend fun completeOnboarding() = settingsStore.setOnboarded(true)

        override suspend fun workspaceLabel(): String = workspaces.workspace().label
    }

    private inner class ChatDepsImpl : ChatDeps {
        override val settings: Flow<QuineSettings> = settingsStore.settings

        override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
            conversations.observeMessages(conversationId)

        override suspend fun ensureConversation(preferredId: String?): Conversation =
            conversations.ensureConversation(preferredId)

        override suspend fun history(conversationId: String): List<LlmMessage> =
            conversations.history(conversationId)

        override suspend fun appendMessage(
            conversationId: String,
            role: ChatRole,
            text: String,
            toolCalls: List<ToolCallRequest>,
            toolCallId: String?,
            toolName: String?,
            meta: MessageMeta,
        ): ChatMessage = conversations.append(
            conversationId = conversationId,
            role = role,
            text = text,
            toolCalls = toolCalls,
            toolCallId = toolCallId,
            toolName = toolName,
            meta = meta,
        )

        override suspend fun deleteMessage(id: String) = conversations.deleteMessage(id)

        override suspend fun createLoop(): AgentLoop = newAgentLoop()

        override suspend fun toolContext(): ToolContext = this@AppContainer.toolContext()

        override suspend fun setActiveConversationId(id: String) =
            settingsStore.setActiveConversationId(id)

        override suspend fun saveDraft(text: String) = settingsStore.setDraft(text)

        override suspend fun setTrustLevel(level: TrustLevel) = settingsStore.setTrustLevel(level)
    }

    private inner class SettingsDepsImpl : SettingsDeps {
        override val settings: Flow<QuineSettings> = settingsStore.settings

        override val appVersion: String = appVersion()

        override suspend fun checkConnection(
            provider: ProviderConfig,
            apiKey: String?,
        ): Result<Unit> {
            val key = apiKey ?: apiKeyStore.get(provider.apiKeyRef)
            ?: return Result.failure(LlmException(LlmErrors.missingKey()))
            return providerProbe.check(provider, key)
        }

        override suspend fun saveProvider(provider: ProviderConfig, apiKey: String?) {
            if (apiKey != null) apiKeyStore.put(provider.apiKeyRef, apiKey)
            settingsStore.setProvider(provider)
        }

        override suspend fun maskedApiKey(ref: String): String? =
            apiKeyStore.get(ref)?.let { LogRedaction.maskKey(it) }

        override suspend fun setIconVariant(variant: IconVariant) =
            settingsStore.setIconVariant(variant)

        override suspend fun applyIconVariant(variant: IconVariant) = iconAlias.apply(variant)

        override suspend fun setTrustLevel(level: TrustLevel) = settingsStore.setTrustLevel(level)

        override suspend fun saveSafTreeUri(uri: String?) = settingsStore.setSafTreeUri(uri)

        override suspend fun workspaceLabel(): String = workspaces.workspace().label
    }
}
