package com.hengji.data.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
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
                InsightPreferenceRecord(
                    mutedTypes = setOf("SELL_CANDIDATE"),
                    ignoredDeduplicationKeys = setOf("asset:ignored"),
                    updatedAtEpochMillis = 99,
                    adoptedDeduplicationKeys = setOf("asset:adopted"),
                    snoozedUntilEpochMillisByKey = mapOf("asset:snoozed" to 999),
                ),
            )
            repository.close()

            repository = open(path)
            val snapshot = repository.snapshot()
            assertEquals(Long.MAX_VALUE, snapshot.transactions.single().amount.minorUnits)
            assertEquals(setOf("SELL_CANDIDATE"), snapshot.insightPreferences.mutedTypes)
            assertEquals(setOf("asset:ignored"), snapshot.insightPreferences.ignoredDeduplicationKeys)
            assertEquals(setOf("asset:adopted"), snapshot.insightPreferences.adoptedDeduplicationKeys)
            assertEquals(mapOf("asset:snoozed" to 999L), snapshot.insightPreferences.snoozedUntilEpochMillisByKey)
            assertEquals(99L, snapshot.insightPreferences.updatedAtEpochMillis)
            repository.close()
        }
    }

    @Test
    fun insightPreferencesCanBeOverwrittenAndResetAcrossReopen() = runTest {
        withDatabaseFile { path ->
            var repository = open(path)
            repository.saveInsightPreferences(
                InsightPreferenceRecord(
                    ignoredDeduplicationKeys = setOf("old:key"),
                    updatedAtEpochMillis = 10,
                ),
            )
            repository.saveInsightPreferences(
                InsightPreferenceRecord(
                    adoptedDeduplicationKeys = setOf("new:key"),
                    updatedAtEpochMillis = 20,
                ),
            )
            repository.close()

            repository = open(path)
            assertEquals(
                InsightPreferenceRecord(
                    adoptedDeduplicationKeys = setOf("new:key"),
                    updatedAtEpochMillis = 20,
                ),
                repository.snapshot().insightPreferences,
            )
            repository.saveInsightPreferences(InsightPreferenceRecord(updatedAtEpochMillis = 30))
            repository.close()

            repository = open(path)
            assertEquals(
                InsightPreferenceRecord(updatedAtEpochMillis = 30),
                repository.snapshot().insightPreferences,
            )
            repository.close()
        }
    }

    @Test
    fun migrationOneToTwoPreservesExistingPreferencesAndAddsEmptyFeedbackState() {
        withDatabaseFileBlocking { path ->
            BundledSQLiteDriver().open(path).use { connection ->
                connection.execSQL(
                    """
                    CREATE TABLE insight_preferences (
                        singletonId INTEGER NOT NULL PRIMARY KEY,
                        mutedTypesJson TEXT NOT NULL,
                        ignoredDeduplicationKeysJson TEXT NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO insight_preferences (
                        singletonId,
                        mutedTypesJson,
                        ignoredDeduplicationKeysJson,
                        updatedAtEpochMillis
                    ) VALUES (1, '["BUDGET_PACE"]', '["ignored:key"]', 77)
                    """.trimIndent(),
                )

                MIGRATION_1_2.migrate(connection)

                connection.prepare(
                    """
                    SELECT mutedTypesJson,
                           ignoredDeduplicationKeysJson,
                           updatedAtEpochMillis,
                           adoptedDeduplicationKeysJson,
                           snoozedUntilEpochMillisByKeyJson
                    FROM insight_preferences
                    WHERE singletonId = 1
                    """.trimIndent(),
                ).use { statement ->
                    assertTrue(statement.step())
                    assertEquals("""["BUDGET_PACE"]""", statement.getText(0))
                    assertEquals("""["ignored:key"]""", statement.getText(1))
                    assertEquals(77, statement.getLong(2))
                    assertEquals("[]", statement.getText(3))
                    assertEquals("{}", statement.getText(4))
                }
            }
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
