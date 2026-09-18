package com.quine.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.quine.core.common.QuineDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 存储。红线（agent-prompt.md §0.2）：Key 只存本机、Keystore 加密、
 * 日志严禁出现明文。因此这里的实现细节是：
 *
 * - 密文进私有 SharedPreferences，**密钥本身永远留在 AndroidKeyStore，不出硬件**
 * - AES/GCM/NoPadding，每次加密随机 IV，IV 与密文一起存
 * - 任何失败路径都不打印内容，解密失败就当「没存过」并清掉脏数据，让用户重新粘贴
 */
interface ApiKeyStore {
    suspend fun put(ref: String, apiKey: String)

    suspend fun get(ref: String): String?

    suspend fun delete(ref: String)

    suspend fun has(ref: String): Boolean
}

class KeystoreApiKeyStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = QuineDispatchers().io,
) : ApiKeyStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun put(ref: String, apiKey: String): Unit = withContext(dispatcher) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(keyOf(ref), payload).commit()
    }

    override suspend fun get(ref: String): String? = withContext(dispatcher) {
        val payload = prefs.getString(keyOf(ref), null) ?: return@withContext null
        val parts = payload.split(SEPARATOR, limit = 2)
        if (parts.size != 2) {
            prefs.edit().remove(keyOf(ref)).commit()
            return@withContext null
        }
        runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrElse {
            // 换机、Keystore 被清、密文损坏：清掉脏数据，别让用户卡在一个永远失败的 Key 上。
            prefs.edit().remove(keyOf(ref)).commit()
            null
        }
    }

    override suspend fun delete(ref: String): Unit = withContext(dispatcher) {
        prefs.edit().remove(keyOf(ref)).commit()
    }

    override suspend fun has(ref: String): Boolean = withContext(dispatcher) {
        prefs.contains(keyOf(ref))
    }

    private fun keyOf(ref: String): String = "api_key_$ref"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "quine_secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "quine.api_key.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
