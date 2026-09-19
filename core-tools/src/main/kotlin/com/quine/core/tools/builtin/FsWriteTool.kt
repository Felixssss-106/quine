package com.quine.core.tools.builtin

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.tools.Snapshotter
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
 * `fs_write`（agent-prompt.md §3.9）：写工作区里的文本文件。
 *
 * 红线（v1-blueprint.md §2.1）：**改动前自动快照**。
 * 这里把它做成了硬约束 —— 拿不到快照就拒绝写入，绝不留下撤不回来的改动。
 *
 * 另外做了一层 TOCTOU 防护：快照和写入之间如果文件被别人改过（例如用户手动改了），
 * 写入会返回 Conflict 而不是覆盖。
 */
class FsWriteTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        id = ID,
        title = "写文件",
        description = "把整段文本写入工作区里的一个文件（会覆盖原内容）。" +
            "写入前会自动做快照，改坏了可以回滚。path 是相对工作区根的路径。",
        parameters = PARAMETERS,
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val startedAt = context.time.nowMillis()

        val path = args.stringOrNull("path")
        val content = args.stringOrNull("content")
        if (path == null) return fail(context, startedAt, MISSING_PATH)
        if (content == null) return fail(context, startedAt, MISSING_CONTENT)

        val workspace = context.workspace
        val bytes = content.toByteArray(Charsets.UTF_8)
        val oldBytes = workspace.readBytes(path)

        // 快照是硬约束：失败就不许写。
        val snapshotRef = if (oldBytes != null) {
            context.snapshotter.capture(path, oldBytes)
                ?: return fail(context, startedAt, SNAPSHOT_FAILED)
        } else {
            null
        }

        // 用刚才读到的内容做乐观并发检查：快照与写入之间若被改过，就拒绝覆盖。
        val outcome = workspace.writeBytes(path, bytes, expectedOldBytes = oldBytes)

        return when (outcome) {
            is WriteOutcome.Ok -> ToolResult(
                toolId = ID,
                ok = true,
                output = buildString {
                    append("[${workspace.label}] $path — 写入 ${outcome.bytesWritten} 字节")
                    if (snapshotRef != null) append("\n快照：$snapshotRef（可回滚）")
                    else append("\n（新文件，无快照）")
                },
                sourceRef = "${workspace.label}:$path",
                durationMillis = context.time.nowMillis() - startedAt,
            )

            WriteOutcome.Conflict -> fail(context, startedAt, CONFLICT)
            WriteOutcome.NotFound -> fail(context, startedAt, notFound(path))
            WriteOutcome.IsDirectory -> fail(context, startedAt, isDirectory(path))
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
        const val ID = "fs_write"

        val MISSING_PATH = QuineError(
            kind = ErrorKind.TOOL,
            message = "没有给 path。",
            impact = "文件没有被写入。",
            nextStep = "把 path 填成工作区内的相对路径，例如 notes/todo.md。",
        )

        val MISSING_CONTENT = QuineError(
            kind = ErrorKind.TOOL,
            message = "没有给 content。",
            impact = "文件没有被写入。",
            nextStep = "把要写入的完整文本放进 content。",
        )

        val SNAPSHOT_FAILED = QuineError(
            kind = ErrorKind.STORAGE,
            message = "改动前的快照没做成。",
            impact = "为了能回滚，这次没有写入。",
            nextStep = "检查存储空间后重试；快照是写入的前置条件，不能跳过。",
        )

        val CONFLICT = QuineError(
            kind = ErrorKind.TOOL,
            message = "这个文件在我读取之后被改过了。",
            impact = "为了不覆盖别人的改动，这次没有写入。",
            nextStep = "重新读一遍文件，在最新内容的基础上再改。",
        )

        fun notFound(path: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "工作区里没有 $path。",
            impact = "文件没有被写入。",
            nextStep = "确认路径拼写；新文件会自动创建，父目录不存在的除外。",
        )

        fun isDirectory(path: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "$path 是一个目录，不是文件。",
            impact = "文件没有被写入。",
            nextStep = "把 path 换成目录里的具体文件名。",
        )

        private val SchemaJson = Json { ignoreUnknownKeys = true }

        private val PARAMETERS: JsonObject = SchemaJson.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "相对工作区根的路径，例如 notes/todo.md" },
                "content": { "type": "string", "description": "文件的完整新内容（会覆盖原文）" }
              },
              "required": ["path", "content"],
              "additionalProperties": false
            }
            """.trimIndent(),
        ).jsonObject
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
