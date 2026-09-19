package com.quine.core.tools

/**
 * 快照登记处。与 [com.quine.core.tools.workspace.Workspace] 一样是依赖倒置的接口：
 * 实现在 app 层（接到 `core-storage` 的 `SnapshotStore` 与 `TaskDao`），
 * `core-tools` 不认识 `core-storage`。
 *
 * 快照是「一键回滚」的根据：改动前记一份，改坏了把它写回去。
 *
 * 四个方法都是 `suspend`：实现的背后是数据库与磁盘 IO，
 * 而调用方（工具）本来就在挂起上下文里 —— 在 IO 线程上 `runBlocking` 等自己，
 * 是把「能取消的等待」变成「堵死的线程」，没必要。
 */
interface SnapshotRegistry {

    /**
     * 记下改动前的内容，返回引用。**失败返回 null** —— 写工具据此拒绝写入：
     * 宁可写不进去，也不能留下撤不回来的改动。
     *
     * @param root 工作区标识（[com.quine.core.tools.workspace.Workspace.label]）。
     *   `path` 是相对路径、不带工作区信息，而用户可以在「授权目录」与「私有工作区」之间切换；
     *   不记下它，回滚就会把 A 工作区的旧内容写进 B 工作区的同名文件 —— 那不是回滚，是破坏。
     */
    suspend fun capture(path: String, oldBytes: ByteArray, root: String): SnapshotRef?

    /** 某个路径最近一次快照；没有返回 null（回滚默认找它）。 */
    suspend fun latestFor(path: String): SnapshotRef?

    /** 按快照 id 找；没有返回 null。 */
    suspend fun find(id: String): SnapshotRef?

    /** 读回 blob 内容（回滚时用）；blob 已不在返回 null。 */
    suspend fun contentOf(blobRef: String): ByteArray?
}

/** 一条快照记录。`id` 是回滚时用的把手，`blobRef` 指向内容，`root` 标明属于哪个工作区。 */
data class SnapshotRef(
    val id: String,
    val path: String,
    val blobRef: String,
    val root: String,
    val createdAt: Long,
)

/**
 * 一律拒绝的实现（capture 返回 null、查询返回 null）：用在「不该发生写入」的场合或测试里。
 *
 * 名字叫 Denying 而不是 Noop —— 它不是「什么都不做」，
 * 而是**让写入失败**，这正是红线要求的失败方向。
 */
object DenyingSnapshots : SnapshotRegistry {
    override suspend fun capture(path: String, oldBytes: ByteArray, root: String): SnapshotRef? = null
    override suspend fun latestFor(path: String): SnapshotRef? = null
    override suspend fun find(id: String): SnapshotRef? = null
    override suspend fun contentOf(blobRef: String): ByteArray? = null
}
