package com.quine.core.tools.workspace

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import java.io.File

/**
 * 私有工作区：`context.filesDir/workspace`。
 *
 * 无需任何授权即可用，因此它同时是「用户拒绝 SAF 授权」时的回落根
 * （**回落必须有可见说明，不得静默** —— 由 UI 负责）。
 * 纯 `java.io`，可以在 JVM 单测里直接跑。
 */
class PrivateWorkspace(private val root: File) : Workspace {

    override val label: String = LABEL

    override suspend fun read(relativePath: String, offsetBytes: Long, maxBytes: Int): ReadOutcome {
        val normalized = when (val checked = PathGuard.normalize(relativePath)) {
            is PathCheck.Rejected -> return ReadOutcome.Failed(checked.error)
            is PathCheck.Ok -> checked.relativePath
        }

        val rootPath = runCatching { root.canonicalPath }.getOrNull()
            ?: return ReadOutcome.Failed(
                QuineError(
                    kind = ErrorKind.STORAGE,
                    message = "工作区目录打不开。",
                    impact = "文件没有被读取。",
                    nextStep = "检查应用存储空间后重试。",
                ),
            )

        val target = runCatching { File(root, normalized).canonicalFile }.getOrNull()
            ?: return ReadOutcome.Failed(
                QuineError(
                    kind = ErrorKind.TOOL,
                    message = "这个路径解析不了。",
                    impact = "文件没有被读取。",
                    nextStep = "换一个工作区内的相对路径再试。",
                ),
            )

        // 符号链接 / 大小写差异都可能让 canonicalPath 逃出根目录，这里统一兜底。
        val insideRoot = target.path == rootPath || target.path.startsWith(rootPath + File.separator)
        if (!insideRoot) {
            return ReadOutcome.Failed(
                QuineError(
                    kind = ErrorKind.TOOL,
                    message = "这个路径跑到工作区外面去了。",
                    impact = "文件没有被读取。",
                    nextStep = "只用工作区内的相对路径，不要用跳转段或链接。",
                ),
            )
        }

        if (!target.exists()) return ReadOutcome.NotFound
        if (target.isDirectory) return ReadOutcome.IsDirectory

        return try {
            target.inputStream().use { input ->
                val outcome = LimitedReader.read(input, offsetBytes, maxBytes)
                ReadOutcome.Ok(
                    text = LimitedReader.decode(outcome.bytes, outcome.truncated),
                    totalBytes = target.length(),
                    truncated = outcome.truncated,
                )
            }
        } catch (error: java.io.IOException) {
            ReadOutcome.Failed(
                QuineError(
                    kind = ErrorKind.STORAGE,
                    message = "文件读不出来。",
                    impact = "文件没有被读取。",
                    nextStep = "确认文件还在、还有读取权限，然后重试。",
                    cause = error,
                ),
            )
        }
    }

    companion object {
        const val LABEL = "私有工作区"

        /** 私有工作区的根目录名，app 层据此创建。 */
        const val DIRECTORY_NAME = "workspace"
    }
}
