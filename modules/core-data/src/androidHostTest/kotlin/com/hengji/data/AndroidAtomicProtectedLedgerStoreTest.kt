package com.hengji.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AndroidAtomicProtectedLedgerStoreTest {
    @Test
    fun compareAndSwapCreatesReplacesAndRejectsStaleExpectedValue() = withStore { store, _ ->
        runTest {
            assertNull(store.readEnvelope())
            assertTrue(store.compareAndSwap(null, "first"))
            assertFalse(store.compareAndSwap(null, "lost"))
            assertFalse(store.compareAndSwap("stale", "lost"))
            assertTrue(store.compareAndSwap("first", "second"))
            assertEquals("second", store.readEnvelope())
        }
    }

    @Test
    fun independentInstancesCannotOverwriteAStaleEnvelope() = withStore { first, target ->
        runTest {
            val second = AndroidAtomicProtectedLedgerStore(target, HostLedgerFileOperations)
            assertTrue(first.compareAndSwap(null, "initial"))
            val stale = second.readEnvelope()
            assertTrue(first.compareAndSwap("initial", "winner"))
            assertFalse(second.compareAndSwap(stale, "loser"))
            assertEquals("winner", second.readEnvelope())
        }
    }

    @Test
    fun malformedUtf8AndNonRegularTargetsFailClosed() = withStore { store, target ->
        runTest {
            target.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
            assertFailsWith<StorageProtectionException> { store.readEnvelope() }
            assertTrue(target.delete())
            assertTrue(target.mkdir())
            assertFailsWith<StorageProtectionException> { store.readEnvelope() }
        }
    }

    private fun withStore(block: (AndroidAtomicProtectedLedgerStore, File) -> Unit) {
        val directory = Files.createTempDirectory("hengji-android-store-").toFile()
        val target = File(directory, "ledger.hjenc")
        try {
            block(AndroidAtomicProtectedLedgerStore(target, HostLedgerFileOperations), target)
        } finally {
            Files.walk(directory.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}

private object HostLedgerFileOperations : AndroidLedgerFileOperations {
    override fun isRegularDirectory(directory: File): Boolean =
        directory.isDirectory && !Files.isSymbolicLink(directory.toPath())

    override fun isRegularFile(file: File): Boolean =
        file.isFile && !Files.isSymbolicLink(file.toPath())

    override fun publishWithoutReplacing(source: File, target: File) {
        Files.move(source.toPath(), target.toPath())
    }

    override fun publishReplacing(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    override fun syncDirectory(directory: File) = Unit
}
