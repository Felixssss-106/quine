package com.quine.core.sandbox

import com.quine.core.common.QuineError
import java.io.File

/**
 * 首启屏二的四个步骤（`docs/page-specs.md` §1）。
 *
 * 顺序不是随便排的：**「存储空间不足」会发生在第 ③ 步**（解压时才真正知道要占多少），
 * 这跟文档给的失败文案「卡在第 ③ 步：存储空间不足。」是对上的。
 */
enum class SandboxStage {
    /** ① 看这台设备能不能跑：目录可写、ABI 认识、空间大概够。 */
    CHECK_DEVICE,

    /** ② 把 rootfs 的 tarball 取回来并校验。 */
    EXPAND_ROOTFS,

    /** ③ 解压成真的目录树，再补上 /tmp、/proc、/home 这些运行时要用的地方。 */
    INIT_FILESYSTEM,

    /** ④ 工具链自检：关键二进制在不在。 */
    TOOLCHAIN_SELFTEST,
    ;

    /** 屏二步骤列表里的序号标记：① ② ③ ④。失败文案要用。 */
    val stepMark: String get() = MARKS[ordinal]

    /** 屏二步骤列表里的文案。 */
    val label: String
        get() = when (this) {
            CHECK_DEVICE -> "检查设备环境"
            EXPAND_ROOTFS -> "展开 Linux 工作区"
            INIT_FILESYSTEM -> "初始化文件系统"
            TOOLCHAIN_SELFTEST -> "工具链自检"
        }

    private companion object {
        val MARKS = listOf("①", "②", "③", "④")
    }
}

/** 沙箱安装的状态。屏二照着它渲染：步骤点、进度条、日志区、失败态。 */
sealed interface SandboxState {

    /** 还没开始。 */
    data object Idle : SandboxState

    /** 正在某一步上。`progress` 是**这一步内**的进度（0..1），总进度由 UI 折算。 */
    data class Working(
        val stage: SandboxStage,
        val progress: Float,
        val log: List<String> = emptyList(),
    ) : SandboxState

    /** 装好了，可以直接用。 */
    data class Ready(
        val root: File,
        val log: List<String> = emptyList(),
    ) : SandboxState

    /**
     * 卡住了。`stage` 就是卡在第几步 —— 屏二据此给出「卡在第 ③ 步：……」。
     *
     * `error` 必须是三段式（发生了什么 / 影响 / 下一步），
     * 因为屏二的失败态要同时给出「重试」和「清理空间」这类出口。
     */
    data class Failed(
        val stage: SandboxStage,
        val error: QuineError,
        val log: List<String> = emptyList(),
    ) : SandboxState
}

/** 失败文案：「卡在第 ③ 步」（page-specs §1 的原话就是这个形状）。 */
fun SandboxState.Failed.headline(): String = "卡在第 ${stage.stepMark} 步：${error.message}"
