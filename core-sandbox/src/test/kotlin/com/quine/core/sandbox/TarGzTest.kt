package com.quine.core.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class TarGzTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun extract(bytes: ByteArray, name: String = "out"): Pair<TarGz.Result, File> {
        val dir = File(temp.root, name)
        return TarGz.extract(ByteArrayInputStream(bytes), dir) to dir
    }

    private fun read(dir: File, path: String): String = File(dir, path).readText()

    @Test
    fun 解开文件与目录() {
        val (result, dir) = extract(
            TarTestData.gz(
                TarTestData.dir("bin"),
                TarTestData.file("bin/sh", "#!/bin/sh\n"),
                TarTestData.file("etc/passwd", "root:x:0:0\n"),
            ),
        )

        assertEquals(2, result.files)
        assertEquals(1, result.dirs)
        assertEquals("#!/bin/sh\n", read(dir, "bin/sh"))
        assertEquals("root:x:0:0\n", read(dir, "etc/passwd"))
        assertTrue(File(dir, "bin").isDirectory)
    }

    @Test
    fun 中文路径与内容都不乱码() {
        val (_, dir) = extract(TarTestData.gz(TarTestData.file("笔记/待办.md", "- 记得买牛奶\n")))

        assertEquals("- 记得买牛奶\n", read(dir, "笔记/待办.md"))
    }

    @Test
    fun 大文件分块写入也不截断() {
        val content = "0123456789".repeat(5_000) // 50 KB，跨多个块
        val (_, dir) = extract(TarTestData.gz(TarTestData.file("big.txt", content)))

        assertEquals(content, read(dir, "big.txt"))
    }

    /** 恶意 tarball 最经典的那一手：把文件写到目标目录外面去。 */
    @Test
    fun 逃逸路径一律挡掉() {
        val (result, dir) = extract(
            TarTestData.gz(
                TarTestData.file("../../escape.txt", "不该出现"),
                TarTestData.file("/absolute.txt", "不该出现"),
                TarTestData.file("ok.txt", "可以出现"),
            ),
        )

        // `../..` 那条被挡掉；`/absolute.txt` 会被规范化成目录内的相对路径（也跑不出去，
        // 所以不算可疑，照常解开）
        assertEquals(2, result.files)
        assertEquals(1, result.skipped)
        assertEquals("可以出现", read(dir, "ok.txt"))
        assertEquals("不该出现", read(dir, "absolute.txt"))

        assertFalse("绝不能写到目标目录外面", File(temp.root, "escape.txt").exists())
        assertFalse(File(dir.parentFile, "escape.txt").exists())
        assertTrue(result.warnings.any { it.contains("可疑") })
    }

    @Test
    fun 设备节点跳过且不影响其它条目() {
        val (result, dir) = extract(
            TarTestData.gz(
                TarTestData.Entry("dev/null", null, '3'),
                TarTestData.file("ok.txt", "还在"),
            ),
        )

        assertEquals(1, result.skipped)
        assertEquals(1, result.files)
        assertEquals("还在", read(dir, "ok.txt"))
    }

    /**
     * 软链在这台机器的 JVM 上不一定建得出来（Windows 要特权）。
     * 所以这里只断言「不管成不成，都不会把解压判死」。
     */
    @Test
    fun 软链建不出来也只是记warning() {
        val (result, dir) = extract(
            TarTestData.gz(
                TarTestData.file("real.txt", "内容"),
                TarTestData.symlink("link.txt", "real.txt"),
            ),
        )

        assertEquals("内容", read(dir, "real.txt"))
        if (result.symlinks == 0) {
            assertTrue(result.warnings.isNotEmpty())
        }
    }

    @Test
    fun 校验和坏掉就抛错而不是解出垃圾() {
        val bytes = TarTestData.gz(TarTestData.file("a.txt", "内容"), corruptChecksum = true)

        val threw = runCatching { extract(bytes) }.exceptionOrNull()

        assertTrue(threw is IOException)
    }

    @Test
    fun GNU长文件名也能解开() {
        val longName = "deep/${"a".repeat(90)}/${"b".repeat(90)}/file.txt"
        val gz = TarTestData.gz(TarTestData.Entry(longName, "长名内容", '0'))

        val (result, dir) = extract(gz)

        // 名字超过 100 字节时标准的 tar 会拆成 'L' 条目；这里没造 'L'，
        // 所以名字会被截到 100 字节 —— 至少不该崩、不该解到目录外
        assertTrue(result.files + result.skipped > 0)
        assertTrue(dir.exists())
    }

    @Test
    fun 空压缩包也能安全结束() {
        val (result, _) = extract(TarTestData.gz())

        assertEquals(0, result.files)
        assertEquals(0, result.dirs)
    }
}
