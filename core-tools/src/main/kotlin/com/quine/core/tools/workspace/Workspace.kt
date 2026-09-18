package com.quine.core.tools.workspace

import com.quine.core.common.QuineError

/**
 * 工作区：工具读写的根。
 *
 * 两个实现 —— [PrivateWorkspace]（应用私有目录，无需授权，纯 `java.io`）
 * 与 [SafWorkspace]（用户 SAF 授权的目录）。
 *
 * **依赖倒置**：`core-tools` 不认识 `core-storage`，当前生效的根由
 * [WorkspaceProvider] 在 app 层决定并注入。
 */
interface Workspace {
    /** 给人看的名字，会出现在工具结果的引用里：「授权目录」/「私有工作区」。 */
    val label: String

    /**
     * 读一个文本文件。
     *
     * @param relativePath 相对工作区根的路径（调用方已可假定是 [PathGuard] 通过的形式，
     *   实现仍需自行校验，因为它同时被单测直接调用）。
     * @param offsetBytes 起始偏移，0 表示从头。
     * @param maxBytes 最多读多少字节；超出即截断。
     */
    suspend fun read(relativePath: String, offsetBytes: Long = 0, maxBytes: Int): ReadOutcome
}

sealed interface ReadOutcome {
    /** @param totalBytes 文件总字节数（不是本次读到的字节数），用于给模型「还有多少没看到」。 */
    data class Ok(
        val text: String,
        val totalBytes: Long,
        val truncated: Boolean,
    ) : ReadOutcome

    data object NotFound : ReadOutcome

    data object IsDirectory : ReadOutcome

    data class Failed(val error: QuineError) : ReadOutcome
}

/** 由 app 层实现：决定当前用哪个工作区（授权目录优先，否则回落私有目录）。 */
interface WorkspaceProvider {
    suspend fun workspace(): Workspace
}
