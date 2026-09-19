package com.quine.feature.tasks

import com.quine.core.storage.TaskRunEntity
import com.quine.core.storage.TaskStepEntity
import kotlinx.coroutines.flow.Flow

/**
 * 任务面需要的外部能力。由 app 层实现 —— feature 不依赖 app。
 *
 * 只放**现在真能做的事**：取消就是把状态改成 CANCELLED，DAO 已经支持。
 * 「重试 / 批准」要等任务执行器（沙箱 + `shell_run`）落地才有意义，
 * 这里先不声明 —— 声明了就得给假实现，那比不做更糟。
 */
interface TasksDeps {

    /** 全部任务，按时间倒序。 */
    fun observeRuns(): Flow<List<TaskRunEntity>>

    /** 某个任务的时间线步骤。 */
    fun observeSteps(taskId: String): Flow<List<TaskStepEntity>>

    suspend fun findRun(id: String): TaskRunEntity?

    /** 来源会话的标题；null 表示没有（定时/分享来源）。 */
    suspend fun conversationTitle(id: String?): String?

    /** 这次任务改过的文件（来自快照表），用于详情页产物区。 */
    suspend fun artifacts(taskId: String): List<ArtifactUi>

    /** 取消任务。 */
    suspend fun cancel(id: String)
}
