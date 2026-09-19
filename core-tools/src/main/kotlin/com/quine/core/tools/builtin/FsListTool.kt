package com.quine.core.tools.builtin

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.tools.Tool
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolResult
import com.quine.core.tools.ToolSpec
import com.quine.core.tools.workspace.WorkspaceEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `fs_list`：列工作区里的一个目录。 */
class FsListTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        id = ID,
        title = "列目录",
        description = "列出工作区某个目录下的条目。path 留空表示工作区根目录。",
        parameters = PARAMETERS,
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val startedAt = context.time.nowMillis()

        // path 可选：留空 = 根目录
        val path = this@FsListTool.let {
            args["path"]?.jsonPrimitive?.content?.trim().orEmpty()
        }

        val workspace = context.workspace
        val entries: List<WorkspaceEntry>? = workspace.list(path)

        if (entries == null) {
            return fail(context, startedAt, notFound(path))
        }
        if (entries.isEmpty()) {
            return ToolResult(
                toolId = ID,
                ok = true,
                output = "[${workspace.label}] ${path.ifBlank { "." }} — 空目录",
                sourceRef = "${workspace.label}:${path.ifBlank { "." }}",
                durationMillis = context.time.nowMillis() - startedAt,
            )
        }

        return ToolResult(
            toolId = ID,
            ok = true,
            output = buildString {
                appendLine("[${workspace.label}] ${path.ifBlank { "." }}")
                for (entry in entries) {
                    val suffix = if (entry.isDirectory) "/" else ""
                    val size = entry.size?.let { "  ($it 字节)" } ?: ""
                    appendLine("- ${entry.name}$suffix$size")
                }
            }.trimEnd(),
            sourceRef = "${workspace.label}:${path.ifBlank { "." }}",
            durationMillis = context.time.nowMillis() - startedAt,
        )
    }

    private fun fail(context: ToolContext, startedAt: Long, error: QuineError): ToolResult = ToolResult(
        toolId = ID,
        ok = false,
        output = error.message + error.nextStep,
        error = error,
        durationMillis = context.time.nowMillis() - startedAt,
    )

    companion object {
        const val ID = "fs_list"

        fun notFound(path: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "工作区里没有 $path 这个目录。",
            impact = "没有列出内容。",
            nextStep = "先用 fs_list 列根目录看看有哪些东西。",
        )

        private val SchemaJson = Json { ignoreUnknownKeys = true }

        private val PARAMETERS: JsonObject = SchemaJson.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "path": { "type": "string", "description": "目录的相对路径；留空表示工作区根" }
              },
              "required": [],
              "additionalProperties": false
            }
            """.trimIndent(),
        ).jsonObject
    }
}
