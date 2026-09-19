package com.quine.core.tools

/** 测试用：记录快照调用，并可按需要模拟失败（验证「拿不到快照就拒绝写入」）。 */
class FakeSnapshots : SnapshotRegistry {
    data class Capture(val path: String, val oldBytes: ByteArray)

    val captures = mutableListOf<Capture>()

    /** 置 true 后 `capture` 返回 null，用来验证写入会被拦下。 */
    var failNext = false

    /**
     * 捕获时触发的副作用。用于模拟「读取之后、写入之前文件被人改了」的并发场景 ——
     * 捕获正好发生在读与写之间。
     */
    var onCapture: (() -> Unit)? = null

    /** 置 true 后 `contentOf` 返回 null：模拟 blob 被 prune 掉，验证回滚会明确失败。 */
    var loseBlobs = false

    private val refs = mutableListOf<SnapshotRef>()
    private val blobs = mutableMapOf<String, ByteArray>()

    override suspend fun capture(path: String, oldBytes: ByteArray, root: String): SnapshotRef? {
        onCapture?.invoke()
        if (failNext) return null
        captures += Capture(path, oldBytes)
        val blobRef = "blob/sha256/fake-${captures.size}"
        blobs[blobRef] = oldBytes
        val ref = SnapshotRef(
            id = "snap-${captures.size}",
            path = path,
            blobRef = blobRef,
            root = root,
            createdAt = captures.size.toLong(),
        )
        refs += ref
        return ref
    }

    override suspend fun latestFor(path: String): SnapshotRef? = refs.lastOrNull { it.path == path }

    override suspend fun find(id: String): SnapshotRef? = refs.firstOrNull { it.id == id }

    override suspend fun contentOf(blobRef: String): ByteArray? =
        if (loseBlobs) null else blobs[blobRef]

    /** 预置一条快照：用来模拟不是本站产生的记录（例如另一个工作区留下的）。 */
    fun seed(ref: SnapshotRef, content: ByteArray) {
        refs += ref
        blobs[ref.blobRef] = content
    }

    fun reset() {
        captures.clear()
        refs.clear()
        blobs.clear()
        failNext = false
        onCapture = null
        loseBlobs = false
    }
}

/** 测试用：总是成功，不关心内容。用于被测路径不涉及写入的场景。 */
object StubSnapshots : SnapshotRegistry {
    private val refs = mutableListOf<SnapshotRef>()
    private val blobs = mutableMapOf<String, ByteArray>()

    override suspend fun capture(path: String, oldBytes: ByteArray, root: String): SnapshotRef {
        val blobRef = "blob/sha256/stub"
        blobs[blobRef] = oldBytes
        val ref = SnapshotRef(
            id = "snap-stub",
            path = path,
            blobRef = blobRef,
            root = root,
            createdAt = System.currentTimeMillis(),
        )
        refs += ref
        return ref
    }

    override suspend fun latestFor(path: String): SnapshotRef? = refs.lastOrNull { it.path == path }

    override suspend fun find(id: String): SnapshotRef? = refs.firstOrNull { it.id == id }

    override suspend fun contentOf(blobRef: String): ByteArray? = blobs[blobRef]
}
