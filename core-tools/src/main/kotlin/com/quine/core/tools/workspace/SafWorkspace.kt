package com.quine.core.tools.workspace

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import java.io.IOException

/**
 * SAF 授权目录工作区。
 *
 * 路径解析一律从 tree 根**逐段 findFile**，因此结构上不可能逃逸出授权目录 ——
 * 这也是这里不需要 canonicalPath 校验的原因。
 */
class SafWorkspace(
    private val root: DocumentFile,
    private val resolver: ContentResolver,
) : Workspace {

    override val label: String = LABEL

    override suspend fun read(relativePath: String, offsetBytes: Long, maxBytes: Int): ReadOutcome {
        val target = resolve(relativePath) ?: return ReadOutcome.NotFound
        if (target.isDirectory) return ReadOutcome.IsDirectory

        return try {
            val input = resolver.openInputStream(target.uri)
                ?: return ReadOutcome.Failed(UNREADABLE)
            input.use { stream ->
                val outcome = LimitedReader.read(stream, offsetBytes, maxBytes)
                ReadOutcome.Ok(
                    text = LimitedReader.decode(outcome.bytes, outcome.truncated),
                    totalBytes = target.length(),
                    truncated = outcome.truncated,
                )
            }
        } catch (error: SecurityException) {
            ReadOutcome.Failed(REVOKED)
        } catch (error: IOException) {
            ReadOutcome.Failed(ioError(error))
        }
    }

    override fun readBytes(relativePath: String): ByteArray? {
        val target = resolve(relativePath) ?: return null
        if (!target.isFile) return null
        return runCatching {
            resolver.openInputStream(target.uri)?.use { it.readBytes() }
        }.getOrNull()
    }

    override fun writeBytes(
        relativePath: String,
        bytes: ByteArray,
        expectedOldBytes: ByteArray?,
    ): WriteOutcome {
        val segments = split(relativePath) ?: return WriteOutcome.Failed(ILLEGAL_PATH)
        if (segments.isEmpty()) return WriteOutcome.IsDirectory

        return try {
            var parent = root
            for (segment in segments.dropLast(1)) {
                val next = parent.findFile(segment)
                parent = when {
                    next == null -> parent.createDirectory(segment) ?: return WriteOutcome.Failed(UNWRITABLE)
                    next.isDirectory -> next
                    else -> return WriteOutcome.Failed(UNWRITABLE)
                }
            }

            val name = segments.last()
            val existing = parent.findFile(name)
            if (existing?.isDirectory == true) return WriteOutcome.IsDirectory

            if (expectedOldBytes != null && existing != null) {
                val current = runCatching {
                    resolver.openInputStream(existing.uri)?.use { it.readBytes() }
                }.getOrNull()
                if (current != null && !current.contentEquals(expectedOldBytes)) return WriteOutcome.Conflict
            }

            val target = existing
                ?: parent.createFile("application/octet-stream", name)
                ?: return WriteOutcome.Failed(UNWRITABLE)

            val output = resolver.openOutputStream(target.uri, "wt")
                ?: return WriteOutcome.Failed(UNWRITABLE)
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            WriteOutcome.Ok(bytes.size.toLong())
        } catch (error: SecurityException) {
            WriteOutcome.Failed(REVOKED)
        } catch (error: IOException) {
            WriteOutcome.Failed(ioError(error))
        }
    }

    override fun list(relativePath: String): List<WorkspaceEntry>? {
        val target = if (relativePath.isBlank()) root else resolve(relativePath) ?: return null
        if (!target.isDirectory) return emptyList()
        return target.listFiles()
            .map { WorkspaceEntry(it.name ?: "", it.isDirectory, if (it.isFile) it.length() else null) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    override fun delete(relativePath: String): Boolean {
        val target = resolve(relativePath) ?: return false
        return runCatching { target.delete() }.getOrDefault(false)
    }

    private fun split(relativePath: String): List<String>? = when (val checked = PathGuard.normalize(relativePath)) {
        is PathCheck.Rejected -> null
        is PathCheck.Ok -> checked.relativePath.split('/').filter { it.isNotEmpty() }
    }

    private fun resolve(relativePath: String): DocumentFile? {
        val segments = split(relativePath) ?: return null
        var current: DocumentFile = root
        for (segment in segments) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    companion object {
        const val LABEL = "授权目录"

        /**
         * 从持久化的 tree Uri 还原一个工作区。
         *
         * 放在这里而不是 app 层，是为了让 `DocumentFile` 只出现在 core-tools 内部 ——
         * app 只需要说「用这个 uri」，不需要认识 SAF 的类型。
         *
         * @return 解析失败（授权被系统回收、uri 失效）返回 null，由调用方回落私有工作区。
         */
        fun fromTreeUri(context: android.content.Context, treeUri: String): SafWorkspace? {
            val appContext = context.applicationContext
            val tree = runCatching {
                DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri))
            }.getOrNull() ?: return null
            return SafWorkspace(tree, appContext.contentResolver)
        }

        val ILLEGAL_PATH = QuineError(
            kind = ErrorKind.TOOL,
            message = "这个路径不被允许。",
            impact = "操作没有执行。",
            nextStep = "只用工作区内的相对路径，不要用跳转段。",
        )

        val REVOKED = QuineError(
            kind = ErrorKind.AUTH,
            message = "授权目录的权限已经失效。",
            impact = "操作没有执行。",
            nextStep = "去 设置 → 工作目录 重新选一次目录。",
        )

        val UNREADABLE = QuineError(
            kind = ErrorKind.STORAGE,
            message = "这个文件打不开。",
            impact = "操作没有执行。",
            nextStep = "确认它还是普通文件、没有被别的应用占用，然后重试。",
        )

        val UNWRITABLE = QuineError(
            kind = ErrorKind.STORAGE,
            message = "写不进这个目录。",
            impact = "改动没有保存。",
            nextStep = "确认授权目录仍然可写；不行就换一个目录。",
        )

        fun ioError(error: IOException) = QuineError(
            kind = ErrorKind.STORAGE,
            message = "文件读写失败。",
            impact = "这次操作没有完成。",
            nextStep = "确认文件还在、存储可写，然后重试。",
            cause = error,
        )
    }
}
