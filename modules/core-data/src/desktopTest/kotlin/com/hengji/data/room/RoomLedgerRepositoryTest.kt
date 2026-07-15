package com.hengji.data.room

import com.hengji.data.CommitImportBatchRequest
import com.hengji.data.ImportBatchCommitStatus
import com.hengji.data.ImportBatchState
import com.hengji.data.InsightPreferenceRecord
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomLedgerRepositoryTest {
    @Test
    fun persistsSnapshotAndLongMinorUnitsAcrossReopen() = runTest {
        withDatabaseFile { path ->
            var repository = open(path)
            val transaction = importedTransaction("tx-max", "hj1_max", Long.MAX_VALUE)
            repository.upsertTransaction(transaction)
            repository.saveInsightPreferences(
                InsightPreferenceRecord(setOf("SELL_CANDIDATE"), setOf("asset:demo"), 99),
            )
            repository.close()

            repository = open(path)
            val snapshot = repository.snapshot()
            assertEquals(Long.MAX_VALUE, snapshot.transactions.single().amount.minorUnits)
            assertEquals(setOf("SELL_CANDIDATE"), snapshot.insightPreferences.mutedTypes)
            repository.close()
        }
    }

    @Test
    fun importCommitIsIdempotentAndRollbackIsAtomic() = runTest {
        withDatabaseFile { path ->
            val repository = open(path)
            val request = CommitImportBatchRequest(
                batchId = "batch_room_001",
                sourceConnectorId = "csv-local",
                sourceDigest = "sha256:stable",
                createdAtEpochMillis = 10,
                committedAtEpochMillis = 20,
                transactions = listOf(
                    importedTransaction("room-1", "hj1_room_1", 100),
                    importedTransaction("room-2", "hj1_room_2", 200),
                ),
            )

            val first = repository.commitImportBatch(request)
            val second = repository.commitImportBatch(request)
            assertEquals(ImportBatchCommitStatus.COMMITTED, first.status)
            assertEquals(listOf("room-1", "room-2"), first.insertedTransactionIds)
            assertEquals(ImportBatchCommitStatus.ALREADY_COMMITTED, second.status)
            assertEquals(2, repository.snapshot().transactions.size)

            val rollback = repository.rollbackImportBatch("batch_room_001", 30)
            assertFalse(rollback.alreadyRolledBack)
            assertEquals(setOf("room-1", "room-2"), rollback.removedTransactionIds.toSet())
            val after = repository.snapshot(includeDeleted = true)
            assertTrue(after.transactions.isEmpty())
            assertEquals(ImportBatchState.ROLLED_BACK, after.importBatches.single().state)
            assertTrue(repository.rollbackImportBatch("batch_room_001", 40).alreadyRolledBack)
            repository.close()
        }
    }

    @Test
    fun clearRemainsEmptyAndNonPristineAcrossReopen() = runTest {
        withDatabaseFile { path ->
            var repository = open(path)
            repository.upsertTransaction(importedTransaction("clear-me", "hj1_clear_me", 1234))
            repository.clear()
            val clearedRevision = repository.snapshot().revision
            assertTrue(clearedRevision > 0)
            assertTrue(repository.snapshot().transactions.isEmpty())
            repository.close()

            repository = open(path)
            val reopened = repository.snapshot()
            assertEquals(clearedRevision, reopened.revision)
            assertTrue(reopened.transactions.isEmpty())
            assertTrue(reopened.assets.isEmpty())
            repository.close()
        }
    }

    @Test
    fun encryptedProductionPolicyFailsClosed() {
        withDatabaseFileBlocking { path ->
            assertFailsWith<DatabaseEncryptionUnavailableException> {
                createDesktopLedgerRepository(path, RoomStoragePolicy.REQUIRE_APPLICATION_ENCRYPTION)
            }
        }
    }

    private fun open(path: String) = createDesktopLedgerRepository(
        path,
        RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
    )

    private fun importedTransaction(id: String, fingerprint: String, minor: Long) = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.EXPENSE,
        amount = Money(minor, CurrencyCode.CNY),
        bookedOn = LocalDate(2026, 7, 15),
        categoryId = CategoryId("test"),
        source = TransactionSource.FILE_IMPORT,
        importFingerprint = fingerprint,
    )

    private suspend fun withDatabaseFile(block: suspend (String) -> Unit) {
        val file = File.createTempFile("hengji-room-", ".db").also { it.delete() }
        try {
            block(file.absolutePath)
        } finally {
            deleteDatabaseFiles(file)
        }
    }

    private fun withDatabaseFileBlocking(block: (String) -> Unit) {
        val file = File.createTempFile("hengji-room-", ".db").also { it.delete() }
        try {
            block(file.absolutePath)
        } finally {
            deleteDatabaseFiles(file)
        }
    }

    private fun deleteDatabaseFiles(file: File) {
        listOf(file, File(file.path + "-wal"), File(file.path + "-shm")).forEach { it.delete() }
    }
}
