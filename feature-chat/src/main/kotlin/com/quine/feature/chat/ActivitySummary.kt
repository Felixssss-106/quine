package com.quine.feature.chat

import com.quine.core.tools.builtin.FsDiffTool
import com.quine.core.tools.builtin.FsListTool
import com.quine.core.tools.builtin.FsReadTool
import com.quine.core.tools.builtin.FsRollbackTool
import com.quine.core.tools.builtin.FsSearchTool
import com.quine.core.tools.builtin.FsWriteTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 活动卡的一行摘要（agent-prompt.md §4.2）：一律**人话标题**，不许术语裸奔 ——
 * 「改了 notes/todo.md」而不是「fs_write({"path":"notes/todo.md"})」。
 *
 * 这里只取 `path` / `query` 等少量字段做标题所需，**不**解析 `content`（那是写工具的主战场，
 * 不应该出现在一行摘要里）。失败态只说「没成功」，具体原因在卡片展开态看 `event.output`。
 *
 * 「未来加新工具时显式补一条」也是原则 —— 宁可显示工具 id 也不要胡编一条人话。
 */
object ActivitySummary {

    private val json = Json { ignoreUnknownKeys = true }

    fun describe(
        toolId: String,
        argumentsJson: String,
        ok: Boolean,
        truncated: Boolean,
    ): String {
        val args = parseArgs(argumentsJson)
        val path = args["path"]?.takeIf { it.isNotBlank() }
        val query = args["query"]?.takeIf { it.isNotBlank() }
        val base = when (toolId) {
            FsReadTool.ID -> when {
                path == null -> if (ok) "读了一个文件" else "有个文件没读到"
                ok -> "读了 $path"
                else -> "没读到 $path"
            }

            FsWriteTool.ID -> when {
                path == null -> if (ok) "改了一个文件" else "没改成"
                ok -> "改了 $path"
                else -> "没改成 $path"
            }

            FsListTool.ID -> when {
                path == null -> if (ok) "列了工作区" else "没列到工作区"
                ok -> "列了 $path 下的内容"
                else -> "没列到 $path"
            }

            FsSearchTool.ID -> when {
                query == null -> if (ok) "搜了一次" else "搜没成"
                path == null -> if (ok) "在工作区里搜了 $query" else "没搜到 $query"
                ok -> "在 $path 里搜了 $query"
                else -> "没搜到 $query"
            }

            FsDiffTool.ID -> when {
                path == null -> if (ok) "预览了一次改动" else "没预览成"
                ok -> "预览了 $path 的改动"
                else -> "没预览成 $path"
            }

            FsRollbackTool.ID -> when {
                path == null -> if (ok) "回滚了一次" else "没回滚成"
                ok -> "回滚了 $path"
                else -> "没回滚成 $path"
            }

            else -> toolId
        }
        return if (truncated) "$base（内容太长，已截断）" else base
    }

    private fun parseArgs(argumentsJson: String): Map<String, String> {
        if (argumentsJson.isBlank()) return emptyMap()
        return runCatching {
            val obj: JsonObject = json.parseToJsonElement(argumentsJson).jsonObject
            buildMap {
                for ((key, value) in obj) {
                    // 摘要只用字符串字段（path / query）。数字 / 布尔被有意忽略。
                    runCatching {
                        val s = value.jsonPrimitive.content
                        if (s.isNotBlank()) put(key, s)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }
}