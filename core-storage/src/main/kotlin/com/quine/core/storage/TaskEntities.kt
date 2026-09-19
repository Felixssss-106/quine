package com.quine.core.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 任务运行（agent-prompt.md §2.6）。
 *
 * M1 的「双主面」里，任务面就建立在这张表上：列表按状态分组、详情页读它的读数行。
 */
@Entity(
    tableName = "task_runs",
    indices = [Index("status"), Index("originConversationId"), Index("createdAt")],
)
data class TaskRunEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** 见 [TaskStatus]。存名字而非序号，避免改枚举时历史数据错位。 */
    val status: String,
    /** 见 [TaskOrigin]。 */
    val originType: String,
    val originConversationId: String?,
    val createdAt: Long,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    /** token 与费用口径还没定（蓝图 §7「成本观感」是持续风险），先用 JSON 存，避免改表。 */
    val costJson: String? = null,
    val errorJson: String? = null,
)

/** 任务状态（page-specs.md §2 的四种 + 详情页的「排队等待中」）。 */
enum class TaskStatus {
    /** 排队中：还没开始，可取消。 */
    QUEUED,

    RUNNING,

    /** 需确认：卡在审批上，等用户点头。 */
    NEEDS_APPROVAL,

    SUCCEEDED,

    FAILED,

    /** 被用户或系统取消。 */
    CANCELLED,
    ;

    companion object {
        fun fromId(id: String?): TaskStatus = entries.firstOrNull { it.name == id } ?: FAILED
    }
}

/** 任务来源（agent-prompt.md §2.6）。 */
enum class TaskOrigin {
    CONVERSATION,
    TIMER,
    SHARE,
    ;

    companion object {
        fun fromId(id: String?): TaskOrigin = entries.firstOrNull { it.name == id } ?: CONVERSATION
    }
}

/**
 * 任务步骤（agent-prompt.md §2.6）—— 任务详情页「单一时间线」的每一行。
 *
 * `payloadJson` 承载五件套附件块（diff / 终端 / 网页 / 屏幕）的内容，默认折叠展示。
 */
@Entity(
    tableName = "task_steps",
    foreignKeys = [
        ForeignKey(
            entity = TaskRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId"), Index(value = ["taskId", "idx"])],
)
data class TaskStepEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    /** 同一任务内的顺序，用于时间线排序。 */
    val idx: Int,
    /** 见 [TaskStepType]。 */
    val type: String,
    /** 人话标题（「改了 3 个文件」）—— 不许术语裸奔（page-specs.md §2）。 */
    val title: String,
    val payloadJson: String? = null,
    val ts: Long,
)

enum class TaskStepType {
    THINK,
    TOOL,
    OUTPUT,
    ERROR,
    APPROVAL,
    ;

    companion object {
        fun fromId(id: String?): TaskStepType = entries.firstOrNull { it.name == id } ?: OUTPUT
    }
}

/**
 * 文件快照（agent-prompt.md §2.6）—— **改动前强制生成**，是一键回滚的根据。
 *
 * `blobRef` 是内容寻址：同一内容只存一份，回滚就是把 blob 写回 `path`。
 */
@Entity(
    tableName = "snapshots",
    indices = [Index("taskId"), Index("path"), Index("createdAt")],
)
data class SnapshotEntity(
    @PrimaryKey val id: String,
    /** 被改动文件的原始路径（相对工作区根）。 */
    val path: String,
    /** 内容寻址存储的引用，例如 `blob/sha256/<hash>`。 */
    val blobRef: String,
    /**
     * 这条快照属于哪个工作区（`Workspace.label`）。
     *
     * `path` 是相对路径，而用户可以在「授权目录」与「私有工作区」之间切换 ——
     * 没有它，回滚就会拿 A 工作区的旧内容去覆盖 B 工作区的同名文件。
     * 空串表示「未知」（v3 之前留下的老数据），回滚时按对不上处理。
     */
    @ColumnInfo(defaultValue = "''") val root: String = "",
    val taskId: String? = null,
    val createdAt: Long,
)
