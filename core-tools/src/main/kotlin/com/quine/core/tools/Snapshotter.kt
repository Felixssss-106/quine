package com.quine.core.tools

import com.quine.core.common.QuineError

/**
 * 快照记录器。**改动前必须调用**，是一键回滚的根据。
 *
 * 与 [Workspace] 一样是依赖倒置的接口：实现在 app 层（接到 `core-storage` 的
 * `SnapshotStore`），`core-tools` 不认识 `core-storage`。
 */
interface Snapshotter {

    /**
     * 记下改动前的内容，返回 blobRef。
     *
     * **失败必须返回 null，调用方据此拒绝写入** —— 没有快照的改动不可回滚，
     * 宁可写不进去，也不能留下撤不回来的改动。
     */
    fun capture(path: String, oldBytes: ByteArray): String?
}

/**
 * 一律拒绝的实现（返回 null）：用在「不该发生写入」的场合或测试里。
 *
 * 名字叫 Denying 而不是 Noop —— 因为它不是「什么都不做」，
 * 而是**让写入失败**，这正是红线要求的失败方向。
 */
object DenyingSnapshotter : Snapshotter {
    override fun capture(path: String, oldBytes: ByteArray): String? = null
}
