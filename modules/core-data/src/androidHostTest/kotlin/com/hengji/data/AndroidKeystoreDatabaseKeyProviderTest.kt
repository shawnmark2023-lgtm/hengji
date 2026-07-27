package com.hengji.data

import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class AndroidKeystoreDatabaseKeyProviderTest {
    @Test
    fun provisionsReloadsAndSeparatesKeysWithoutPersistingPlaintext() = withTemporaryDirectory { root ->
        runTest {
            val protector = FakeAndroidKeyProtector()
            val provider = provider(root, protector)
            assertNull(provider.loadKey("ledger-primary"))

            val created = provider.loadOrCreateKey("ledger-primary").useBytes()
            val sameProcess = provider.loadOrCreateKey("ledger-primary").useBytes()
            val reloaded = provider(root, protector).loadKey("ledger-primary")!!.useBytes()
            val secondAlias = provider.loadOrCreateKey("ledger-secondary").useBytes()

            assertContentEquals(created, sameProcess)
            assertContentEquals(created, reloaded)
            assertFalse(created.contentEquals(secondAlias))
            assertFalse(
                File(root, "ledger-primary.keystore")
                    .readBytes()
                    .containsSubsequence(created),
            )
        }
    }

    @Test
    fun corruptionSwapAndMissingWrappingKeyFailClosedWithoutReplacement() = withTemporaryDirectory { root ->
        runTest {
            val protector = FakeAndroidKeyProtector()
            val provider = provider(root, protector)
            provider.loadOrCreateKey("ledger-primary").destroy()
            val primaryFile = File(root, "ledger-primary.keystore")
            val corrupted = primaryFile.readBytes().also { it[it.lastIndex] = (it.last() + 1).toByte() }
            primaryFile.writeBytes(corrupted)

            assertFailsWith<StorageProtectionException> {
                provider.loadOrCreateKey("ledger-primary")
            }
            assertContentEquals(corrupted, primaryFile.readBytes())

            provider.loadOrCreateKey("ledger-secondary").destroy()
            val swapped = File(root, "ledger-secondary.keystore").readBytes()
            primaryFile.writeBytes(swapped)
            assertFailsWith<StorageProtectionException> {
                provider.loadKey("ledger-primary")
            }
            assertContentEquals(swapped, primaryFile.readBytes())

            primaryFile.writeBytes(corrupted)
            protector.forget("ledger-primary")
            assertFailsWith<StorageProtectionException> {
                provider.loadOrCreateKey("ledger-primary")
            }
            assertContentEquals(corrupted, primaryFile.readBytes())
        }
    }

    @Test
    fun concurrentProvidersConvergeOnOnePersistedKey() = withTemporaryDirectory { root ->
        val protector = FakeAndroidKeyProtector()
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures = List(2) {
                executor.submit<ByteArray> {
                    start.await()
                    runBlocking {
                        provider(root, protector)
                            .loadOrCreateKey("ledger-primary")
                            .useBytes()
                    }
                }
            }
            start.countDown()

            assertContentEquals(futures[0].get(), futures[1].get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun rejectsAliasesThatCouldEscapeTheVault() = withTemporaryDirectory { root ->
        runTest {
            val provider = provider(root, FakeAndroidKeyProtector())
            assertFailsWith<IllegalArgumentException> { provider.loadOrCreateKey("../ledger") }
            assertFailsWith<IllegalArgumentException> { provider.loadOrCreateKey("Ledger Primary") }
            assertNull(provider.loadKey("valid-ledger_1"))
        }
    }
}

private fun provider(
    root: File,
    protector: AndroidKeyProtector,
): AndroidKeystoreDatabaseKeyProvider =
    AndroidKeystoreDatabaseKeyProvider(
        rootDirectory = root,
        protector = protector,
        secureRandom = SecureRandom(),
        fileOperations = JvmVaultFileOperations,
    )

private inline fun withTemporaryDirectory(block: (File) -> Unit) {
    val root = Files.createTempDirectory("hengji-android-key-test-").toFile()
    try {
        block(root)
    } finally {
        root.walkBottomUp().forEach(File::delete)
    }
}

private class FakeAndroidKeyProtector : AndroidKeyProtector {
    private val keys = ConcurrentHashMap<String, SecretKey>()

    override fun protect(
        alias: String,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): AndroidProtectedKeyBlob {
        val key = keys.computeIfAbsent(alias) {
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        return AndroidProtectedKeyBlob(cipher.iv.copyOf(), cipher.doFinal(plaintext))
    }

    override fun unprotect(
        alias: String,
        blob: AndroidProtectedKeyBlob,
        associatedData: ByteArray,
    ): ByteArray {
        val key = keys[alias] ?: error("Wrapping key is unavailable")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(blob.ciphertext)
    }

    fun forget(alias: String) {
        keys.remove(alias)
    }
}

private object JvmVaultFileOperations : AndroidVaultFileOperations {
    override fun isRegularDirectory(directory: File): Boolean =
        directory.isDirectory && !Files.isSymbolicLink(directory.toPath())

    override fun isRegularFile(file: File): Boolean =
        file.isFile && !Files.isSymbolicLink(file.toPath())

    override fun publishWithoutReplacing(source: File, target: File) {
        Files.createLink(target.toPath(), source.toPath())
    }

    override fun syncDirectory(directory: File) = Unit
}

private fun DatabaseKeyMaterial.useBytes(): ByteArray =
    try {
        copyBytes()
    } finally {
        destroy()
    }

private fun ByteArray.containsSubsequence(candidate: ByteArray): Boolean {
    if (candidate.isEmpty() || candidate.size > size) return false
    return indices
        .take(size - candidate.size + 1)
        .any { start -> candidate.indices.all { offset -> this[start + offset] == candidate[offset] } }
}
