package com.quine.core.tools.builtin

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.tools.CapabilityLevel
import com.quine.core.tools.RiskLevel
import com.quine.core.tools.Tool
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolResult
import com.quine.core.tools.ToolSpec
import com.quine.core.tools.diff.TextDiff
import com.quine.core.tools.workspace.ReadOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `fs_diff`：把「打算写进去的内容」和文件现在的内容做对比，把差别显示出来。
 *
 * 它就是 §3.1 那句红线的落点 —— **批量改动先给 diff 预览且可取消**。
 * 所以这里的要点是：**只预览，一个字节都不改**。确认没问题再让 `fs_write` 写。
 *
 * 差别怎么画由 `core-design` 的 diff 块负责（等宽、`+` 极浅底、`−` 灰字删除线、不用红绿）。
 * 工具只给文本，`+/−` 前缀是无障碍的保底通道（色弱 / 黑白模式 / 终端块里也能分辨）。
 */
class FsDiffTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        id = ID,
        title = "预览改动",
        description = "把「打算写进 path 的内容」和文件现在的内容做对比，把差别显示出来 " +
            "（+ 是新增，− 是删除）。**只预览，不改文件** —— 确认后再用 fs_write 写入。" +
            "文件还不存在时，整篇都算新增。",
        parameters = PARAMETERS,
        risk = RiskLevel.SAFE,
        capability = CapabilityLevel.STANDARD,
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val startedAt = context.time.nowMillis()

        val path = args.stringOrNull("path")
            ?: return failure(context, startedAt, MISSING_PATH)
        val content = args.stringOrNull("content")
            ?: return failure(context, startedAt, MISSING_CONTENT)

        val workspace = context.workspace
        val outcome = workspace.read(path, offsetBytes = 0, maxBytes = MAX_FILE_BYTES)

        val oldText = when (outcome) {
            is ReadOutcome.Ok -> outcome.text

            // 文件不存在 = 新建，旧内容是空的，整篇算新增
            ReadOutcome.NotFound -> ""

            ReadOutcome.IsDirectory -> return failure(context, startedAt, isDirectory(path))
            is ReadOutcome.Failed -> return failure(context, startedAt, outcome.error)
        }

        val lines = TextDiff.diff(oldText, content)
        val summary = TextDiff.summary(lines)
        val duration = context.time.nowMillis() - startedAt

        if (!summary.changed) {
            return ToolResult(
                toolId = ID,
                ok = true,
                output = "[${workspace.label}] $path — 和现在的内容没有差别，" +
                    "写不写都一样。",
                sourceRef = "${workspace.label}:$path#diff",
                durationMillis = duration,
            )
        }

        val truncated = lines.size > MAX_DIFF_LINES
        val shown = if (truncated) lines.take(MAX_DIFF_LINES) else lines

        return ToolResult(
            toolId = ID,
            ok = true,
            output = buildString {
                append("[${workspace.label}] $path — 预览：")
                append('+').append(summary.added).append(" −").append(summary.removed)
                append("（共 ").append(lines.size).append(" 行）\n")
                append(TextDiff.render(shown))
                if (truncated) {
                    append("（太长，只显示前 ").append(MAX_DIFF_LINES).append(" 行；")
                    append("想看全文用 fs_read。）\n")
                }
                if (outcome is ReadOutcome.Ok && outcome.truncated) {
                    append("（文件超过 64 KB，只比对了开头部分。）\n")
                }
            }.trimEnd(),
            sourceRef = "${workspace.label}:$path#diff",
            truncated = truncated,
            durationMillis = duration,
        )
    }

    private fun failure(context: ToolContext, startedAt: Long, error: QuineError): ToolResult = ToolResult(
        toolId = ID,
        ok = false,
        output = error.message + error.nextStep,
        error = error,
        durationMillis = context.time.nowMillis() - startedAt,
    )

    companion object {
        const val ID = "fs_diff"

        /** 与 `fs_read` 一致的单文件上限。 */
        const val MAX_FILE_BYTES = 64 * 1024

        /** diff 再长也只给人看前这么多行；完整差别用 fs_read 看原文。 */
        const val MAX_DIFF_LINES = 400

        val MISSING_PATH = QuineError(
            kind = ErrorKind.TOOL,
            message = "没有给 path。",
            impact = "没有生成预览。",
            nextStep = "把 path 填成工作区内的相对路径，例如 notes/todo.md。",
        )

        val MISSING_CONTENT = QuineError(
            kind = ErrorKind.TOOL,
            message = "没有给 content。",
            impact = "没有生成预览。",
            nextStep = "把打算写进去的完整内容放进 content。",
        )

        fun isDirectory(path: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "$path 是一个目录，不是文件。",
            impact = "没有生成预览。",
            nextStep = "把 path 换成目录里的具体文件名。",
        )

        private val SchemaJson = Json { ignoreUnknownKeys = true }

        private val PARAMETERS: JsonObject = SchemaJson.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "要改的文件，相对工作区根的路径" },
                "content": { "type": "string", "description": "打算写进去的完整新内容（只预览，不会写入）" }
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
