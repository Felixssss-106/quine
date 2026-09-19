package com.quine.core.sandbox

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * tar.gz 解压。**不引第三方库**：gzip 用 JDK 的 `GZIPInputStream`，
 * tar 自己按 512 字节头解析（格式很老也很简单，一百来行就够）。
 *
 * 三条刻意的取舍：
 * - **跳过设备节点与 FIFO**（typeflag `3/4/6`）：非 root 的 Android 上根本建不出来，
 *   proot 也不需要真的设备节点。
 * - **符号链接建不出来只记 warning，不失败**：它在这台机器的 JVM 上可能没权限
 *   （Windows 建软链要特权），但在 Android 上是正常的 —— 不能因为测试环境就把解压判死。
 * - **路径穿越一律挡掉**：`../` 或绝对路径的条目直接跳过，绝不解到目标目录外面去。
 */
object TarGz {

    data class Result(
        val files: Int = 0,
        val dirs: Int = 0,
        val symlinks: Int = 0,
        val skipped: Int = 0,
        val warnings: List<String> = emptyList(),
    )

    fun extract(gzipStream: InputStream, targetDir: File): Result {
        targetDir.mkdirs()
        val root = targetDir.canonicalFile

        var files = 0
        var dirs = 0
        var symlinks = 0
        var skipped = 0
        val warnings = mutableListOf<String>()

        val block = ByteArray(BLOCK)

        GZIPInputStream(gzipStream).use { gz ->
            var pendingName: String? = null
            var pendingLink: String? = null

            while (readBlock(gz, block)) {
                if (isZero(block)) break
                // 头坏了就停手：往下解析会把后面的数据当成条目，解出一堆垃圾。
                if (!checksumOk(block)) throw java.io.IOException("tar 头校验失败，文件可能已损坏")

                val rawName = readString(block, 0, 100)
                val size = readNumeric(block, SIZE_OFFSET, SIZE_LENGTH)
                val typeFlag = block[TYPE_OFFSET].toInt().toChar()
                val rawLink = readString(block, LINK_OFFSET, LINK_LENGTH)

                val name = pendingName ?: rawName
                val link = pendingLink ?: rawLink
                pendingName = null
                pendingLink = null

                when (typeFlag) {
                    // GNU 长名：这一条的数据块是下一条真正的名字 / 链接目标
                    'L' -> {
                        pendingName = readData(gz, size)
                        continue
                    }

                    'K' -> {
                        pendingLink = readData(gz, size)
                        continue
                    }

                    // PAX 扩展头：rootfs 用不到里面的东西，跳过
                    'x', 'g' -> {
                        skip(gz, size)
                        continue
                    }
                }

                val target = resolveInside(root, name)
                if (target == null) {
                    skipped++
                    warnings += "跳过可疑路径：$name"
                    skip(gz, size)
                    continue
                }

                when (typeFlag) {
                    '0', ' ', '\u0000' -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> copy(gz, out, size) }
                        skipPadding(gz, size)
                        files++
                    }

                    '5' -> {
                        target.mkdirs()
                        skip(gz, size)
                        dirs++
                    }

                    '2' -> {
                        target.parentFile?.mkdirs()
                        if (link.isBlank() || !createLink(target, link)) {
                            skipped++
                            warnings += "软链建不出来，按普通文件跳过：$name -> $link"
                        } else {
                            symlinks++
                        }
                        skip(gz, size)
                    }

                    else -> {
                        // 硬链接、设备节点、FIFO：非 root 环境下建不了也不需要
                        skipped++
                        skip(gz, size)
                    }
                }
            }
        }

        return Result(
            files = files,
            dirs = dirs,
            symlinks = symlinks,
            skipped = skipped,
            warnings = warnings,
        )
    }

    /** 软链建不出来不要抛：测试环境（Windows）常常没这个权限。 */
    private fun createLink(link: File, target: String): Boolean = runCatching {
        if (link.exists()) link.delete()
        Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(target))
        true
    }.getOrDefault(false)

    /**
     * 把条目名解析成 `root` 内的文件。**返回 null = 不许解**（绝对路径、`..` 逃逸、空名）。
     */
    private fun resolveInside(root: File, name: String): File? {
        val cleaned = name.replace('\\', '/')
        val stack = ArrayDeque<String>()
        for (part in cleaned.split('/')) {
            when {
                part.isEmpty() || part == "." -> Unit
                part == ".." -> if (stack.isEmpty()) return null else stack.removeLast()
                else -> stack += part
            }
        }
        if (stack.isEmpty()) return null

        var file = root
        for (part in stack) file = File(file, part)

        return runCatching {
            val canonical = file.canonicalPath
            val base = root.canonicalPath
            if (canonical == base || canonical.startsWith(base + File.separator)) file else null
        }.getOrNull()
    }

    private fun readBlock(stream: InputStream, block: ByteArray): Boolean {
        var off = 0
        while (off < block.size) {
            val n = stream.read(block, off, block.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun isZero(block: ByteArray): Boolean = block.all { it == 0.toByte() }

    /**
     * tar 头自带校验和：把整个头当无符号字节求和，其中校验字段本身按空格算。
     * 不校验的话，坏掉的 tarball 会被"顺利"解出一堆垃圾文件。
     */
    private fun checksumOk(block: ByteArray): Boolean {
        var sum = 0L
        for (i in block.indices) {
            sum += if (i in CHECKSUM_OFFSET until CHECKSUM_OFFSET + CHECKSUM_LENGTH) {
                ' '.code.toLong()
            } else {
                block[i].toLong() and 0xff
            }
        }
        return readNumeric(block, CHECKSUM_OFFSET, CHECKSUM_LENGTH) == sum
    }

    private fun readString(block: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val last = offset + length
        while (end < last && block[end] != 0.toByte()) end++
        return String(block, offset, end - offset, Charsets.UTF_8)
    }

    /** tar 里的数字是八进制字符串；特别大的用 GNU 的 base-256（首字节最高位为 1）。 */
    private fun readNumeric(block: ByteArray, offset: Int, length: Int): Long {
        if ((block[offset].toInt() and 0x80) != 0) {
            var value = (block[offset].toInt() and 0x7f).toLong()
            for (i in 1 until length) {
                value = (value shl 8) or (block[offset + i].toLong() and 0xff)
            }
            return value
        }
        var value = 0L
        var started = false
        for (i in 0 until length) {
            val c = block[offset + i].toInt().toChar()
            when {
                c in '0'..'7' -> {
                    value = value * 8 + (c - '0')
                    started = true
                }

                c == ' ' || c == '\u0000' -> if (started) break
            }
        }
        return value
    }

    private fun padded(size: Long): Long = (size + BLOCK - 1) / BLOCK * BLOCK

    private fun skip(stream: InputStream, size: Long) {
        var remaining = padded(size)
        val buf = ByteArray(8 * 1024)
        while (remaining > 0) {
            val n = stream.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
            if (n < 0) break
            remaining -= n
        }
    }

    /** 已经写了 `size` 字节，把这一条剩下的 padding 吃掉。 */
    private fun skipPadding(stream: InputStream, size: Long) {
        var remaining = padded(size) - size
        val buf = ByteArray(8 * 1024)
        while (remaining > 0) {
            val n = stream.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
            if (n < 0) break
            remaining -= n
        }
    }

    private fun copy(from: InputStream, to: OutputStream, size: Long) {
        var remaining = size
        val buf = ByteArray(8 * 1024)
        while (remaining > 0) {
            val n = from.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
            if (n < 0) break
            to.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun readData(stream: InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        var off = 0
        while (off < bytes.size) {
            val n = stream.read(bytes, off, bytes.size - off)
            if (n < 0) break
            off += n
        }
        skipPadding(stream, size)
        return String(bytes, 0, off, Charsets.UTF_8).trim { it == '\u0000' || it.isWhitespace() }
    }

    private const val BLOCK = 512
    private const val SIZE_OFFSET = 124
    private const val SIZE_LENGTH = 12
    private const val TYPE_OFFSET = 156
    private const val LINK_OFFSET = 157
    private const val LINK_LENGTH = 100
    private const val CHECKSUM_OFFSET = 148
    private const val CHECKSUM_LENGTH = 8
}
