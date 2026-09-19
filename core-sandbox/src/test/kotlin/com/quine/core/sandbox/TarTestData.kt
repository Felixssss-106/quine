package com.quine.core.sandbox

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * 测试用的 tar 打包器。
 *
 * 为什么自己写：要能造出**正常打包工具造不出来的东西** —— 逃逸路径（`../evil`）、
 * 绝对路径、设备节点、坏掉的校验和。这些正是解压器要挡住的情况。
 */
object TarTestData {

    class Entry(
        val name: String,
        val content: String? = null,
        val type: Char = '0',
        val link: String = "",
    )

    fun file(name: String, content: String) = Entry(name, content, '0')
    fun dir(name: String) = Entry(name, null, '5')
    fun symlink(name: String, target: String) = Entry(name, null, '2', target)

    fun gz(vararg entries: Entry, corruptChecksum: Boolean = false): ByteArray {
        val raw = ByteArrayOutputStream()
        for (entry in entries) {
            val payload = entry.content?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            raw.write(header(entry, payload.size.toLong(), corruptChecksum))
            raw.write(payload)
            val padding = (512 - payload.size % 512) % 512
            raw.write(ByteArray(padding.toInt()))
        }
        // tar 的结尾：两个全零块
        raw.write(ByteArray(1024))

        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw.toByteArray()) }
        return out.toByteArray()
    }

    /** 一个最小但"像样"的 rootfs：有 /bin/sh 和 /bin/busybox，能通过工具链自检。 */
    fun miniRootfs(): ByteArray = gz(
        dir("bin"),
        file("bin/sh", "#!/bin/busybox sh\n"),
        file("bin/busybox", "not-really-a-binary\n"),
        dir("usr"),
        dir("usr/bin"),
        file("usr/bin/env", "#!/bin/busybox env\n"),
        file("etc/passwd", "root:x:0:0:root:/root:/bin/sh\n"),
    )

    private fun header(entry: Entry, size: Long, corrupt: Boolean): ByteArray {
        val block = ByteArray(512)
        writeString(block, entry.name, 0, 100)
        writeOctal(block, 0b111_111_111L, 100, 8) // mode 0777
        writeOctal(block, 0, 108, 8) // uid
        writeOctal(block, 0, 116, 8) // gid
        writeOctal(block, size, 124, 12)
        writeOctal(block, 0, 136, 12) // mtime
        // 校验字段先填空格，算完再写回
        repeat(8) { block[148 + it] = ' '.code.toByte() }
        block[156] = entry.type.code.toByte()
        writeString(block, entry.link, 157, 100)
        writeString(block, "ustar", 257, 6)
        writeString(block, "00", 263, 2)

        var sum = 0L
        for (byte in block) sum += byte.toLong() and 0xff
        if (corrupt) sum += 1
        writeOctal(block, sum, 148, 6)
        block[154] = 0
        block[155] = ' '.code.toByte()
        return block
    }

    private fun writeString(block: ByteArray, value: String, offset: Int, length: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        for (i in 0 until minOf(bytes.size, length)) block[offset + i] = bytes[i]
    }

    private fun writeOctal(block: ByteArray, value: Long, offset: Int, length: Int) {
        val text = value.toString(8).padStart(length - 1, '0')
        writeString(block, text, offset, length - 1)
        block[offset + length - 1] = 0
    }
}
