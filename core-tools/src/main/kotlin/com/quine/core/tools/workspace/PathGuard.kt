package com.quine.core.tools.workspace

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError

sealed interface PathCheck {
    data class Ok(val relativePath: String) : PathCheck

    data class Rejected(val error: QuineError) : PathCheck
}

/**
 * 路径守卫：把模型给的路径规整成「工作区内的相对路径」，拒绝一切逃逸写法。
 *
 * 拒绝规则（保守优先，宁可让模型改一次参数）：
 * 空路径 · 绝对路径（`/` 或 `\` 开头）· 含反斜杠 · 含 NUL · 任一段为 `..` 或 `.`。
 *
 * 注意：这里只做**字面**检查。符号链接逃逸由 [PrivateWorkspace] 的
 * `canonicalPath` 前缀校验兜底；SAF 侧因为是从根逐段查找，结构上无法逃逸。
 */
object PathGuard {

    fun normalize(raw: String): PathCheck {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return reject(
            message = "路径是空的。",
            impact = "文件没有被读取。",
            nextStep = "把 path 填成工作区内的相对路径，比如 notes/todo.md。",
        )
        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) return reject(
            message = "不接受绝对路径。",
            impact = "文件没有被读取。",
            nextStep = "改用相对工作区根的路径，比如 notes/todo.md。",
        )
        if (trimmed.contains('\\')) return reject(
            message = "路径里出现了反斜杠。",
            impact = "文件没有被读取。",
            nextStep = "用 / 分隔目录，比如 notes/todo.md。",
        )
        if (trimmed.any { it == '\u0000' }) return reject(
            message = "路径里含有非法字符。",
            impact = "文件没有被读取。",
            nextStep = "把路径里的控制字符去掉再试。",
        )

        val segments = trimmed.split('/')
        if (segments.any { it == ".." || it == "." }) return reject(
            message = "路径里出现了 .. 或 . 这类跳转段。",
            impact = "文件没有被读取。",
            nextStep = "从工作区根开始写完整相对路径，不要用跳转段。",
        )

        val cleaned = segments.filter { it.isNotEmpty() }.joinToString("/")
        if (cleaned.isEmpty()) return reject(
            message = "路径是空的。",
            impact = "文件没有被读取。",
            nextStep = "把 path 填成工作区内的相对路径，比如 notes/todo.md。",
        )
        return PathCheck.Ok(cleaned)
    }

    private fun reject(message: String, impact: String, nextStep: String): PathCheck =
        PathCheck.Rejected(
            QuineError(kind = ErrorKind.TOOL, message = message, impact = impact, nextStep = nextStep),
        )
}
