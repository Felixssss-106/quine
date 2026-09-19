package com.quine.core.tools.builtin

import com.quine.core.common.ErrorKind
import com.quine.core.tools.FakeSnapshotter
import com.quine.core.tools.ToolContext
import com.quine.core.tools.workspace.PrivateWorkspace
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FsWriteToolTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var workspace: PrivateWorkspace
    private lateinit var snapshotter: FakeSnapshotter
    private val tool = FsWriteTool()

    @Before
    fun setUp() {
        workspace = PrivateWorkspace(temp.root)
        snapshotter = FakeSnapshotter()
    }

    private fun context() = ToolContext(workspace = workspace, snapshotter = snapshotter)

    private fun args(path: String?, content: String?): JsonObject = buildJsonObject {
        path?.let { put("path", it) }
        content?.let { put("content", it) }
    }

    private fun write(path: String, content: String) = runBlocking {
        tool.execute(args(path, content), context())
    }

    @Test
    fun 写新文件成功且没有快照() {
        val result = write("notes/new.md", "hello")

        assertTrue(result.ok)
        assertEquals("hello", File(temp.root, "notes/new.md").readText())
        assertTrue(snapshotter.captures.isEmpty())
    }

    @Test
    fun 覆盖已有文件前会先做快照() {
        File(temp.root, "a.md").writeText("旧内容")
        val result = write("a.md", "新内容")

        assertTrue(result.ok)
        assertEquals("新内容", File(temp.root, "a.md").readText())

        assertEquals(1, snapshotter.captures.size)
        assertEquals("a.md", snapshotter.captures[0].path)
        assertEquals("旧内容", snapshotter.captures[0].oldBytes.toString(Charsets.UTF_8))
        assertTrue(result.output.contains("可回滚"))
    }

    @Test
    fun 快照失败就拒绝写入_绝不留下撤不回的改动() {
        File(temp.root, "a.md").writeText("旧内容")
        snapshotter.failNext = true

        val result = write("a.md", "不该被写进去")

        assertFalse(result.ok)
        assertEquals("旧内容", File(temp.root, "a.md").readText())
        assertTrue(result.output.contains("快照"))
    }

    @Test
    fun 读取后被别人改过就拒绝覆盖() {
        File(temp.root, "a.md").writeText("模型看到的内容")

        // 捕获发生在「读」与「写」之间：在这里偷偷改文件 = 真实的并发竞争
        snapshotter.onCapture = { File(temp.root, "a.md").writeText("用户改过的内容") }

        val result = write("a.md", "模型的改动")

        assertFalse(result.ok)
        assertEquals(ErrorKind.TOOL, result.error?.kind)
        assertTrue(result.output.contains("被改过"))
        // 用户的改动必须保住
        assertEquals("用户改过的内容", File(temp.root, "a.md").readText())
    }

    @Test
    fun 缺参数给出人话错误() {
        val noPath = runBlocking { tool.execute(args(null, "内容"), context()) }
        assertFalse(noPath.ok)
        assertTrue(noPath.output.contains("path"))

        val noContent = runBlocking { tool.execute(args("a.md", null), context()) }
        assertFalse(noContent.ok)
        assertTrue(noContent.output.contains("content"))
    }

    @Test
    fun 路径逃逸被拒且文件没被创建() {
        assertFalse(write("../../evil.md", "x").ok)
        assertFalse(File(temp.root.parentFile, "evil.md").exists())
    }

    @Test
    fun 中文内容往返不丢字() {
        val content = "- 第一行\n- 第二行：记得买牛奶\n"
        write("notes/todo.md", content)
        assertEquals(content, File(temp.root, "notes/todo.md").readText())
    }

    @Test
    fun 结果里带上写的是哪个工作区() {
        val result = write("a.md", "x")
        assertTrue(result.sourceRef!!.startsWith(PrivateWorkspace.LABEL))
    }

    @Test
    fun 写入不留下临时文件() {
        write("a.md", "内容")
        val leftovers = temp.root.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.toList()
        assertTrue(leftovers.isEmpty())
    }
}
