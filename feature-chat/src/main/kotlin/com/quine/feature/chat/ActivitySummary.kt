package com.quine.feature.chat

import com.quine.core.tools.builtin.FsReadTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 活动卡的一行摘要（agent-prompt.md §4.2）：一律**人话标题**，不许术语裸奔 ——
 * 「读了 notes/todo.md」而不是「fs_read({"path":"notes/todo.md"})」。
 */
object ActivitySummary {

    private val json = Json { ignoreUnknownKeys = true }

    fun describe(
        toolId: String,
        argumentsJson: String,
        ok: Boolean,
        truncated: Boolean,
    ): String {
        val path = pathOf(argumentsJson)
        val base = when (toolId) {
            FsReadTool.ID -> when {
                path == null -> if (ok) "读了一个文件" else "有个文件没读到"
                ok -> "读了 $path"
                else -> "没读到 $path"
            }

            else -> toolId
        }
        return if (truncated) "$base（内容太长，已截断）" else base
    }

    private fun pathOf(argumentsJson: String): String? {
        if (argumentsJson.isBlank()) return null
        return runCatching {
            val obj: JsonObject = json.parseToJsonElement(argumentsJson).jsonObject
            obj["path"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
