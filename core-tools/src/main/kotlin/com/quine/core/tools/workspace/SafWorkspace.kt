package com.quine.core.tools.workspace

import android.content.ContentResolver
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
        val normalized = when (val checked = PathGuard.normalize(relativePath)) {
            is PathCheck.Rejected -> return ReadOutcome.Failed(checked.error)
            is PathCheck.Ok -> checked.relativePath
        }

        val target = resolve(normalized) ?: return ReadOutcome.NotFound
        if (target.isDirectory) return ReadOutcome.IsDirectory

        return try {
            val input = resolver.openInputStream(target.uri)
                ?: return ReadOutcome.Failed(
                    QuineError(
                        kind = ErrorKind.STORAGE,
                        message = "这个文件打不开。",
                        impact = "文件没有被读取。",
                        nextStep = "确认它还是普通文件、没有被别的应用占用，然后重试。",
                    ),
                )
            input.use { stream ->
                val outcome = LimitedReader.read(stream, offsetBytes, maxBytes)
                ReadOutcome.Ok(
                    text = LimitedReader.decode(outcome.bytes, outcome.truncated),
                    totalBytes = target.length(),
                    truncated = outcome.truncated,
                )
            }
        } catch (error: SecurityException) {
            ReadOutcome.Failed(
                QuineError(
                    kind = ErrorKind.AUTH,
                    message = "授权目录的权限已经失效。",
                    impact = "文件没有被读取。",
                    nextStep = "去 设置 → 工作目录 重新选一次目录。",
                    cause = error,
                ),
            )
        } catch (error: IOException) {
            ReadOutcome.Failed(
                QuineError(
                    kind = ErrorKind.STORAGE,
                    message = "文件读不出来。",
                    impact = "文件没有被读取。",
                    nextStep = "确认文件还在、存储可写，然后重试。",
                    cause = error,
                ),
            )
        }
    }

    private fun resolve(path: String): DocumentFile? {
        var current: DocumentFile = root
        for (segment in path.split('/')) {
            if (segment.isEmpty()) continue
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
                DocumentFile.fromTreeUri(appContext, android.net.Uri.parse(treeUri))
            }.getOrNull() ?: return null
            return SafWorkspace(tree, appContext.contentResolver)
        }
    }
}
