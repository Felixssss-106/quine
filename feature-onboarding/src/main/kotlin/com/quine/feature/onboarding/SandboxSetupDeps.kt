package com.quine.feature.onboarding

import com.quine.core.sandbox.SandboxState

/**
 * 首启屏二需要的外部能力（`page-specs.md` §1）。由 app 层实现 —— feature 不依赖 app。
 *
 * 这里的形状是**照着状态机给的**：`installSandbox` 边跑边把每一步状态喂回来，
 * 屏二因此能画出步骤点、进度条和日志，也能在失败时说清卡在第几步。
 */
interface SandboxSetupDeps {

    /** 沙箱装好了没有：装好了就直接进主界面（"之后秒开"）。 */
    fun isSandboxReady(): Boolean

    /** 跑一次安装。`onState` 会收到每一步的状态，最后一个是终态。 */
    suspend fun installSandbox(onState: suspend (SandboxState) -> Unit): SandboxState

    /** 清掉装了一半的残留。失败态的「清理空间」之后、重试之前会用到。 */
    fun clearSandbox()

    /** 标记首启完成。 */
    suspend fun completeOnboarding()
}
