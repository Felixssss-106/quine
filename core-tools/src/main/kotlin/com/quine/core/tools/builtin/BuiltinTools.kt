package com.quine.core.tools.builtin

import com.quine.core.tools.Tool

/**
 * 内置工具的**唯一清单**。app 层装配时用它，不要再逐个 `new`。
 *
 * 为什么要集中：分散在装配处逐个写，漏掉一个**既不会报错也不会被任何测试发现** ——
 * 工具写了、单测也过了，模型却永远看不到它（本项目真踩过：`fs_rollback` 就是这样
 * 漏注册的，直到检查装配处才发现）。集中到这里之后，完整性由 `BuiltinToolsTest` 守着。
 */
object BuiltinTools {

    val all: List<Tool> = listOf(
        FsReadTool(),
        FsWriteTool(),
        FsListTool(),
        FsSearchTool(),
        FsDiffTool(),
        FsRollbackTool(),
    )
}
