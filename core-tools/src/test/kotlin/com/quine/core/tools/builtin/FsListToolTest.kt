package com.quine.core.tools.builtin

import com.quine.core.tools.DenyingSnapshotter
import com.quine.core.tools.ToolContext
import com.quine.core.tools.workspace.PrivateWorkspace
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FsListToolTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var workspace: PrivateWorkspace
    private val tool = FsListTool()

    @Before
    fun setUp() {
        workspace = PrivateWorkspace(temp.root)
    }

    private fun list(path: String?) = runBlocking {
        tool.execute(
            buildJsonObject { path?.let { put("path", it) } },
            ToolContext(workspace = workspace, snapshotter = DenyingSnapshotter),
        )
    }

    @Test
    fun 列根目录_目录排在前面且按名排序() {
        File(temp.root, "b.md").writeText("b")
        File(temp.root, "notes").mkdirs()
        File(temp.root, "a.txt").writeText("a")

        val result = list(null)
        assertTrue(result.ok)
        val output = result.output
        assertTrue(output.contains("notes/"))
        assertTrue(output.indexOf("notes/") < output.indexOf("a.txt"))
        assertTrue(output.indexOf("a.txt") < output.indexOf("b.md"))
    }

    @Test
    fun 列子目录() {
        File(temp.root, "notes").mkdirs()
        File(temp.root, "notes/todo.md").writeText("x")
        File(temp.root, "notes/sub").mkdirs()

        val result = list("notes")
        assertTrue(result.ok)
        assertTrue(result.output.contains("todo.md"))
        assertTrue(result.output.contains("sub/"))
    }

    @Test
    fun 目录不存在给出人话错误() {
        val result = list("没有这个目录")
        assertFalse(result.ok)
        assertTrue(result.output.contains("没有"))
    }

    @Test
    fun 空目录也能列() {
        File(temp.root, "empty").mkdirs()
        val result = list("empty")
        assertTrue(result.ok)
        assertTrue(result.output.contains("空目录"))
    }

    @Test
    fun 列一个文件返回空而不是报错() {
        File(temp.root, "a.md").writeText("x")
        val result = list("a.md")
        assertTrue(result.ok)
    }

    @Test
    fun 路径逃逸被拒() {
        assertFalse(list("../../etc").ok)
    }

    @Test
    fun 条目带大小() {
        File(temp.root, "notes").mkdirs()
        File(temp.root, "notes/a.md").writeText("12345")

        val result = list("notes")
        assertTrue(result.output.contains("5 字节"))
    }
}
