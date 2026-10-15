package com.example.wastenot.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreRefreshTokenStoreTest {
    private lateinit var directory: File
    private lateinit var alias: String
    private lateinit var store: KeystoreRefreshTokenStore
    private val token = RefreshToken.of("fixture-refresh-token-private-marker")

    @Before
    fun setUp() {
        val id = UUID.randomUUID().toString()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.noBackupFilesDir, "token-store-test-$id")
        alias = "wastenot.test.$id"
        store = KeystoreRefreshTokenStore(directory, alias)
    }

    @After
    fun tearDown() = runTest {
        store.clear()
        directory.deleteRecursively()
    }

    @Test
    fun roundTripUsesCiphertextRandomIvAndANonExportableKey() = runTest {
        assertEquals(TokenReadResult.Absent, store.read())
        store.write(token)
        val first = File(directory, "refresh-token.enc").readBytes()
        assertFalse(first.toString(Charsets.UTF_8).contains(token.value))
        assertEquals(TokenReadResult.Available(token), KeystoreRefreshTokenStore(directory, alias).read())
        store.write(token)
        assertFalse(first.contentEquals(File(directory, "refresh-token.enc").readBytes()))
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertNull(ks.getKey(alias, null).encoded)
        assertFalse(token.toString().contains(token.value))
    }

    @Test
    fun tamperingInvalidatesAndRemovesCredentialAndKey() = runTest {
        store.write(token)
        val file = File(directory, "refresh-token.enc")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertEquals(TokenReadResult.Invalidated, store.read())
        assertFalse(file.exists())
        assertEquals(TokenReadResult.Absent, store.read())
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(ks.containsAlias(alias))
    }

    @Test
    fun lostKeyNeverCreatesAReplacementToReadAnOldToken() = runTest {
        store.write(token)
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.deleteEntry(alias)
        assertEquals(TokenReadResult.Invalidated, store.read())
        assertEquals(TokenReadResult.Absent, store.read())
        assertFalse(ks.containsAlias(alias))
        store.write(token)
        assertEquals(TokenReadResult.Available(token), store.read())
    }

    @Test
    fun clearRemovesAllCredentialFilesAndIsIdempotent() = runTest {
        store.write(token)
        store.clear()
        store.clear()
        assertEquals(TokenReadResult.Absent, store.read())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(ks.containsAlias(alias))
    }
}
