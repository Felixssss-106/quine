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
import com.quine.core.common.ReasoningEffort
import com.quine.core.gateway.LlmMessage
import com.quine.core.gateway.LlmProvider
import com.quine.core.gateway.OpenAiProviderFactory
import com.quine.core.gateway.ProviderConfig
import com.quine.core.gateway.ProviderProbe
import com.quine.core.gateway.RetryPolicy
import com.quine.core.gateway.ToolCallRequest
import com.quine.core.loop.AgentLoop
import com.quine.core.loop.LoopConfig
import com.quine.core.sandbox.RootfsSource
import com.quine.core.sandbox.SandboxInstaller
import com.quine.core.sandbox.SandboxState
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
import com.quine.core.storage.TaskRunEntity
import com.quine.core.storage.TaskStatus
import com.quine.core.storage.TaskStepEntity
import com.quine.core.tools.SnapshotRef
import com.quine.feature.tasks.ArtifactUi
import com.quine.feature.tasks.TasksDeps
import com.quine.feature.tasks.toArtifact
import com.quine.core.tools.SnapshotRegistry
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolRegistry
import com.quine.core.tools.builtin.BuiltinTools
import com.quine.feature.chat.ChatDeps
import com.quine.feature.onboarding.OnboardingDeps
import com.quine.feature.onboarding.SandboxSetupDeps
import com.quine.feature.settings.SettingsDeps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.io.InputStream
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

    val sandboxSetupDeps: SandboxSetupDeps = SandboxSetupDepsImpl()

    val chatDeps: ChatDeps = ChatDepsImpl()

    val tasksDeps: TasksDeps = TasksDepsImpl()

    val settingsDeps: SettingsDeps = SettingsDepsImpl()

    /** 横屏侧边栏用的会话列表（app 层聚合：feature 之间不互相依赖）。 */
    fun observeConversations(): Flow<List<Conversation>> = conversations.observeConversations()

    /** 横屏侧边栏用的任务列表。 */
    fun observeTaskRuns(): Flow<List<TaskRunEntity>> = database.taskDao().observeRuns()

    /** 按给定配置建一个 provider（Key 从 Keystore 取，明文不进调用栈之外）。 */
    private fun newProviderFor(provider: ProviderConfig): LlmProvider = providerFactory.create(
        baseUrl = provider.baseUrl,
        model = provider.model,
        apiKeyProvider = { apiKeyStore.get(provider.apiKeyRef) },
    )

    /** 每次发送都新建一个 loop：loop 内部带取消状态，不复用。 */
    suspend fun newAgentLoop(): AgentLoop {
        val settings = settingsStore.settings.first()
        return AgentLoop(
            provider = newProviderFor(settings.provider),
            tools = toolRegistry,
            config = LoopConfig(reasoningEffort = settings.reasoningEffort),
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
        const val ROOTFS_DIR = "rootfs"
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

    /**
     * 沙箱安装器提到容器层：**屏二与设置页共用同一个实例**。
     *
     * 一个安装器 = 一个 `rootfs` 目录 = 一份 `.quine-ready` 标记。
     * 分成两个实例的话，设置页读到的就绪状态和屏二实际装的地方就不是一回事了
     * —— 那种 bug 只有在"明明搭好了却显示没搭好"时才会被发现。
     */
    private val sandboxInstaller: SandboxInstaller by lazy {
        SandboxInstaller(
            root = File(app.filesDir, ROOTFS_DIR),
            source = PendingRootfsSource(),
        )
    }

    private inner class SandboxSetupDepsImpl : SandboxSetupDeps {

        override fun isSandboxReady(): Boolean = sandboxInstaller.isReady()

        override suspend fun installSandbox(onState: suspend (SandboxState) -> Unit): SandboxState =
            sandboxInstaller.install(onState)

        override fun clearSandbox() = sandboxInstaller.clearRoot()

        override suspend fun completeOnboarding() = settingsStore.setOnboarded(true)
    }

    /**
     * **rootfs 的来源还没定**（见 `docs/plans/m1.md`）：用哪个发行版、从哪个镜像站下、
     * 要不要内置进 APK，都还没拍板。
     *
     * 所以这里不假装有源，而是把「没配置」当成第 ② 步的失败说清楚 ——
     * 比"网络断了"更接近真相，也不会让人以为沙箱已经能用。
     */
    private class PendingRootfsSource : RootfsSource {
        override val expectedSha256: String? = null
        override val sizeBytes: Long? = null

        override fun open(): InputStream =
            throw IOException("还没配置 Linux 工作区的下载地址（M1 待定项）")
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

    /**
     * 任务面：数据全在 `task_runs` / `task_steps` / `snapshots` 三张表里，
     * 这里只是把它们搬过去。**不提供重试 / 批准** —— 那要等任务执行器接进来，
     * 现在声明了就得给假实现，比不做更糟。
     */
    private inner class TasksDepsImpl : TasksDeps {

        private val taskDao: TaskDao by lazy { database.taskDao() }

        override fun observeRuns(): Flow<List<TaskRunEntity>> = taskDao.observeRuns()

        override fun observeSteps(taskId: String): Flow<List<TaskStepEntity>> =
            taskDao.observeSteps(taskId)

        override suspend fun findRun(id: String): TaskRunEntity? = taskDao.findRun(id)

        override suspend fun conversationTitle(id: String?): String? =
            id?.let { database.conversationDao().findConversation(it)?.title }

        override suspend fun artifacts(taskId: String): List<ArtifactUi> =
            taskDao.snapshotsForTask(taskId).map { it.toArtifact() }

        override suspend fun cancel(id: String) {
            taskDao.markEnded(id, System.currentTimeMillis(), TaskStatus.CANCELLED.name, null)
        }
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

        override fun isSandboxReady(): Boolean = sandboxInstaller.isReady()

        override suspend fun setReasoningEffort(effort: ReasoningEffort) =
            settingsStore.setReasoningEffort(effort)

        override suspend fun fetchModels(): Result<List<String>> =
            newProviderFor(settingsStore.settings.first().provider).models()
    }
}
