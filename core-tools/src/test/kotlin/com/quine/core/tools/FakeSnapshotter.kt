package com.quine.core.tools

/** 测试用：记录快照调用，并可按需要模拟失败（验证「拿不到快照就拒绝写入」）。 */
class FakeSnapshotter : Snapshotter {
    data class Capture(val path: String, val oldBytes: ByteArray)

    val captures = mutableListOf<Capture>()

    /** 置 true 后 `capture` 返回 null，用来验证写入会被拦下。 */
    var failNext = false

    /**
     * 捕获时触发的副作用。用于模拟「读取之后、写入之前文件被人改了」的并发场景 ——
     * 捕获正好发生在读与写之间。
     */
    var onCapture: (() -> Unit)? = null

    override fun capture(path: String, oldBytes: ByteArray): String? {
        onCapture?.invoke()
        if (failNext) return null
        captures += Capture(path, oldBytes)
        return "blob/sha256/fake-${captures.size}"
    }

    fun reset() {
        captures.clear()
        failNext = false
        onCapture = null
    }
}

/** 测试用：总是成功，不关心内容。用于被测路径不涉及写入的场景。 */
object StubSnapshotter : Snapshotter {
    override fun capture(path: String, oldBytes: ByteArray): String? = "blob/sha256/stub"
}
