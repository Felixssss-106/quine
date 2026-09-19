package com.quine.feature.tasks

import com.quine.core.storage.SnapshotEntity
import com.quine.core.storage.TaskRunEntity
import com.quine.core.storage.TaskStatus
import com.quine.core.storage.TaskStepEntity
import com.quine.core.storage.TaskStepType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 任务步骤的附件块（`page-specs.md` §2 的五件套）。
 *
 * 只用得上 diff 与终端 —— 网页块要浏览器、屏幕块要录屏（M4），
 * 那些类型的块解析出来会被当成"未知"略过，不会把界面搞崩。
 */
@Serializable
data class StepBlock(
    val type: String,
    val title: String? = null,
    /** diff：文件路径。 */
    val path: String? = null,
    /** diff：改动前后全文。 */
    val before: String? = null,
    val after: String? = null,
    /** terminal：输出文本。 */
    val text: String? = null,
    val domain: String? = null,
    @SerialName("fetched_at") val fetchedAt: Long? = null,
)

@Serializable
data class StepPayload(
    val summary: String? = null,
    val blocks: List<StepBlock> = emptyList(),
)

/** 时间线上的一条。标题一律人话（文档：不许术语裸奔）。 */
data class TimelineItemUi(
    val id: String,
    val timeLabel: String,
    val title: String,
    val summary: String?,
    val kind: TaskStepType,
    val blocks: List<StepBlock>,
)

/** 列表与详情页头卡用的任务视图。 */
data class TaskRunUi(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val statusLabel: String,
    val durationLabel: String?,
    val originLabel: String?,
    val createdAtLabel: String,
    val errorText: String?,
)

/**
 * 产物区「这次留下了什么」的一行（page-specs §2-4）。
 *
 * 只有名字和时间 —— 快照表没存体积，不编一个数给用户看。
 */
data class ArtifactUi(
    val snapshotId: String,
    val name: String,
    val timeLabel: String,
)

// ---- 映射 ----

private val PayloadJson = Json { ignoreUnknownKeys = true }

/** payload 解析不出来就当没有附件：坏数据不该让详情页白屏。 */
fun TaskStepEntity.toTimelineItem(): TimelineItemUi {
    val payload = payloadJson?.let { raw ->
        runCatching { PayloadJson.decodeFromString<StepPayload>(raw) }.getOrNull()
    }
    return TimelineItemUi(
        id = id,
        timeLabel = formatClock(ts),
        title = title,
        summary = payload?.summary,
        kind = TaskStepType.fromId(type),
        blocks = payload?.blocks.orEmpty(),
    )
}

suspend fun TaskRunEntity.toUi(conversationTitle: suspend (String?) -> String?): TaskRunUi {
    val status = TaskStatus.fromId(this.status)
    return TaskRunUi(
        id = id,
        title = title,
        status = status,
        statusLabel = status.label(),
        durationLabel = durationLabel(),
        originLabel = conversationTitle(originConversationId)?.let { "来源会话：$it" },
        createdAtLabel = formatClock(createdAt),
        errorText = errorJson,
    )
}

/** 状态胶囊的人话文案（page-specs §2 任务头卡）。 */
fun TaskStatus.label(): String = when (this) {
    TaskStatus.QUEUED -> "排队中"
    TaskStatus.RUNNING -> "运行中"
    TaskStatus.NEEDS_APPROVAL -> "需确认"
    TaskStatus.SUCCEEDED -> "完成"
    TaskStatus.FAILED -> "失败"
    TaskStatus.CANCELLED -> "已取消"
}

/** 详情页读数行里的耗时（00:42）。 */
private fun TaskRunEntity.durationLabel(): String? {
    val start = startedAt ?: return null
    val end = endedAt ?: return null
    if (end < start) return null
    val seconds = (end - start) / 1_000
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}

fun SnapshotEntity.toArtifact(): ArtifactUi = ArtifactUi(
    snapshotId = id,
    name = path,
    timeLabel = formatClock(createdAt),
)

/**
 * 时间标签。
 *
 * 不缓存 formatter：locale 和时区是**运行时可变**的（系统设置里随时能改），
 * 把它们钉在一个静态字段上，改完语言后时间格式还是旧的。
 */
private fun formatClock(epochMillis: Long): String = runCatching {
    DateTimeFormatter
        .ofPattern("HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}.getOrDefault("--:--:--")
