package com.quine.core.sandbox

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class SandboxInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeSource(
        private val bytes: ByteArray,
        override val expectedSha256: String? = null,
        private val fail: Boolean = false,
    ) : RootfsSource {
        override val sizeBytes: Long? = bytes.size.toLong()
        override fun open(): InputStream {
            if (fail) throw IOException("网络断了")
            return ByteArrayInputStream(bytes)
        }
    }

    private val fullRootfs = TarTestData.miniRootfs()

    /** 缺 busybox：能通过解压，但过不了工具链自检。 */
    private val brokenRootfs = TarTestData.gz(
        TarTestData.dir("bin"),
        TarTestData.file("bin/sh", "#!/bin/sh\n"),
        TarTestData.file("stale.txt", "上次装了一半留下的"),
    )

    private fun root(name: String = "rootfs"): File = File(temp.root, name)

    private fun installer(
        bytes: ByteArray = fullRootfs,
        target: File = root(),
        expected: String? = null,
        fail: Boolean = false,
        toolchain: List<String> = SandboxInstaller.DEFAULT_TOOLCHAIN,
        estimated: Long = 1024L * 1024,
    ): SandboxInstaller = SandboxInstaller(
        root = target,
        source = FakeSource(bytes, expected, fail),
        toolchain = toolchain,
        estimatedBytes = estimated,
    )

    private fun install(installer: SandboxInstaller): Pair<SandboxState, List<SandboxState>> {
        val states = mutableListOf<SandboxState>()
        val final = runBlocking {
            installer.install { states += it }
        }
        return final to states
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun 四步走完就就绪() {
        val target = root()
        val (final, states) = install(installer(expected = sha256(fullRootfs), target = target))

        assertTrue(final is SandboxState.Ready)
        assertTrue(installer().let { SandboxInstaller(target, FakeSource(fullRootfs)).isReady() })
        assertTrue(File(target, "bin/sh").exists())
        // 每个阶段都报过进度
        val stages = states.filterIsInstance<SandboxState.Working>().map { it.stage }.toSet()
        assertEquals(SandboxStage.entries.toSet(), stages)
    }

    @Test
    fun 日志是真实的过程记录() {
        val (final, _) = install(installer(expected = sha256(fullRootfs)))

        val log = (final as SandboxState.Ready).log
        assertTrue(log.any { it.startsWith("[ok]") })
        assertTrue(log.any { it.contains("工具链自检") })
    }

    @Test
    fun 空间不够就卡在第一步() {
        val (final, _) = install(
            installer(estimated = Long.MAX_VALUE / 4),
        )

        assertTrue(final is SandboxState.Failed)
        val failed = final as SandboxState.Failed
        assertEquals(SandboxStage.CHECK_DEVICE, failed.stage)
        assertTrue(failed.error.nextStep.contains("清理"))
        // 屏二的文案就长这个形状
        assertTrue(failed.headline().startsWith("卡在第 ① 步"))
    }

    @Test
    fun 下载断了卡在第二步() {
        val (final, _) = install(installer(fail = true))

        val failed = final as SandboxState.Failed
        assertEquals(SandboxStage.EXPAND_ROOTFS, failed.stage)
        assertTrue(failed.error.message.contains("取回来"))
        // 具体原因要带出来：没有来源配置时"网络断了"是句假话
        assertTrue(failed.error.message.contains("网络断了"))
    }

    @Test
    fun 校验对不上就不装进去() {
        val (final, _) = install(installer(expected = "0".repeat(64)))

        val failed = final as SandboxState.Failed
        assertEquals(SandboxStage.EXPAND_ROOTFS, failed.stage)
        assertTrue(failed.error.message.contains("对不上"))
        assertFalse(File(root(), "bin/sh").exists())
    }

    @Test
    fun 工具链缺失卡在第四步并说清缺了什么() {
        val (final, _) = install(
            installer(bytes = brokenRootfs, expected = sha256(brokenRootfs)),
        )

        val failed = final as SandboxState.Failed
        assertEquals(SandboxStage.TOOLCHAIN_SELFTEST, failed.stage)
        assertTrue(failed.error.message.contains("busybox"))
        assertTrue(failed.headline().startsWith("卡在第 ④ 步"))
    }

    /** 失败重试是最常见的路径：残留必须被清掉，不能叠在半截内容上。 */
    @Test
    fun 装失败后重试不会叠在残留上() {
        val target = root()
        install(installer(bytes = brokenRootfs, expected = sha256(brokenRootfs), target = target))
        assertTrue(File(target, "stale.txt").exists())

        val (final, _) = install(installer(expected = sha256(fullRootfs), target = target))

        assertTrue(final is SandboxState.Ready)
        assertFalse("上次装一半留下的 stale.txt 应该被清掉", File(target, "stale.txt").exists())
        assertTrue(File(target, "bin/busybox").exists())
    }

    @Test
    fun 就绪后可以清掉重来() {
        val target = root()
        install(installer(expected = sha256(fullRootfs), target = target))
        assertTrue(SandboxInstaller(target, FakeSource(fullRootfs)).isReady())

        SandboxInstaller(target, FakeSource(fullRootfs)).clearRoot()

        assertFalse(SandboxInstaller(target, FakeSource(fullRootfs)).isReady())
        assertTrue(target.list()?.isEmpty() != false)
    }

    @Test
    fun 解压失败也知道自己卡在哪一步() {
        val (final, _) = install(
            installer(bytes = TarTestData.gz(TarTestData.file("a", "b"), corruptChecksum = true)),
        )

        val failed = final as SandboxState.Failed
        assertEquals(SandboxStage.INIT_FILESYSTEM, failed.stage)
        assertTrue(failed.headline().startsWith("卡在第 ③ 步"))
    }
}
