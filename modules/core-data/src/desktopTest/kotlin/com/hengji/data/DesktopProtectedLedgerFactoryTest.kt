package com.hengji.data

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DesktopProtectedLedgerFactoryTest {
    @Test
    fun windowsFactoryPersistsWithDpapiAndNeverWritesPlaintextLedger() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        withDirectory { directory ->
            runTest {
                val first = openDesktopProtectedLedger(directory)
                first.repository.replaceWith(DemoLedger.snapshot())
                val expected = first.repository.snapshot(includeDeleted = true)

                val reopened = openDesktopProtectedLedger(directory)
                assertEquals(expected, reopened.repository.snapshot(includeDeleted = true))
                assertEquals(ProtectedLedgerOpenOutcome.OPENED_EXISTING, reopened.outcome)
                assertTrue(Files.isRegularFile(directory.resolve("hengji.ledger.hjenc")))
                assertTrue(Files.isRegularFile(directory.resolve("key-vault/hengji-ledger-primary.dpapi")))
                assertFalse(Files.exists(directory.resolve("hengji.db")))
                assertFalse(Files.readString(directory.resolve("hengji.ledger.hjenc")).contains("asset-headphones"))
            }

            Files.delete(directory.resolve("hengji.ledger.hjenc"))
            assertFailsWith<StorageProtectionException> {
                runTest { openDesktopProtectedLedger(directory) }
            }
            assertFalse(Files.exists(directory.resolve("hengji.ledger.hjenc")))
            assertFalse(Files.exists(directory.resolve("hengji.db")))
        }
    }

    @Test
    fun windowsFactoryClearRemainsEmptyAcrossProtectedReopen() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        withDirectory { directory ->
            runTest {
                val first = openDesktopProtectedLedger(directory)
                first.repository.replaceWith(DemoLedger.snapshot())
                val populatedRevision = first.repository.snapshot(includeDeleted = true).revision

                first.repository.clear()
                val cleared = first.repository.snapshot(includeDeleted = true)
                assertEquals(populatedRevision + 1, cleared.revision)
                assertTrue(cleared.transactions.isEmpty())
                assertTrue(cleared.assets.isEmpty())
                assertEquals(InsightPreferenceRecord(), cleared.insightPreferences)
                assertTrue(cleared.importBatches.isEmpty())

                val reopened = openDesktopProtectedLedger(directory)
                assertEquals(ProtectedLedgerOpenOutcome.OPENED_EXISTING, reopened.outcome)
                assertEquals(cleared, reopened.repository.snapshot(includeDeleted = true))
            }
        }
    }

    @Test
    fun windowsFactoryPersistsDeleteAndExactTokenRestoreAcrossReopen() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        withDirectory { directory ->
            runTest {
                val transaction = DemoLedger.snapshot().transactions.first()
                val deletionToken = 123L
                val first = openDesktopProtectedLedger(directory)
                first.repository.replaceWith(DemoLedger.snapshot())
                assertTrue(first.repository.softDeleteTransaction(transaction.id, deletionToken))

                val deletedReopen = openDesktopProtectedLedger(directory)
                assertFalse(deletedReopen.repository.snapshot().transactions.any { it.id == transaction.id })
                assertEquals(
                    deletionToken,
                    deletedReopen.repository
                        .snapshot(includeDeleted = true)
                        .transactions
                        .single { it.id == transaction.id }
                        .deletedAtEpochMillis,
                )
                assertTrue(deletedReopen.repository.restoreTransaction(transaction.id, deletionToken))

                val restoredReopen = openDesktopProtectedLedger(directory)
                assertTrue(restoredReopen.repository.snapshot().transactions.any { it.id == transaction.id })
                assertEquals(
                    null,
                    restoredReopen.repository
                        .snapshot(includeDeleted = true)
                        .transactions
                        .single { it.id == transaction.id }
                        .deletedAtEpochMillis,
                )
            }
        }
    }

    private fun withDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("hengji-protected-factory-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
