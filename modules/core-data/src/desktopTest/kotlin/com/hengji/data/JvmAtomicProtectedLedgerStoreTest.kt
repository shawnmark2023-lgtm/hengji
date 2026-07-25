package com.hengji.data

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class JvmAtomicProtectedLedgerStoreTest {
    @Test
    fun compareAndSwapCreatesReplacesAndRejectsStaleExpectedValue() = withStore { store, _ ->
        runTest {
            assertNull(store.readEnvelope())
            assertTrue(store.compareAndSwap(null, "first"))
            assertFalse(store.compareAndSwap(null, "lost"))
            assertFalse(store.compareAndSwap("stale", "lost"))
            assertEquals("first", store.readEnvelope())
            assertTrue(store.compareAndSwap("first", "second"))
            assertEquals("second", store.readEnvelope())
        }
    }

    @Test
    fun independentInstancesCannotOverwriteAStaleEnvelope() = withStore { first, path ->
        runTest {
            val second = JvmAtomicProtectedLedgerStore(path)
            assertTrue(first.compareAndSwap(null, "initial"))
            val stale = second.readEnvelope()
            assertTrue(first.compareAndSwap("initial", "winner"))
            assertFalse(second.compareAndSwap(stale, "loser"))
            assertEquals("winner", second.readEnvelope())
        }
    }

    @Test
    fun malformedUtf8AndNonRegularTargetsFailClosed() = withStore { store, path ->
        runTest {
            Files.write(path, byteArrayOf(0xC3.toByte(), 0x28))
            assertFailsWith<StorageProtectionException> { store.readEnvelope() }
            Files.delete(path)
            Files.createDirectory(path)
            assertFailsWith<StorageProtectionException> { store.readEnvelope() }
        }
    }

    @Test
    fun cancellationDuringDurableCommitStillPublishesCommittedState() = runTest {
        val store = BlockingProtectedLedgerStore()
        val repository = ProtectedLedgerRepository.open(
            store = store,
            keyAlias = "ledger-primary",
            keyProvider = DesktopTestKeyProvider(),
        ).repository
        store.blockWrites = true

        val mutation = async { repository.clear() }
        store.writeEntered.await()
        mutation.cancel()
        store.allowWrite.complete(Unit)
        mutation.join()

        assertTrue(mutation.isCancelled)
        assertEquals(1, repository.snapshot().revision)
    }

    private fun withStore(block: (JvmAtomicProtectedLedgerStore, Path) -> Unit) {
        val directory = Files.createTempDirectory("hengji-protected-store-")
        val path = directory.resolve("ledger.hjenc")
        try {
            block(JvmAtomicProtectedLedgerStore(path), path)
        } finally {
            Files.list(directory).use { children ->
                children.forEach(Files::deleteIfExists)
            }
            Files.deleteIfExists(directory)
        }
    }
}

private class DesktopTestKeyProvider : ProvisioningDatabaseKeyProvider {
    private val key = ByteArray(32) { 17 }
    private var available = false

    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? =
        if (available) DatabaseKeyMaterial(key) else null

    override suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial {
        available = true
        return DatabaseKeyMaterial(key)
    }
}

private class BlockingProtectedLedgerStore : ProtectedLedgerStore {
    private var envelope: String? = null
    var blockWrites: Boolean = false
    val writeEntered = CompletableDeferred<Unit>()
    val allowWrite = CompletableDeferred<Unit>()

    override suspend fun readEnvelope(): String? = envelope

    override suspend fun compareAndSwap(expectedEnvelope: String?, replacementEnvelope: String): Boolean {
        if (blockWrites) {
            writeEntered.complete(Unit)
            allowWrite.await()
        }
        if (envelope != expectedEnvelope) return false
        envelope = replacementEnvelope
        return true
    }
}
