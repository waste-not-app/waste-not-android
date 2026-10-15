package com.example.wastenot.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class KeystoreRefreshTokenStore internal constructor(
    private val directory: File,
    private val alias: String = "wastenot.refresh-token.v1",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RefreshTokenStore {
    private val file = AtomicFile(File(directory, "refresh-token.enc"))
    private val mutex = Mutex()

    override suspend fun read(): TokenReadResult = access {
        if (!file.baseFile.exists() && !File(directory, "refresh-token.enc.bak").exists()) {
            return@access TokenReadResult.Absent
        }
        try {
            DataInputStream(file.openRead()).use { input ->
                require(input.readInt() == MAGIC)
                val iv = ByteArray(12).also(input::readFully)
                val size = input.readInt()
                require(size in 17..8208)
                val ciphertext = ByteArray(size).also(input::readFully)
                require(input.read() == -1)
                val key = keyStore().getKey(alias, null) as? SecretKey
                    ?: throw GeneralSecurityException("Credential key unavailable.")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                cipher.updateAAD(associatedData)
                val plaintext = cipher.doFinal(ciphertext)
                try {
                    TokenReadResult.Available(RefreshToken.of(plaintext.decodeToString(throwOnInvalidSequence = true)))
                } finally {
                    plaintext.fill(0)
                }
            }
        } catch (_: GeneralSecurityException) {
            invalidate()
        } catch (_: ProviderException) {
            invalidate()
        } catch (_: IOException) {
            invalidate()
        } catch (_: IllegalArgumentException) {
            invalidate()
        }
    }

    override suspend fun write(token: RefreshToken): Unit = access {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Credential directory unavailable.")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        require(cipher.iv.size == 12)
        val plaintext = token.value.encodeToByteArray()
        val ciphertext = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        val stream = file.startWrite()
        try {
            DataOutputStream(stream).apply {
                writeInt(MAGIC)
                write(cipher.iv)
                writeInt(ciphertext.size)
                write(ciphertext)
                flush()
            }
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    override suspend fun clear(): Unit = access { clearBlocking() }

    private fun invalidate(): TokenReadResult {
        clearBlocking()
        return TokenReadResult.Invalidated
    }

    private fun clearBlocking() {
        file.delete()
        keyStore().deleteEntry(alias)
        if (listOf("", ".bak", ".new").any { File(directory, "refresh-token.enc$it").exists() }) {
            throw IOException("Credential file could not be removed.")
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore().getKey(alias, null)
        if (existing != null) return existing as? SecretKey
            ?: throw GeneralSecurityException("Unexpected credential key type.")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private suspend fun <T> access(block: () -> T): T = withContext(ioDispatcher) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            try { block() }
            catch (error: GeneralSecurityException) { throw IOException("Secure credential storage unavailable.", error) }
            catch (error: ProviderException) { throw IOException("Secure credential storage unavailable.", error) }
        }
    }

    companion object {
        private const val MAGIC = 0x574E5431
        private val associatedData = "WasteNot refresh token v1".encodeToByteArray()

        fun create(context: Context): KeystoreRefreshTokenStore =
            KeystoreRefreshTokenStore(context.applicationContext.noBackupFilesDir)
    }
}
