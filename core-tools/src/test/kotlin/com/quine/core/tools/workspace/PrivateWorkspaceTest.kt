package com.quine.core.tools.workspace

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrivateWorkspaceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun workspace() = PrivateWorkspace(temp.root)

    @Test
    fun `读到文件内容`() = runTest {
        val notes = temp.newFolder("notes")
        File(notes, "todo.md").writeText("第一行\n第二行\n")

        val outcome = workspace().read("notes/todo.md", maxBytes = 64 * 1024)
        assertTrue(outcome is ReadOutcome.Ok)
        val ok = outcome as ReadOutcome.Ok
        assertEquals("第一行\n第二行\n", ok.text)
        assertFalse(ok.truncated)
    }

    @Test
    fun `超过上限即截断并标记`() = runTest {
        val content = "a".repeat(200)
        File(temp.root, "big.txt").writeText(content)

        val outcome = workspace().read("big.txt", maxBytes = 64) as ReadOutcome.Ok
        assertTrue(outcome.truncated)
        assertEquals(64, outcome.text.length)
        assertEquals(200L, outcome.totalBytes)
    }

    @Test
    fun `offset 从中间开始读`() = runTest {
        File(temp.root, "data.txt").writeText("0123456789")

        val outcome = workspace().read("data.txt", offsetBytes = 4, maxBytes = 3) as ReadOutcome.Ok
        assertEquals("456", outcome.text)
        assertTrue(outcome.truncated)
    }

    @Test
    fun `截断落在多字节字符中间时不吐半个字`() = runTest {
        // 每个汉字 3 字节；限 5 字节 → 只能完整容纳 1 个汉字
        File(temp.root, "cn.txt").writeText("你好世界")

        val outcome = workspace().read("cn.txt", maxBytes = 5) as ReadOutcome.Ok
        assertTrue(outcome.truncated)
        assertEquals("你", outcome.text)
    }

    @Test
    fun `文件不存在`() = runTest {
        assertEquals(ReadOutcome.NotFound, workspace().read("nope.txt", maxBytes = 1024))
    }

    @Test
    fun `目录不是文件`() = runTest {
        temp.newFolder("notes")
        assertEquals(ReadOutcome.IsDirectory, workspace().read("notes", maxBytes = 1024))
    }

    @Test
    fun `跳转段被拒`() = runTest {
        val outcome = workspace().read("../outside.txt", maxBytes = 1024)
        assertTrue(outcome is ReadOutcome.Failed)
    }

    @Test
    fun `工作区标签会出现在结果里`() = runTest {
        assertEquals(PrivateWorkspace.LABEL, workspace().label)
    }
}
