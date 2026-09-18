package com.quine.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySummaryTest {

    @Test
    fun `读成功说人话`() {
        val summary = ActivitySummary.describe(
            toolId = "fs_read",
            argumentsJson = "{\"path\":\"notes/todo.md\"}",
            ok = true,
            truncated = false,
        )
        assertEquals("读了 notes/todo.md", summary)
    }

    @Test
    fun `读失败也带路径`() {
        val summary = ActivitySummary.describe(
            toolId = "fs_read",
            argumentsJson = "{\"path\":\"nope.txt\"}",
            ok = false,
            truncated = false,
        )
        assertEquals("没读到 nope.txt", summary)
    }

    @Test
    fun `截断会额外说明`() {
        val summary = ActivitySummary.describe(
            toolId = "fs_read",
            argumentsJson = "{\"path\":\"big.log\"}",
            ok = true,
            truncated = true,
        )
        assertTrue(summary.startsWith("读了 big.log"))
        assertTrue(summary.contains("截断"))
    }

    @Test
    fun `参数坏掉时不崩 退回到模糊说法`() {
        assertEquals(
            "读了一个文件",
            ActivitySummary.describe("fs_read", "{不是 JSON", ok = true, truncated = false),
        )
        assertEquals(
            "有个文件没读到",
            ActivitySummary.describe("fs_read", "", ok = false, truncated = false),
        )
    }

    @Test
    fun `未知工具退回工具名而不是编造`() {
        assertEquals(
            "fs_write",
            ActivitySummary.describe("fs_write", "{}", ok = true, truncated = false),
        )
    }
}
