package com.quine.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySummaryTest {

    @Test fun `fs_read 成功显示路径`() {
        assertEquals(
            "读了 notes/todo.md",
            ActivitySummary.describe("fs_read", """{"path":"notes/todo.md"}""", ok = true, truncated = false),
        )
    }

    @Test fun `fs_read 失败显示没读到`() {
        assertEquals(
            "没读到 notes/todo.md",
            ActivitySummary.describe("fs_read", """{"path":"notes/todo.md"}""", ok = false, truncated = false),
        )
    }

    @Test fun `fs_read 截断会追加说明`() {
        val summary = ActivitySummary.describe(
            "fs_read", """{"path":"big.log"}""", ok = true, truncated = true,
        )
        assertTrue(summary.startsWith("读了 big.log"))
        assertTrue(summary.contains("截断"))
    }

    @Test fun `fs_write 成功显示改了`() {
        assertEquals(
            "改了 notes/todo.md",
            ActivitySummary.describe("fs_write", """{"path":"notes/todo.md"}""", ok = true, truncated = false),
        )
    }

    @Test fun `fs_write 失败显示没改成`() {
        assertEquals(
            "没改成 notes/todo.md",
            ActivitySummary.describe("fs_write", """{"path":"notes/todo.md"}""", ok = false, truncated = false),
        )
    }

    @Test fun `fs_list 有路径显列了路径`() {
        assertEquals(
            "列了 docs 下的内容",
            ActivitySummary.describe("fs_list", """{"path":"docs"}""", ok = true, truncated = false),
        )
    }

    @Test fun `fs_list 空路径显列了工作区`() {
        assertEquals(
            "列了工作区",
            ActivitySummary.describe("fs_list", """{"path":""}""", ok = true, truncated = false),
        )
    }

    @Test fun `fs_search 带路径显在 X 里搜了 Y`() {
        assertEquals(
            "在 docs 里搜了 TODO",
            ActivitySummary.describe("fs_search", """{"path":"docs","query":"TODO"}""", ok = true, truncated = false),
        )
    }

    @Test fun `fs_search 不带路径显在工作区里搜了`() {
        assertEquals(
            "在工作区里搜了 TODO",
            ActivitySummary.describe("fs_search", """{"query":"TODO"}""", ok = true, truncated = false),
        )
    }

    @Test fun `fs_search 失败显没搜到`() {
        assertEquals(
            "没搜到 TODO",
            ActivitySummary.describe("fs_search", """{"path":"docs","query":"TODO"}""", ok = false, truncated = false),
        )
    }

    @Test fun `fs_diff 显预览式成功`() {
        assertEquals(
            "预览了 notes/todo.md 的改动",
            ActivitySummary.describe("fs_diff", """{"path":"notes/todo.md"}""", ok = true, truncated = false),
        )
    }

    @Test fun `fs_rollback 显回滚了`() {
        assertEquals(
            "回滚了 notes/todo.md",
            ActivitySummary.describe("fs_rollback", """{"path":"notes/todo.md"}""", ok = true, truncated = false),
        )
    }

    @Test fun `未知工具 id 不伪装_直接落回 id`() {
        // 「不假装支持」原则：未列出的工具显示 id 本身，而不是胡编人话。
        assertEquals(
            "shell_run",
            ActivitySummary.describe("shell_run", """{"cmd":"ls"}""", ok = true, truncated = false),
        )
    }

    @Test fun `坏 JSON 不崩_走最简回退`() {
        assertEquals(
            "读了一个文件",
            ActivitySummary.describe("fs_read", "{ 这不是 json", ok = true, truncated = false),
        )
    }

    @Test fun `空字符串参数也能跑`() {
        assertEquals(
            "读了一个文件",
            ActivitySummary.describe("fs_read", "", ok = true, truncated = false),
        )
    }

    @Test fun `非字符串字段被忽略_不影响字符串字段`() {
        // caseSensitive 是布尔：JSON 解析时类型不同，应当被忽略，不抛异常。
        val result = ActivitySummary.describe(
            "fs_search",
            """{"query":"TODO","caseSensitive":true,"maxResults":50}""",
            ok = true,
            truncated = false,
        )
        assertEquals("在工作区里搜了 TODO", result)
    }
}