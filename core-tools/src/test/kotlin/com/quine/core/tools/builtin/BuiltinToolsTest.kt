package com.quine.core.tools.builtin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守 `BuiltinTools` 的完整性。
 *
 * 为什么值得一个测试：工具漏注册是一个**静默失败** —— 编译得过、单测也过得，
 * 只是模型永远看不到那个工具（`fs_rollback` 就漏过一次）。
 * 所以这里把「该有的都在」和「没有重名」钉住。
 */
class BuiltinToolsTest {

    @Test
    fun 内置工具清单包含全部工具() {
        val ids = BuiltinTools.all.map { it.spec.id }.toSet()

        assertEquals(
            setOf(
                FsReadTool.ID,
                FsWriteTool.ID,
                FsListTool.ID,
                FsSearchTool.ID,
                FsDiffTool.ID,
                FsRollbackTool.ID,
            ),
            ids,
        )
    }

    @Test
    fun 没有重名的工具() {
        val ids = BuiltinTools.all.map { it.spec.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun 每个工具都带给人看的参数说明() {
        for (tool in BuiltinTools.all) {
            assertTrue(
                "${tool.spec.id} 的 description 是空的，模型不知道该什么时候用它",
                tool.spec.description.isNotBlank(),
            )
            assertTrue(
                "${tool.spec.id} 的 title 是空的",
                tool.spec.title.isNotBlank(),
            )
        }
    }
}
