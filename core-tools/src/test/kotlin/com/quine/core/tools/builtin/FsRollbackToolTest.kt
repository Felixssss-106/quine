package com.quine.core.tools.builtin

import com.quine.core.tools.FakeSnapshots
import com.quine.core.tools.SnapshotRef
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

class FsRollbackToolTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var workspace: PrivateWorkspace
    private lateinit var snapshots: FakeSnapshots
    private val writeTool = FsWriteTool()
    private val rollbackTool = FsRollbackTool()

    @Before
    fun setUp() {
        workspace = PrivateWorkspace(temp.root)
        snapshots = FakeSnapshots()
    }

    private fun context() = ToolContext(workspace = workspace, snapshots = snapshots)

    private fun file(path: String) = File(temp.root, path)

    private fun args(path: String? = null, snapshot: String? = null): JsonObject = buildJsonObject {
        path?.let { put("path", it) }
        snapshot?.let { put("snapshot", it) }
    }

    // 显式写返回类型：表达式体的返回类型要靠推断，而这两个辅助函数互相引用，
    // 不写会撞上 "Type checking has run into a recursive problem"。
    private fun write(path: String, content: String): ToolResult = runBlocking {
        writeTool.execute(
            buildJsonObject {
                put("path", path)
                put("content", content)
            },
            context(),
        )
    }

    private fun rollback(path: String? = null, snapshot: String? = null): ToolResult = runBlocking {
        rollbackTool.execute(args(path, snapshot), context())
    }

    @Test
    fun 回滚把文件恢复到改动前() {
        file("a.md").writeText("改动前")
        write("a.md", "改动后")
        assertEquals("改动后", file("a.md").readText())

        val result = rollback(path = "a.md")

        assertTrue(result.ok)
        assertEquals("改动前", file("a.md").readText())
        assertTrue(result.sourceRef!!.startsWith(PrivateWorkspace.LABEL))
    }

    @Test
    fun 按快照id回滚到指定的那一次() {
        file("a.md").writeText("第一版")
        write("a.md", "第二版") // 快照 snap-1 = 第一版
        write("a.md", "第三版") // 快照 snap-2 = 第二版

        val result = rollback(snapshot = "snap-1")

        assertTrue(result.ok)
        assertEquals("第一版", file("a.md").readText())
    }

    /** 回滚本身也是一次写入，所以它必须能撤回 —— 否则回滚错了就没救。 */
    @Test
    fun 回滚本身也能被回滚掉() {
        file("a.md").writeText("A")
        write("a.md", "B")

        assertTrue(rollback(path = "a.md").ok)
        assertEquals("A", file("a.md").readText())

        // 回滚前那份（B）已存成快照，再回滚一次就换回去
        assertTrue(rollback(path = "a.md").ok)
        assertEquals("B", file("a.md").readText())
    }

    @Test
    fun 回滚结果里给出撤回用的快照id() {
        file("a.md").writeText("旧")
        write("a.md", "新")

        val result = rollback(path = "a.md")

        assertTrue(result.output.contains("snap-"))
        assertTrue(result.output.contains("再回滚一次"))
    }

    /**
     * `path` 是相对路径，同名文件在不同工作区里是两个文件。
     * 拿另一个工作区的快照回滚 = 覆盖一个它不该碰的文件，必须拒绝。
     */
    @Test
    fun 别的Workspace的快照不接受回滚() {
        val mine = "本工作区的内容"
        file("a.md").writeText(mine)
        snapshots.seed(
            SnapshotRef(
                id = "snap-other",
                path = "a.md",
                blobRef = "blob/sha256/other",
                root = "授权目录",
                createdAt = 0L,
            ),
            "别的目录的内容".toByteArray(),
        )

        val result = rollback(snapshot = "snap-other")

        assertFalse(result.ok)
        assertEquals(mine, file("a.md").readText())
        assertTrue(result.output.contains("授权目录"))
    }

    @Test
    fun 快照内容不在了就明确失败且不动文件() {
        file("a.md").writeText("旧")
        write("a.md", "新")
        snapshots.loseBlobs = true

        val result = rollback(path = "a.md")

        assertFalse(result.ok)
        assertEquals("新", file("a.md").readText())
        assertTrue(result.output.contains("已经不在"))
    }

    @Test
    fun 回滚前存不下当前内容就不回滚() {
        file("a.md").writeText("旧")
        write("a.md", "新")
        snapshots.failNext = true

        val result = rollback(path = "a.md")

        assertFalse(result.ok)
        assertEquals("新", file("a.md").readText())
        assertTrue(result.output.contains("快照"))
    }

    @Test
    fun 没有快照的文件给出人话错误() {
        file("new.md").writeText("刚建的")

        val result = rollback(path = "new.md")

        assertFalse(result.ok)
        assertEquals("刚建的", file("new.md").readText())
        assertTrue(result.output.contains("没有可回滚的快照"))
    }

    @Test
    fun 两个参数都不给就报错而不是猜一个() {
        val result = runBlocking { rollbackTool.execute(buildJsonObject {}, context()) }

        assertFalse(result.ok)
        assertTrue(result.output.contains("path"))
    }

    @Test
    fun 快照id不存在时说明清楚() {
        val result = rollback(snapshot = "snap-404")

        assertFalse(result.ok)
        assertTrue(result.output.contains("snap-404"))
    }

    /** 被删掉的文件也能靠快照恢复 —— 回滚不该只服务"文件还在"的情况。 */
    @Test
    fun 文件被删了也能回滚回来() {
        file("a.md").writeText("原始内容")
        write("a.md", "改过的")
        file("a.md").delete()

        val result = rollback(path = "a.md")

        assertTrue(result.ok)
        assertEquals("原始内容", file("a.md").readText())
    }
}
