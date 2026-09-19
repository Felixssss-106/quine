package com.quine.core.sandbox

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.common.SystemTime
import com.quine.core.common.TimeProvider
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * rootfs 从哪来。**实现在 app 层**（HTTP 下载 / 内置 / 测试里的假数据）：
 * `core-sandbox` 不该认识 OkHttp，也不该认识网络。
 */
interface RootfsSource {
    /** 期望的 sha256（小写 hex）。null = 不校验（只建议在测试里这么干）。 */
    val expectedSha256: String?

    /** 预计字节数；null = 未知，进度条退化为不确定。 */
    val sizeBytes: Long?

    @Throws(Exception::class)
    fun open(): InputStream
}

/**
 * 首启屏二背后那份工作（`page-specs.md` §1）：四步走完，沙箱就绪。
 *
 * 两条刻意的设计：
 * - **每一步都能单独失败**，且失败时知道自己卡在第几步 —— 屏二的
 *   「卡在第 ③ 步：存储空间不足。」就是靠这个说出来的。
 * - **可重入**：上次装了一半的残留会被清掉，重试不用先手工清理。
 *   靠 [readyMark] 判断「上次是不是装完了」，避免把装了一半的东西当成品。
 */
class SandboxInstaller(
    private val root: File,
    private val source: RootfsSource,
    /** 第 ④ 步要检查的关键二进制（相对 rootfs 根的路径）。 */
    private val toolchain: List<String> = DEFAULT_TOOLCHAIN,
    /** 第 ① 步用来判断空间够不够的预估值。 */
    private val estimatedBytes: Long = DEFAULT_ESTIMATED_BYTES,
    private val time: TimeProvider = SystemTime,
) {

    /** 装完了没有。屏二可以据此决定「秒进主界面」还是「再来一次」。 */
    fun isReady(): Boolean = readyMark.exists()

    /**
     * 走完四步。`emit` 每步都会回调（日志会累积，屏二的日志区直接照着渲染）。
     *
     * @return 终态：[SandboxState.Ready] 或 [SandboxState.Failed]。
     */
    suspend fun install(emit: suspend (SandboxState) -> Unit): SandboxState {
        val log = mutableListOf<String>()

        // ---- ① 检查设备环境 ----
        emit(SandboxState.Working(SandboxStage.CHECK_DEVICE, 0f, log.toList()))

        if (!ensureDirectory(root)) {
            return fail(SandboxStage.CHECK_DEVICE, cannotPrepare(), log, emit)
        }
        // 上次的残留：没装完就清掉，重试才不会叠在半截内容上
        if (!readyMark.exists() && root.list()?.isNotEmpty() == true) {
            log += "[..] 清掉上次没装完的残留"
            clearRoot()
        }
        val usable = root.usableSpace
        log += "[ok] 目录可写，可用空间 ${(usable / MB)} MB"
        if (usable < estimatedBytes) {
            return fail(SandboxStage.CHECK_DEVICE, notEnoughSpace(estimatedBytes), log, emit)
        }
        emit(SandboxState.Working(SandboxStage.CHECK_DEVICE, 1f, log.toList()))

        // ---- ② 取回 rootfs 并校验 ----
        val tarball = File(root.parentFile, "${root.name}.tar.gz")
        log += "[..] 取回 Linux 工作区"
        emit(SandboxState.Working(SandboxStage.EXPAND_ROOTFS, 0f, log.toList()))

        // download 是 suspend（进度回调里要 emit），所以这里用 try/catch 而不是 runCatching
        val actual: String = try {
            download(tarball) { progress ->
                log += "[..] ${(progress * 100).roundToInt()}%"
                emit(SandboxState.Working(SandboxStage.EXPAND_ROOTFS, progress, log.toList()))
            }
        } catch (error: Exception) {
            return fail(SandboxStage.EXPAND_ROOTFS, downloadFailed(error.message), log, emit)
        }
        log += "[ok] 收到 ${(tarball.length() / MB)} MB，sha256=${actual.take(12)}…"

        val expected = source.expectedSha256
        if (expected != null && !actual.equals(expected, ignoreCase = true)) {
            return fail(SandboxStage.EXPAND_ROOTFS, checksumMismatch(), log, emit)
        }
        log += "[ok] 校验通过"
        emit(SandboxState.Working(SandboxStage.EXPAND_ROOTFS, 1f, log.toList()))

        // ---- ③ 展开 + 建运行时要用的目录 ----
        log += "[..] 展开目录树"
        emit(SandboxState.Working(SandboxStage.INIT_FILESYSTEM, 0f, log.toList()))

        val extracted = runCatching {
            tarball.inputStream().use { TarGz.extract(it, root) }
        }.getOrNull()
        if (extracted == null) {
            return fail(SandboxStage.INIT_FILESYSTEM, extractFailed(), log, emit)
        }
        log += "[ok] ${extracted.files} 个文件、${extracted.dirs} 个目录"
        for (warning in extracted.warnings.take(3)) log += "[!!] $warning"

        for (dir in RUNTIME_DIRS) File(root, dir).mkdirs()
        log += "[ok] 运行时目录就位"
        emit(SandboxState.Working(SandboxStage.INIT_FILESYSTEM, 1f, log.toList()))

        // ---- ④ 工具链自检 ----
        emit(SandboxState.Working(SandboxStage.TOOLCHAIN_SELFTEST, 0f, log.toList()))
        val missing = toolchain.filter { !File(root, it).exists() }
        if (missing.isNotEmpty()) {
            return fail(SandboxStage.TOOLCHAIN_SELFTEST, toolchainMissing(missing), log, emit)
        }
        log += "[ok] 工具链自检通过（${toolchain.joinToString("、")}）"
        emit(SandboxState.Working(SandboxStage.TOOLCHAIN_SELFTEST, 1f, log.toList()))

        // 装完了：留个标记，下次启动直接秒进
        runCatching { readyMark.writeText("${time.nowMillis()}") }
        tarball.delete()
        val ready = SandboxState.Ready(root, log.toList())
        emit(ready)
        return ready
    }

    /** 把 rootfs 删掉。**调用方必须保证 root 是应用私有的 rootfs 目录** —— 不是用户文件。 */
    fun clearRoot() {
        root.listFiles()?.forEach { it.deleteRecursively() }
        readyMark.delete()
    }

    /**
     * 下载并算 sha256。边下边算，不把整份读进内存 —— rootfs 有几十 MB。
     */
    @Throws(Exception::class)
    private suspend fun download(target: File, onProgress: suspend (Float) -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        val total = source.sizeBytes
        var read = 0L
        target.parentFile?.mkdirs()
        source.open().use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    out.write(buf, 0, n)
                    read += n
                    if (total != null && total > 0) {
                        onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensureDirectory(dir: File): Boolean = runCatching {
        dir.mkdirs()
        dir.isDirectory && dir.canWrite()
    }.getOrDefault(false)

    private suspend fun fail(
        stage: SandboxStage,
        error: QuineError,
        log: MutableList<String>,
        emit: suspend (SandboxState) -> Unit,
    ): SandboxState {
        log += "[!!] ${error.message}"
        val state = SandboxState.Failed(stage, error, log.toList())
        emit(state)
        return state
    }

    private val readyMark: File get() = File(root, READY_MARK)

    companion object {
        const val READY_MARK = ".quine-ready"

        /** 文档写「占用约 200 MB」，这里就按这个量级预估。 */
        const val DEFAULT_ESTIMATED_BYTES = 200L * 1024 * 1024

        val DEFAULT_TOOLCHAIN = listOf("bin/sh", "bin/busybox")

        /** proot 起来之后要用到的挂载点与目录。 */
        val RUNTIME_DIRS = listOf("tmp", "proc", "dev", "home", "root")

        private const val MB = 1024 * 1024

        fun notEnoughSpace(need: Long) = QuineError(
            kind = ErrorKind.STORAGE,
            message = "存储空间不够（大约需要 ${need / MB} MB）。",
            impact = "工作区没搭起来，现在还跑不了命令。",
            nextStep = "清理出空间后点「重试」。",
        )

        fun cannotPrepare() = QuineError(
            kind = ErrorKind.STORAGE,
            message = "工作区目录建不出来。",
            impact = "工作区没搭起来，现在还跑不了命令。",
            nextStep = "确认存储空间正常后点「重试」。",
        )

        /** @param reason 具体原因。没有来源配置时它比"网络断了"更接近真相。 */
        fun downloadFailed(reason: String? = null) = QuineError(
            kind = ErrorKind.NETWORK,
            message = if (reason.isNullOrBlank()) {
                "工作区下载到一半断了。"
            } else {
                "工作区没能取回来：$reason"
            },
            impact = "工作区没搭起来。",
            nextStep = "检查网络后点「重试」，会从头重新下载。",
        )

        fun checksumMismatch() = QuineError(
            kind = ErrorKind.NETWORK,
            message = "下载下来的工作区和预期的对不上。",
            impact = "为了不装进一个坏的工作区，这次停下了。",
            nextStep = "点「重试」重新下载一次；反复对不上就换个网络试试。",
        )

        fun extractFailed() = QuineError(
            kind = ErrorKind.STORAGE,
            message = "工作区展开到一半失败了，多半是空间不够。",
            impact = "工作区没搭起来。",
            nextStep = "清理出空间后点「重试」。",
        )

        fun toolchainMissing(missing: List<String>) = QuineError(
            kind = ErrorKind.TOOL,
            message = "工作区里少了 ${missing.joinToString("、")} 这类关键工具。",
            impact = "命令跑不起来。",
            nextStep = "点「重试」；反复失败的话换个发行版再试。",
        )
    }
}
