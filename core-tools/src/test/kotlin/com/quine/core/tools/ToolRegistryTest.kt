package com.quine.core.tools

import com.quine.core.common.ErrorKind
import com.quine.core.common.TimeProvider
import com.quine.core.tools.builtin.FsReadTool
import com.quine.core.tools.workspace.PrivateWorkspace
import com.quine.core.tools.workspace.Workspace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ToolRegistryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val fixedTime = TimeProvider { 1_000L }

    private fun registry(): ToolRegistry = ToolRegistry(listOf(FsReadTool()))

    private fun context(workspace: Workspace = PrivateWorkspace(temp.root)) =
        ToolContext(workspace = workspace, snapshotter = StubSnapshotter, time = fixedTime)

    @Test
    fun `暴露给模型的 schema 与工具声明一致`() {
        val schemas = registry().schemas()
        assertEquals(1, schemas.size)
        assertEquals(FsReadTool.ID, schemas.first().id)
    }

    @Test
    fun `未知工具不抛异常而是回灌错误`() = runTest {
        val result = registry().execute("fs_write", "{}", context())
        assertFalse(result.ok)
        assertEquals(ErrorKind.TOOL, result.error?.kind)
        assertTrue(result.output.isNotEmpty())
    }

    @Test
    fun `参数不是合法 JSON`() = runTest {
        val result = registry().execute(FsReadTool.ID, "{not json", context())
        assertFalse(result.ok)
        assertTrue(result.output.contains("JSON"))
    }

    @Test
    fun `参数缺必填字段被拦在工具之外`() = runTest {
        val result = registry().execute(FsReadTool.ID, "{}", context())
        assertFalse(result.ok)
        assertTrue(result.output.contains("path"))
    }

    @Test
    fun `参数含未知字段被拦`() = runTest {
        val result = registry().execute(FsReadTool.ID, """{"path":"a.txt","nope":1}""", context())
        assertFalse(result.ok)
        assertTrue(result.output.contains("nope"))
    }

    @Test
    fun `取消后不再执行`() = runTest {
        File(temp.root, "a.txt").writeText("hello")
        val cancelledContext = ToolContext(
            workspace = PrivateWorkspace(temp.root),
            snapshotter = StubSnapshotter,
            cancelled = { true },
            time = fixedTime,
        )
        val result = registry().execute(FsReadTool.ID, """{"path":"a.txt"}""", cancelledContext)
        assertFalse(result.ok)
        assertEquals(ErrorKind.CANCELLED, result.error?.kind)
    }

    @Test
    fun `正常读取并带回引用`() = runTest {
        File(temp.root, "a.txt").writeText("hello")
        val result = registry().execute(FsReadTool.ID, """{"path":"a.txt"}""", context())
        assertTrue(result.ok)
        assertTrue(result.output.contains("hello"))
        assertNotNull(result.sourceRef)
        assertTrue(result.sourceRef!!.contains("a.txt"))
        assertFalse(result.truncated)
    }
}
