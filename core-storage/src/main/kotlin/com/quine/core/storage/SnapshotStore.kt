package com.quine.core.storage

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 快照的 blob 存储 —— **内容寻址**：同一内容只存一份，回滚就是把 blob 写回原路径。
 *
 * `blobRef` 的形式是 `blob/sha256/<hex>`（与 `SnapshotEntity.blobRef` 一致）；
 * 落盘位置是 `<root>/blob/<前2位>/<剩余>`，分目录避免单目录塞太多文件。
 *
 * 写入走「临时文件 + 重命名」，避免半截文件被当成有效 blob。
 */
class SnapshotStore(private val root: File) {

    init {
        root.mkdirs()
    }

    /** 存下内容，返回 blobRef。已存在则直接复用（内容寻址的收益）。 */
    @Throws(IOException::class)
    fun put(content: ByteArray): String {
        val hash = sha256Hex(content)
        val target = fileFor(hash)
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeBytes(content)
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("无法落盘快照内容：$hash")
            }
        }
        return REF_PREFIX + hash
    }

    /** 读回 blob 内容；不存在返回 null。 */
    fun content(blobRef: String): ByteArray? = fileForOrNull(blobRef)?.takeIf { it.isFile }?.readBytes()

    fun exists(blobRef: String): Boolean = fileForOrNull(blobRef)?.isFile == true

    fun delete(blobRef: String) {
        fileForOrNull(blobRef)?.delete()
    }

    /**
     * 清掉没有被引用的 blob。`keep` 通常是数据库里还存在的全部 `blobRef`。
     * 返回删除的数量。
     */
    fun prune(keep: Set<String>): Int {
        val dir = File(root, BLOB_DIR)
        if (!dir.isDirectory) return 0
        var removed = 0
        dir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val ref = refFor(file)
                if (ref == null || ref !in keep) {
                    if (file.delete()) removed++
                }
            }
        return removed
    }

    fun usageBytes(): Long {
        val dir = File(root, BLOB_DIR)
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun fileFor(hash: String): File =
        File(File(root, BLOB_DIR), "${hash.take(2)}${File.separator}${hash.drop(2)}")

    private fun fileForOrNull(blobRef: String): File? {
        val hash = hashOf(blobRef) ?: return null
        return fileFor(hash)
    }

    /** `blob/sha256/<hex>` → `<hex>`；格式不符返回 null。 */
    private fun hashOf(blobRef: String): String? {
        if (!blobRef.startsWith(REF_PREFIX)) return null
        val hash = blobRef.removePrefix(REF_PREFIX)
        if (hash.isEmpty() || hash.any { it !in HEX }) return null
        return hash
    }

    private fun refFor(file: File): String? {
        val hash = file.parentFile?.name.orEmpty() + file.name
        if (hash.length != SHA256_HEX_LENGTH || hash.any { it !in HEX }) return null
        return REF_PREFIX + hash
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val REF_PREFIX = "blob/sha256/"
        const val BLOB_DIR = "blob"
        const val HEX = "0123456789abcdef"
        const val SHA256_HEX_LENGTH = 64
    }
}
