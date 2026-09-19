package com.quine.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SnapshotStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: SnapshotStore

    @Before
    fun setUp() {
        store = SnapshotStore(File(temp.root, "snapshots"))
    }

    @Test
    fun 存取往返() {
        val content = "- 第一行\n- 第二行\n".toByteArray()
        val ref = store.put(content)

        assertTrue(ref.startsWith("blob/sha256/"))
        assertTrue(store.exists(ref))
        assertEquals(content.decodeToString(), store.content(ref)!!.decodeToString())
    }

    @Test
    fun 同一内容只存一份() {
        val first = store.put("相同内容".toByteArray())
        val second = store.put("相同内容".toByteArray())

        assertEquals(first, second)
        assertEquals(1, File(File(temp.root, "snapshots"), "blob").walkTopDown().count { it.isFile })
    }

    @Test
    fun 不同内容得到不同引用() {
        val a = store.put("内容 A".toByteArray())
        val b = store.put("内容 B".toByteArray())
        assertNotEquals(a, b)
    }

    @Test
    fun 空内容也能存() {
        val ref = store.put(ByteArray(0))
        assertTrue(store.exists(ref))
        assertEquals(0, store.content(ref)!!.size)
    }

    @Test
    fun 删除后读不到() {
        val ref = store.put("会被删掉".toByteArray())
        store.delete(ref)
        assertFalse(store.exists(ref))
        assertNull(store.content(ref))
    }

    @Test
    fun 清理只删没有被引用的() {
        val kept = store.put("保留".toByteArray())
        store.put("丢弃".toByteArray())
        store.put("也丢弃".toByteArray())

        val removed = store.prune(setOf(kept))
        assertEquals(2, removed)
        assertTrue(store.exists(kept))
        assertEquals(1, File(File(temp.root, "snapshots"), "blob").walkTopDown().count { it.isFile })
    }

    @Test
    fun 用量统计() {
        store.put("12345".toByteArray())
        store.put("67890".toByteArray())
        assertEquals(10L, store.usageBytes())
    }

    @Test
    fun 非法引用不会误判为存在() {
        assertFalse(store.exists("不是 blob 引用"))
        assertNull(store.content("blob/sha256/not-hex"))
        assertFalse(store.exists(""))
    }
}
