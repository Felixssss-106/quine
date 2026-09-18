package com.quine.core.tools.builtin

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.tools.CapabilityLevel
import com.quine.core.tools.RiskLevel
import com.quine.core.tools.Tool
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolResult
import com.quine.core.tools.ToolSpec
import com.quine.core.tools.workspace.ReadOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * `fs_read`（agent-prompt.md §3.9）：读工作区里的文本文件。
 *
 * 契约：`{path, offset?, limit?}`，path 为相对工作区根的路径；
 * 超过 [MAX_BYTES] 截断，且**截断必须带引用**（`sourceRef`）。
 */
class FsReadTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        id = ID,
        title = "读文件",
        description = "读取工作区里的一个文本文件。path 是相对工作区根的路径，例如 notes/todo.md。" +
            "文件很大时用 offset 和 limit 分段读。",
        parameters = PARAMETERS,
        risk = RiskLevel.SAFE,
        capability = CapabilityLevel.STANDARD,
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val startedAt = context.time.nowMillis()
        val path = (args["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        val offset = ((args["offset"] as? JsonPrimitive)?.longOrNull ?: 0L).coerceAtLeast(0L)
        val requested = (args["limit"] as? JsonPrimitive)?.intOrNull ?: MAX_BYTES
        val limit = requested.coerceIn(1, MAX_BYTES)

        val workspace = context.workspace
        val outcome = workspace.read(relativePath = path, offsetBytes = offset, maxBytes = limit)
        val duration = context.time.nowMillis() - startedAt

        return when (outcome) {
            is ReadOutcome.Ok -> {
                val end = offset + outcome.text.toByteArray(Charsets.UTF_8).size
                ToolResult(
                    toolId = ID,
                    ok = true,
                    output = buildString {
                        append("[").append(workspace.label).append("] ").append(path)
                        append(" — 共 ").append(outcome.totalBytes).append(" 字节")
                        append('\n')
                        if (outcome.truncated) {
                            append("（已截断：本次只返回前 ").append(limit).append(" 字节，")
                            append("继续读请带 offset=").append(end).append("）\n")
                        }
                        append('\n')
                        append(outcome.text)
                    },
                    sourceRef = "${workspace.label}:$path#bytes:$offset-$end",
                    truncated = outcome.truncated,
                    durationMillis = duration,
                )
            }

            ReadOutcome.NotFound -> failure(
                duration = duration,
                error = QuineError(
                    kind = ErrorKind.TOOL,
                    message = "工作区里没有 $path 这个文件。",
                    impact = "文件没有被读取。",
                    nextStep = "确认路径拼写；或者先读工作区根目录看看有哪些文件。",
                ),
            )

            ReadOutcome.IsDirectory -> failure(
                duration = duration,
                error = QuineError(
                    kind = ErrorKind.TOOL,
                    message = "$path 是一个目录，不是文件。",
                    impact = "文件没有被读取。",
                    nextStep = "把 path 换成目录里的具体文件名。",
                ),
            )

            is ReadOutcome.Failed -> failure(duration = duration, error = outcome.error)
        }
    }

    private fun failure(duration: Long, error: QuineError): ToolResult = ToolResult(
        toolId = ID,
        ok = false,
        output = error.message + error.nextStep,
        error = error,
        durationMillis = duration,
    )

    companion object {
        const val ID = "fs_read"

        /** 单次读取上限：64 KB。超出即截断并带引用。 */
        const val MAX_BYTES = 64 * 1024

        private val SchemaJson = Json { ignoreUnknownKeys = true }

        private val PARAMETERS: JsonObject = SchemaJson.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "相对工作区根的路径，例如 notes/todo.md"
                },
                "offset": {
                  "type": "integer",
                  "description": "从第几个字节开始读，默认 0"
                },
                "limit": {
                  "type": "integer",
                  "description": "最多读多少字节，默认 65536，上限也是 65536"
                }
              },
              "required": ["path"],
              "additionalProperties": false
            }
            """.trimIndent(),
        ).jsonObject
    }
}
