package com.quine.core.tools.builtin

import com.quine.core.tools.DenyingSnapshots
import com.quine.core.tools.ToolContext
import com.quine.core.tools.ToolResult
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

class FsSearchToolTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var workspace: PrivateWorkspace
    private val tool = FsSearchTool()

    @Before
    fun setUp() {
        workspace = PrivateWorkspace(temp.root)
        File(temp.root, "notes").mkdirs()
        File(temp.root, "src").mkdirs()
        File(temp.root, "notes/todo.md").writeText("- 第一行\n- 第二行：记得买牛奶\n")
        File(temp.root, "notes/other.md").writeText("买牛奶\n别的\n")
        File(temp.root, "src/a.txt").writeText("Milk 牛奶\ncode\n")
    }

    private fun context() = ToolContext(workspace = workspace, snapshots = DenyingSnapshots)

    private fun args(
        query: String? = null,
        path: String? = null,
        caseSensitive: Boolean? = null,
        maxResults: Int? = null,
    ): JsonObject = buildJsonObject {
        query?.let { put("query", it) }
        path?.let { put("path", it) }
        caseSensitive?.let { put("caseSensitive", it) }
        maxResults?.let { put("maxResults", it) }
    }

    private fun search(
        query: String? = null,
        path: String? = null,
        caseSensitive: Boolean? = null,
        maxResults: Int? = null,
    ): ToolResult = runBlocking {
        tool.execute(args(query, path, caseSensitive, maxResults), context())
    }

    @Test
    fun 搜到内容并带上路径与行号() {
        val result = search("买牛奶")

        assertTrue(result.ok)
        assertTrue(result.output.contains("notes/todo.md:2"))
        assertTrue(result.output.contains("notes/other.md:1"))
    }

    @Test
    fun 限定目录时只搜那个目录() {
        val result = search("牛奶", path = "notes")

        assertTrue(result.ok)
        assertTrue(result.output.contains("notes/todo.md"))
        assertFalse(result.output.contains("src/a.txt"))
    }

    @Test
    fun 默认不区分大小写() {
        assertTrue(search("milk").output.contains("src/a.txt"))
    }

    @Test
    fun 要求区分大小写时就搜不到了() {
        val result = search("milk", caseSensitive = true)

        assertTrue(result.ok)
        assertFalse(result.output.contains("src/a.txt"))
    }

    @Test
    fun 二进制文件跳过不搜() {
        val binary = File(temp.root, "src/logo.bin")
        binary.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x00, 0x00) + "买牛奶".toByteArray())

        val result = search("买牛奶")

        assertTrue(result.ok)
        assertFalse(result.output.contains("logo.bin"))
    }

    @Test
    fun 到上限就说还有更多且带引用() {
        val result = search("牛奶", maxResults = 2)

        assertTrue(result.ok)
        assertTrue(result.truncated)
        assertTrue(result.output.contains("还有更多"))
        // 截断必须带引用（agent-prompt.md §2.3），否则模型不知道被省略了什么
        assertTrue(result.sourceRef!!.contains("search"))
    }

    @Test
    fun 搜不到也明确说清楚而不是假装成功() {
        val result = search("这个词肯定不存在")

        assertTrue(result.ok)
        assertTrue(result.output.contains("没有找到"))
    }

    @Test
    fun 缺query给出人话错误() {
        val result = search()

        assertFalse(result.ok)
        assertTrue(result.output.contains("query"))
    }

    @Test
    fun 目录不存在时报错() {
        val result = search("买牛奶", path = "nope")

        assertFalse(result.ok)
        assertTrue(result.output.contains("nope"))
    }

    @Test
    fun 结果里说明是在哪个工作区搜的() {
        assertTrue(search("买牛奶").output.startsWith("[${PrivateWorkspace.LABEL}]"))
    }

    @Test
    fun 长行会被截断但不会丢掉命中位置() {
        File(temp.root, "notes/long.md").writeText("买牛奶" + "很长的内容".repeat(100))
        val result = search("买牛奶")

        assertTrue(result.output.contains("notes/long.md:1"))
        assertTrue(result.output.contains("…"))
    }

    @Test
    fun 空query等同于没给() {
        assertFalse(search("   ").ok)
    }

    @Test
    fun 搜索不会改动任何文件() {
        val before = File(temp.root, "notes/todo.md").readText()
        search("买牛奶")
        assertEquals(before, File(temp.root, "notes/todo.md").readText())
    }
}
