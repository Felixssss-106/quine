package com.quine.core.tools.builtin

import com.quine.core.common.ErrorKind
import com.quine.core.tools.ToolContext
import com.quine.core.tools.StubSnapshots
import com.quine.core.tools.workspace.PrivateWorkspace
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FsReadToolTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val tool = FsReadTool()
    private val json = Json { ignoreUnknownKeys = true }

    private fun context() = ToolContext(
        workspace = PrivateWorkspace(temp.root),
        snapshots = StubSnapshots,
    )

    private fun args(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    @Test
    fun `契约参数与文档一致`() {
        assertEquals("fs_read", tool.spec.id)
        assertEquals(64 * 1024, FsReadTool.MAX_BYTES)

        val properties = tool.spec.parameters["properties"]!!.jsonObject
        assertTrue(properties.containsKey("path"))
        assertTrue(properties.containsKey("offset"))
        assertTrue(properties.containsKey("limit"))
        assertTrue(tool.spec.parameters["required"].toString().contains("path"))
    }

    @Test
    fun `读小文件不截断`() = runTest {
        File(temp.root, "note.md").writeText("你好")
        val result = tool.execute(args("""{"path":"note.md"}"""), context())

        assertTrue(result.ok)
        assertTrue(result.output.contains("你好"))
        assertFalse(result.truncated)
        assertTrue(result.sourceRef!!.contains("note.md"))
    }

    @Test
    fun `大文件截断并带引用`() = runTest {
        File(temp.root, "big.txt").writeText("x".repeat(FsReadTool.MAX_BYTES + 500))
        val result = tool.execute(args("""{"path":"big.txt"}"""), context())

        assertTrue(result.ok)
        assertTrue(result.truncated)
        assertTrue("截断结果必须带引用", !result.sourceRef.isNullOrEmpty())
        assertTrue(result.output.contains("已截断"))
        assertTrue(result.output.contains("offset="))
    }

    @Test
    fun `limit 被夹在上限内`() = runTest {
        File(temp.root, "big.txt").writeText("x".repeat(FsReadTool.MAX_BYTES + 500))
        val result = tool.execute(args("""{"path":"big.txt","limit":99999999}"""), context())

        assertTrue(result.ok)
        assertTrue(result.truncated)
    }

    @Test
    fun `文件不存在给出人话错误`() = runTest {
        val result = tool.execute(args("""{"path":"nope.txt"}"""), context())
        assertFalse(result.ok)
        assertEquals(ErrorKind.TOOL, result.error?.kind)
        assertTrue(result.output.isNotEmpty())
    }

    @Test
    fun `目录给出人话错误`() = runTest {
        temp.newFolder("notes")
        val result = tool.execute(args("""{"path":"notes"}"""), context())
        assertFalse(result.ok)
        assertEquals(ErrorKind.TOOL, result.error?.kind)
    }

    @Test
    fun `路径逃逸被拒`() = runTest {
        val result = tool.execute(args("""{"path":"../../etc/passwd"}"""), context())
        assertFalse(result.ok)
        assertEquals(ErrorKind.TOOL, result.error?.kind)
    }

    @Test
    fun `结果里标明读的是哪个工作区`() = runTest {
        File(temp.root, "a.txt").writeText("hi")
        val result = tool.execute(args("""{"path":"a.txt"}"""), context())
        assertTrue(result.output.contains(PrivateWorkspace.LABEL))
    }
}
