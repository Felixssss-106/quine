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

class FsDiffToolTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var workspace: PrivateWorkspace
    private val tool = FsDiffTool()

    @Before
    fun setUp() {
        workspace = PrivateWorkspace(temp.root)
        File(temp.root, "notes").mkdirs()
    }

    private fun context() = ToolContext(workspace = workspace, snapshots = DenyingSnapshots)

    private fun args(path: String? = null, content: String? = null): JsonObject = buildJsonObject {
        path?.let { put("path", it) }
        content?.let { put("content", it) }
    }

    private fun diff(path: String? = null, content: String? = null): ToolResult = runBlocking {
        tool.execute(args(path, content), context())
    }

    private fun file(path: String) = File(temp.root, path)

    @Test
    fun 预览改动并显示增删() {
        file("notes/todo.md").writeText("- 买牛奶\n- 写周报\n")

        val result = diff("notes/todo.md", "- 买牛奶\n- 写周报\n- 浇花\n")

        assertTrue(result.ok)
        assertTrue(result.output.contains("+1"))
        // 不写死前缀后有没有空格：那是 TextDiff.render 的契约，由它自己的测试钉着
        assertTrue(result.output.lines().any { it.startsWith("+") && it.contains("浇花") })
    }

    @Test
    fun 删除的行用减号标出来() {
        file("notes/todo.md").writeText("- 买牛奶\n- 写周报\n")

        val result = diff("notes/todo.md", "- 买牛奶\n")

        assertTrue(result.output.contains("−1"))
        assertTrue(result.output.lines().any { it.startsWith("-") && it.contains("写周报") })
    }

    /** 这条是整个工具存在的理由：预览就是预览，一个字节都不许改。 */
    @Test
    fun 只预览不改文件() {
        val original = "- 买牛奶\n- 写周报\n"
        file("notes/todo.md").writeText(original)

        val result = diff("notes/todo.md", "完全不一样的内容\n")

        assertTrue(result.ok)
        assertEquals(original, file("notes/todo.md").readText())
    }

    @Test
    fun 文件不存在时整篇算新增() {
        val result = diff("notes/new.md", "- 第一条\n")

        assertTrue(result.ok)
        assertTrue(result.output.contains("+2") || result.output.contains("+1"))
        assertFalse(file("notes/new.md").exists())
    }

    @Test
    fun 内容一样就说没差别() {
        val content = "- 买牛奶\n"
        file("notes/todo.md").writeText(content)

        val result = diff("notes/todo.md", content)

        assertTrue(result.ok)
        assertTrue(result.output.contains("没有差别"))
    }

    @Test
    fun 缺参数给出人话错误() {
        val noPath = diff(content = "内容")
        assertFalse(noPath.ok)
        assertTrue(noPath.output.contains("path"))

        val noContent = diff(path = "notes/todo.md")
        assertFalse(noContent.ok)
        assertTrue(noContent.output.contains("content"))
    }

    @Test
    fun 目录不能拿来diff() {
        val result = diff("notes", "内容")

        assertFalse(result.ok)
        assertTrue(result.output.contains("目录"))
    }

    @Test
    fun 结果带引用说明比对的是哪个文件() {
        file("notes/todo.md").writeText("旧\n")

        val result = diff("notes/todo.md", "新\n")

        assertTrue(result.sourceRef!!.contains("notes/todo.md"))
    }

    @Test
    fun 中文改动也能正确比对() {
        file("notes/todo.md").writeText("- 记得买牛奶\n")

        val result = diff("notes/todo.md", "- 记得买豆浆\n")

        assertTrue(result.output.contains("买豆浆"))
        assertTrue(result.output.contains("买牛奶"))
    }

    @Test
    fun diff太长时截断并带引用() {
        val old = (1..500).joinToString("\n") { "旧行 $it" } + "\n"
        val new = (1..500).joinToString("\n") { "新行 $it" } + "\n"
        file("notes/big.md").writeText(old)

        val result = diff("notes/big.md", new)

        assertTrue(result.ok)
        assertTrue(result.truncated)
        assertTrue(result.sourceRef!!.isNotBlank())
        assertTrue(result.output.contains("只显示前"))
    }
}
