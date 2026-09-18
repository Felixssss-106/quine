package com.quine.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 红线验收（附录 C）：logcat / 磁盘都不许出现 Key 明文。
 * 这里直接翻 SharedPreferences 的落地文件来找明文。
 */
@RunWith(AndroidJUnit4::class)
class KeystoreApiKeyStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun ref() = "test_${System.nanoTime()}"

    @Test
    fun 存取删往返() = runBlocking {
        val store = KeystoreApiKeyStore(context)
        val keyRef = ref()
        val secret = "sk-test-abcdefghijklmnop"

        store.put(keyRef, secret)
        assertTrue(store.has(keyRef))
        assertEquals(secret, store.get(keyRef))

        store.delete(keyRef)
        assertFalse(store.has(keyRef))
        assertNull(store.get(keyRef))
    }

    @Test
    fun 覆盖写同一个 ref() = runBlocking {
        val store = KeystoreApiKeyStore(context)
        val keyRef = ref()

        store.put(keyRef, "sk-first-0000000000")
        store.put(keyRef, "sk-second-1111111111")

        assertEquals("sk-second-1111111111", store.get(keyRef))
        store.delete(keyRef)
    }

    @Test
    fun 不同 ref 互不干扰() = runBlocking {
        val store = KeystoreApiKeyStore(context)
        val a = ref()
        val b = ref()

        store.put(a, "sk-aaaa-0000000000")
        store.put(b, "sk-bbbb-1111111111")

        assertEquals("sk-aaaa-0000000000", store.get(a))
        assertEquals("sk-bbbb-1111111111", store.get(b))

        store.delete(a)
        store.delete(b)
    }

    @Test
    fun 明文不落盘() = runBlocking {
        val store = KeystoreApiKeyStore(context)
        val keyRef = ref()
        val secret = "sk-plaintext-must-not-appear-123456"

        store.put(keyRef, secret)

        val prefsDir = File(context.applicationContext.dataDir, "shared_prefs")
        val hit = prefsDir.listFiles().orEmpty()
            .filter { it.isFile }
            .any { it.readText().contains(secret) }

        assertFalse("SharedPreferences 落地文件里不允许出现 Key 明文", hit)

        store.delete(keyRef)
    }
}
