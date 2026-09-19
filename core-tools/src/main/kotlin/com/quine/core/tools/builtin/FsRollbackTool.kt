package com.quine.core.tools.builtin

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.tools.Tool
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolResult
import com.quine.core.tools.ToolSpec
import com.quine.core.tools.workspace.WriteOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `fs_rollback`：把文件恢复到改动前的样子 —— 「改坏了一键回滚」的那一键。
 *
 * 三条自律，缺一条它就不是「回滚」而是另一种破坏：
 * 1. **只回滚本工作区的快照**。`path` 是相对路径，用户在「授权目录」与「私有工作区」之间
 *    切换后，同名文件是另一个文件；对不上就拒绝。
 * 2. **回滚前先给当前内容也做一份快照**。回滚本身是一次写入，所以它自己也得能撤回 ——
 *    否则用户从「改坏了」跳到「回滚错了」，两头都没救。
 * 3. 和 `fs_write` 一样 **fail closed**：快照做不成、或 blob 已经不在，就报错且不动文件。
 */
class FsRollbackTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        id = ID,
        title = "回滚文件",
        description = "把工作区里的一个文件恢复到改动前的样子。" +
            "给 path 就回滚这个文件最近一次的快照；给 snapshot（fs_write 成功时给出的快照 id）" +
            "就回滚到那一次。回滚前的当前内容也会存成新快照，所以这次回滚还能再撤回。",
        parameters = PARAMETERS,
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val startedAt = context.time.nowMillis()
        val workspace = context.workspace

        val snapshotId = args.stringOrNull("snapshot")
        val path = args.stringOrNull("path")

        val ref = when {
            snapshotId != null -> context.snapshots.find(snapshotId)
                ?: return fail(context, startedAt, snapshotNotFound(snapshotId))

            path != null -> context.snapshots.latestFor(path)
                ?: return fail(context, startedAt, noSnapshotForPath(path))

            else -> return fail(context, startedAt, MISSING_TARGET)
        }

        // 跨工作区回滚 = 把 A 的旧内容写进 B 的同名文件。宁可不动。
        if (ref.root != workspace.label) {
            return fail(context, startedAt, wrongWorkspace(workspace.label, ref.root))
        }

        val bytes = context.snapshots.contentOf(ref.blobRef)
            ?: return fail(context, startedAt, blobMissing(ref.id))

        val current = workspace.readBytes(ref.path)

        // 回滚也是一次写入，所以先把「当前这份」存下来 —— 回滚错了还能再滚回来。
        val undoRef = if (current != null) {
            context.snapshots.capture(ref.path, current, root = workspace.label)
                ?: return fail(context, startedAt, SNAPSHOT_FAILED)
        } else {
            null
        }

        return when (val outcome = workspace.writeBytes(ref.path, bytes, expectedOldBytes = current)) {
            is WriteOutcome.Ok -> ToolResult(
                toolId = ID,
                ok = true,
                output = buildString {
                    append("[${workspace.label}] ${ref.path} — 已回到快照 ${ref.id}（${outcome.bytesWritten} 字节）")
                    if (undoRef != null) {
                        append("\n回滚前的那份也存成了 ${undoRef.id}：要是回滚错了，再回滚一次就换回来")
                    } else {
                        append("\n（回滚前文件已不在，等于是把它恢复了）")
                    }
                },
                sourceRef = "${workspace.label}:${ref.path}",
                durationMillis = context.time.nowMillis() - startedAt,
            )

            WriteOutcome.Conflict -> fail(context, startedAt, CONFLICT)
            WriteOutcome.NotFound -> fail(context, startedAt, notFound(ref.path))
            WriteOutcome.IsDirectory -> fail(context, startedAt, isDirectory(ref.path))
            is WriteOutcome.Failed -> fail(context, startedAt, outcome.error)
        }
    }

    private fun fail(context: ToolContext, startedAt: Long, error: QuineError): ToolResult = ToolResult(
        toolId = ID,
        ok = false,
        output = error.message + error.nextStep,
        error = error,
        durationMillis = context.time.nowMillis() - startedAt,
    )

    companion object {
        const val ID = "fs_rollback"

        val MISSING_TARGET = QuineError(
            kind = ErrorKind.TOOL,
            message = "没说要回滚哪个文件。",
            impact = "没有改动任何文件。",
            nextStep = "给 path（回滚它最近一次快照），或给 snapshot（fs_write 给出的快照 id）。",
        )

        val SNAPSHOT_FAILED = QuineError(
            kind = ErrorKind.STORAGE,
            message = "回滚前的那份内容没能存成快照。",
            impact = "为了不留撤不回来的改动，这次没有回滚。",
            nextStep = "清理出存储空间后重试；回滚前必须先能存下当前内容。",
        )

        val CONFLICT = QuineError(
            kind = ErrorKind.TOOL,
            message = "这个文件在我准备回滚时被改过了。",
            impact = "为了不覆盖别人的改动，这次没有回滚。",
            nextStep = "先看一眼文件现在的内容，确认后再回滚一次。",
        )

        fun snapshotNotFound(id: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "没有 $id 这条快照。",
            impact = "没有改动任何文件。",
            nextStep = "用 fs_rollback 加 path 回滚最近一次快照，或先 fs_write 改一次生成新快照。",
        )

        fun noSnapshotForPath(path: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "$path 没有可回滚的快照。",
            impact = "没有改动任何文件。",
            nextStep = "只有被 fs_write 改过的文件才有快照；新建的文件没有旧内容可回滚。",
        )

        fun wrongWorkspace(current: String, snapshotRoot: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "这条快照属于「$snapshotRoot」，不是当前的「$current」。",
            impact = "没有改动任何文件 —— 同名文件在不同工作区里是两个文件，回滚过去就是覆盖。",
            nextStep = "切回「$snapshotRoot」再回滚，或者在当前工作区里重新改一次。",
        )

        fun blobMissing(id: String) = QuineError(
            kind = ErrorKind.STORAGE,
            message = "快照 $id 的内容已经不在了（可能被清理过）。",
            impact = "没有改动任何文件。",
            nextStep = "用同一路径其它快照回滚；没有的话只能手工改回。",
        )

        fun notFound(path: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "工作区里没有 $path。",
            impact = "没有回滚。",
            nextStep = "确认路径拼写；文件被删掉的话，回滚会把内容重新写回来。",
        )

        fun isDirectory(path: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "$path 是一个目录，不是文件。",
            impact = "没有回滚。",
            nextStep = "把 path 换成目录里的具体文件名。",
        )

        private val SchemaJson = Json { ignoreUnknownKeys = true }

        private val PARAMETERS: JsonObject = SchemaJson.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "要回滚的文件路径；用它最近一次快照" },
                "snapshot": { "type": "string", "description": "快照 id（fs_write 成功时给出）；给了就以它为准" }
              },
              "required": [],
              "additionalProperties": false
            }
            """.trimIndent(),
        ).jsonObject
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
