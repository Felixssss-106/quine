package com.quine.core.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // ---- TaskRun ----

    @Query("SELECT * FROM task_runs ORDER BY createdAt DESC")
    fun observeRuns(): Flow<List<TaskRunEntity>>

    @Query("SELECT * FROM task_runs WHERE status = :status ORDER BY createdAt DESC")
    fun observeRunsByStatus(status: String): Flow<List<TaskRunEntity>>

    @Query("SELECT * FROM task_runs WHERE id = :id")
    suspend fun findRun(id: String): TaskRunEntity?

    @Query("SELECT * FROM task_runs WHERE id = :id")
    fun observeRun(id: String): Flow<TaskRunEntity?>

    @Upsert
    suspend fun upsertRun(run: TaskRunEntity)

    /** 只更新状态与时间戳：loop 每次相位变化都会走这里，不覆盖其它字段。 */
    @Query("UPDATE task_runs SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE task_runs SET startedAt = :at, status = :status WHERE id = :id")
    suspend fun markStarted(id: String, at: Long, status: String)

    @Query("UPDATE task_runs SET endedAt = :at, status = :status, costJson = :costJson WHERE id = :id")
    suspend fun markEnded(id: String, at: Long, status: String, costJson: String?)

    @Query("UPDATE task_runs SET errorJson = :errorJson WHERE id = :id")
    suspend fun setError(id: String, errorJson: String?)

    @Query("DELETE FROM task_runs WHERE id = :id")
    suspend fun deleteRun(id: String)

    // ---- TaskStep ----

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY idx ASC, ts ASC")
    fun observeSteps(taskId: String): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY idx ASC, ts ASC")
    suspend fun steps(taskId: String): List<TaskStepEntity>

    @Upsert
    suspend fun upsertStep(step: TaskStepEntity)

    @Query("SELECT COUNT(*) FROM task_steps WHERE taskId = :taskId")
    suspend fun stepCount(taskId: String): Int

    @Query("DELETE FROM task_steps WHERE taskId = :taskId")
    suspend fun deleteSteps(taskId: String)

    // ---- Snapshot ----

    @Upsert
    suspend fun upsertSnapshot(snapshot: SnapshotEntity)

    @Query("SELECT * FROM snapshots WHERE taskId = :taskId ORDER BY createdAt DESC")
    suspend fun snapshotsForTask(taskId: String): List<SnapshotEntity>

    /** 回滚用：某个路径最近一次快照。 */
    @Query("SELECT * FROM snapshots WHERE path = :path ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestSnapshot(path: String): SnapshotEntity?

    @Query("SELECT * FROM snapshots WHERE blobRef = :blobRef LIMIT 1")
    suspend fun findByBlob(blobRef: String): SnapshotEntity?

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: String)
}
