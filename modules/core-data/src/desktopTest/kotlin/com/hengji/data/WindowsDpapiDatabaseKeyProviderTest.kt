package com.hengji.data

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class WindowsDpapiDatabaseKeyProviderTest {
    @Test
    fun provisionsReloadsAndSeparatesCurrentUserKeys() = withTemporaryDirectory { root ->
        runTest {
            val firstProvider = WindowsDpapiDatabaseKeyProvider(root)
            assertNull(firstProvider.loadKey("ledger-primary"))

            val created = firstProvider.loadOrCreateKey("ledger-primary").useBytes()
            val sameProcess = firstProvider.loadOrCreateKey("ledger-primary").useBytes()
            val reloaded = WindowsDpapiDatabaseKeyProvider(root).loadKey("ledger-primary")!!.useBytes()
            val secondAlias = firstProvider.loadOrCreateKey("ledger-secondary").useBytes()

            assertContentEquals(created, sameProcess)
            assertContentEquals(created, reloaded)
            assertFalse(created.contentEquals(secondAlias))
            assertFalse(root.resolve("ledger-primary.dpapi").readBytes().containsSubsequence(created))
        }
    }

    @Test
    fun corruptedBlobFailsClosedAndIsNotReplaced() = withTemporaryDirectory { root ->
        runTest {
            val provider = WindowsDpapiDatabaseKeyProvider(root)
            provider.loadOrCreateKey("ledger-primary").destroy()
            val keyFile = root.resolve("ledger-primary.dpapi")
            val corrupted = keyFile.readBytes().also { it[it.lastIndex] = (it.last() + 1).toByte() }
            Files.write(keyFile, corrupted)

            assertFailsWith<StorageProtectionException> {
                provider.loadOrCreateKey("ledger-primary")
            }
            assertContentEquals(corrupted, keyFile.readBytes())

            provider.loadOrCreateKey("ledger-secondary").destroy()
            val swapped = root.resolve("ledger-secondary.dpapi").readBytes()
            Files.write(keyFile, swapped)
            assertFailsWith<StorageProtectionException> {
                provider.loadKey("ledger-primary")
            }
            assertContentEquals(swapped, keyFile.readBytes())
        }
    }

    @Test
    fun concurrentProvidersConvergeOnOnePersistedKey() = withTemporaryDirectory { root ->
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures = List(2) {
                executor.submit<ByteArray> {
                    start.await()
                    runBlocking {
                        WindowsDpapiDatabaseKeyProvider(root)
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
            val provider = WindowsDpapiDatabaseKeyProvider(root)
            assertFailsWith<IllegalArgumentException> { provider.loadOrCreateKey("../ledger") }
            assertFailsWith<IllegalArgumentException> { provider.loadOrCreateKey("Ledger Primary") }
            assertNull(provider.loadKey("valid-ledger_1"))
        }
    }
}

private inline fun withTemporaryDirectory(block: (Path) -> Unit) {
    val root = Files.createTempDirectory("hengji-dpapi-test-")
    try {
        block(root)
    } finally {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
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
