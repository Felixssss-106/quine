package com.quine.core.tools.workspace

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import java.io.File
import java.io.IOException

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
        val file = resolve(relativePath) ?: return ReadOutcome.Failed(ILLEGAL_PATH)
        if (!file.exists()) return ReadOutcome.NotFound
        if (file.isDirectory) return ReadOutcome.IsDirectory

        return try {
            file.inputStream().use { input ->
                val outcome = LimitedReader.read(input, offsetBytes, maxBytes)
                ReadOutcome.Ok(
                    text = LimitedReader.decode(outcome.bytes, outcome.truncated),
                    totalBytes = file.length(),
                    truncated = outcome.truncated,
                )
            }
        } catch (error: IOException) {
            ReadOutcome.Failed(ioError(error))
        }
    }

    override fun readBytes(relativePath: String): ByteArray? {
        val file = resolve(relativePath) ?: return null
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    /**
     * 写入。走「临时文件 + 重命名」，写到一半崩了也不会留下半截文件 ——
     * 半截文件比写失败更糟，因为它看起来是有效的。
     */
    override fun writeBytes(
        relativePath: String,
        bytes: ByteArray,
        expectedOldBytes: ByteArray?,
    ): WriteOutcome {
        val file = resolve(relativePath) ?: return WriteOutcome.Failed(ILLEGAL_PATH)
        if (file.isDirectory) return WriteOutcome.IsDirectory

        if (expectedOldBytes != null && file.exists()) {
            val current = runCatching { file.readBytes() }.getOrNull()
            if (current != null && !current.contentEquals(expectedOldBytes)) return WriteOutcome.Conflict
        }

        return try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return WriteOutcome.Failed(
                    QuineError(
                        kind = ErrorKind.STORAGE,
                        message = "写不进这个文件。",
                        impact = "改动没有保存。",
                        nextStep = "检查存储空间与权限后重试。",
                    ),
                )
            }
            WriteOutcome.Ok(bytes.size.toLong())
        } catch (error: IOException) {
            WriteOutcome.Failed(ioError(error))
        }
    }

    override fun list(relativePath: String): List<WorkspaceEntry>? {
        // 空路径 = 工作区根。PathGuard 会拒掉空串，所以这里单独放行。
        val file = if (relativePath.isBlank()) root else resolve(relativePath) ?: return null
        if (!file.exists()) return null
        if (!file.isDirectory) return emptyList()
        return file.listFiles()
            ?.map { WorkspaceEntry(it.name, it.isDirectory, if (it.isFile) it.length() else null) }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    override fun delete(relativePath: String): Boolean {
        val file = resolve(relativePath) ?: return false
        return runCatching { file.deleteRecursively() }.getOrDefault(false)
    }

    /**
     * 路径守卫 + 越界检查。所有入口都走这里，避免某个方法漏掉校验。
     * 返回 null 表示路径不合法或指向工作区之外。
     */
    private fun resolve(relativePath: String): File? {
        val normalized = when (val checked = PathGuard.normalize(relativePath)) {
            is PathCheck.Rejected -> return null
            is PathCheck.Ok -> checked.relativePath
        }
        val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return null
        val target = runCatching { File(root, normalized).canonicalFile }.getOrNull() ?: return null
        val inside = target.path == rootPath || target.path.startsWith(rootPath + File.separator)
        return if (inside) target else null
    }

    companion object {
        const val LABEL = "私有工作区"

        /** 私有工作区的根目录名，app 层据此创建。 */
        const val DIRECTORY_NAME = "workspace"

        val ILLEGAL_PATH = QuineError(
            kind = ErrorKind.TOOL,
            message = "这个路径不被允许。",
            impact = "操作没有执行。",
            nextStep = "只用工作区内的相对路径，不要用跳转段或链接。",
        )

        fun ioError(error: IOException) = QuineError(
            kind = ErrorKind.STORAGE,
            message = "文件读不出来。",
            impact = "这次操作没有完成。",
            nextStep = "确认文件还在、存储可写，然后重试。",
            cause = error,
        )
    }
}
