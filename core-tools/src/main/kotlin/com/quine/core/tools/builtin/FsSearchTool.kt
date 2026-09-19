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
import kotlinx.serialization.json.jsonPrimitive

/**
 * `fs_search`（agent-prompt.md §3.9）：在工作区里按内容找东西。
 *
 * 文档 §3.1 写的是「搜索（沙箱内 ripgrep）」—— **沙箱（`core-sandbox`）还没落地**，
 * 这里先用纯 Kotlin 在工作区里逐行扫描，能力对齐、范围更小（只覆盖工作区，不是全盘）。
 * 等沙箱就绪、工作区搬到沙箱里之后换成 ripgrep，工具契约不变。
 *
 * 两条自律：
 * - **跳过二进制**：图片、压缩包之类的文件内容搜了也没用，还会把结果搅乱。
 * - **截断必须带引用**（§2.3）：到上限就说还有更多，并告诉模型怎么缩小范围。
 */
class FsSearchTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        id = ID,
        title = "搜索内容",
        description = "在工作区的文件里搜索一段文字，返回「文件路径:行号:内容」。默认不区分大小写。" +
            "path 留空表示搜整个工作区根。",
        parameters = PARAMETERS,
        risk = RiskLevel.SAFE,
        capability = CapabilityLevel.STANDARD,
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val startedAt = context.time.nowMillis()

        val query = args.stringOrNull("query")
            ?: return failure(context, startedAt, MISSING_QUERY)
        val root = args["path"]?.jsonPrimitive?.content?.trim().orEmpty()
        val caseSensitive = (args["caseSensitive"] as? JsonPrimitive)?.content.toBoolean()
        val maxResults = ((args["maxResults"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_MAX_RESULTS)
            .coerceIn(1, MAX_RESULTS)

        val workspace = context.workspace
        val scope = root.ifBlank { "." }

        val entries = workspace.list(root)
        val files: List<String> = when {
            // 不存在
            entries == null -> return failure(context, startedAt, notFound(root))
            // 目录：递归收集
            entries.isNotEmpty() -> collectFiles(workspace, root)
            // 空目录或单个文件：就当它是文件，搜不到也无害
            else -> listOf(root)
        }

        val matches = mutableListOf<String>()
        var hitLimit = false
        var partialFile = false
        var scanned = 0

        for (file in files) {
            if (matches.size >= maxResults || scanned >= MAX_FILES) {
                hitLimit = true
                break
            }
            scanned++

            // 二进制跳过：只探前几个字节，够用了
            val head = workspace.readBytes(file) ?: continue
            if (looksBinary(head)) continue

            val outcome = workspace.read(file, offsetBytes = 0, maxBytes = MAX_FILE_BYTES)
            if (outcome !is ReadOutcome.Ok) continue
            if (outcome.truncated) partialFile = true

            outcome.text.lineSequence().forEachIndexed { index, line ->
                if (matches.size < maxResults && contains(line, query, caseSensitive)) {
                    matches += "$file:${index + 1}: ${clip(line)}"
                }
            }
        }

        val duration = context.time.nowMillis() - startedAt
        val sourceRef = "${workspace.label}:$scope#search:$query"

        if (matches.isEmpty()) {
            return ToolResult(
                toolId = ID,
                ok = true,
                output = "[${workspace.label}] $scope — 没有找到「$query」。",
                sourceRef = sourceRef,
                durationMillis = duration,
            )
        }

        return ToolResult(
            toolId = ID,
            ok = true,
            output = buildString {
                append("[${workspace.label}] $scope — 找到 ${matches.size} 处「$query」\n")
                for (match in matches) {
                    append("- ").append(match).append('\n')
                }
                if (hitLimit) {
                    append("（到上限了，还有更多没列出来：换个更具体的词，或用 path 缩小范围。）\n")
                }
                if (partialFile) {
                    append("（有文件超过 64 KB，只搜了它的开头部分。）\n")
                }
            }.trimEnd(),
            sourceRef = sourceRef,
            truncated = hitLimit,
            durationMillis = duration,
        )
    }

    private suspend fun collectFiles(workspace: com.quine.core.tools.workspace.Workspace, root: String): List<String> {
        val result = mutableListOf<String>()
        val stack = ArrayDeque<String>()
        stack += root
        while (stack.isNotEmpty()) {
            if (result.size >= MAX_FILES) break
            val dir = stack.removeFirst()
            val entries = workspace.list(dir) ?: continue
            for (entry in entries) {
                if (result.size >= MAX_FILES) break
                val child = if (dir.isBlank()) entry.name else "${dir}/${entry.name}"
                if (entry.isDirectory) stack += child else result += child
            }
        }
        return result
    }

    private fun failure(context: ToolContext, startedAt: Long, error: QuineError): ToolResult = ToolResult(
        toolId = ID,
        ok = false,
        output = error.message + error.nextStep,
        error = error,
        durationMillis = context.time.nowMillis() - startedAt,
    )

    companion object {
        const val ID = "fs_search"

        /** 单个文件最多读这么多字节（与 `fs_read` 一致）。 */
        const val MAX_FILE_BYTES = 64 * 1024

        const val DEFAULT_MAX_RESULTS = 50
        const val MAX_RESULTS = 200

        /** 遍历上限：工作区再大也不该把工具卡死。 */
        const val MAX_FILES = 2_000

        const val MAX_LINE = 160

        val MISSING_QUERY = QuineError(
            kind = ErrorKind.TOOL,
            message = "没有给 query。",
            impact = "没有搜索。",
            nextStep = "把要找的文字放进 query，例如要找的词或一句话的片段。",
        )

        fun notFound(path: String) = QuineError(
            kind = ErrorKind.TOOL,
            message = "工作区里没有 $path 这个目录。",
            impact = "没有搜索。",
            nextStep = "用 fs_list 看看有哪些目录；path 留空就搜整个工作区。",
        )

        private fun contains(line: String, query: String, caseSensitive: Boolean): Boolean =
            if (caseSensitive) line.contains(query) else line.contains(query, ignoreCase = true)

        /** 含 NUL 字节的基本都是二进制，不搜。 */
        private fun looksBinary(bytes: ByteArray): Boolean {
            val limit = minOf(bytes.size, 8_192)
            for (i in 0 until limit) {
                if (bytes[i] == 0.toByte()) return true
            }
            return false
        }

        private fun clip(line: String): String {
            val trimmed = line.trim()
            return if (trimmed.length > MAX_LINE) trimmed.take(MAX_LINE) + "…" else trimmed
        }

        private val SchemaJson = Json { ignoreUnknownKeys = true }

        private val PARAMETERS: JsonObject = SchemaJson.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "要搜索的文字" },
                "path": { "type": "string", "description": "限定在某个目录下搜索；留空表示整个工作区" },
                "caseSensitive": { "type": "boolean", "description": "是否区分大小写，默认 false" },
                "maxResults": { "type": "integer", "description": "最多返回多少条，默认 50，上限 200" }
              },
              "required": ["query"],
              "additionalProperties": false
            }
            """.trimIndent(),
        ).jsonObject
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
