package com.hengji.data

import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryLedgerRepositoryTest {
    @Test
    fun duplicateImportFingerprintIsSkipped() {
        val repository = InMemoryLedgerRepository()
        val first = importedTransaction("first", "same-fingerprint")
        val duplicate = importedTransaction("second", "same-fingerprint")

        assertEquals(UpsertTransactionResult.INSERTED, repository.upsertTransaction(first))
        assertEquals(UpsertTransactionResult.DUPLICATE_IMPORT_SKIPPED, repository.upsertTransaction(duplicate))
        assertEquals(listOf(first), repository.snapshot().transactions)
    }

    @Test
    fun softDeleteIsHiddenButCanBeExportedForRecovery() {
        val repository = InMemoryLedgerRepository()
        val transaction = importedTransaction("delete-me", "delete-fingerprint")
        repository.upsertTransaction(transaction)

        assertTrue(repository.softDeleteTransaction(transaction.id, deletedAtEpochMillis = 123))
        assertFalse(repository.softDeleteTransaction(transaction.id, deletedAtEpochMillis = 456))
        assertTrue(repository.snapshot().transactions.isEmpty())
        assertEquals(1, repository.snapshot(includeDeleted = true).transactions.size)
    }

    @Test
    fun demoSnapshotHasOnlyClearlyMarkedDemoQuotes() {
        val snapshot = DemoLedger.snapshot()
        assertTrue(snapshot.assets.isNotEmpty())
        assertTrue(snapshot.marketQuotes.isNotEmpty())
        assertTrue(snapshot.marketQuotes.all { !it.isLiveSource })
        assertTrue(snapshot.marketQuotes.all { it.sourceUrl == null })
    }

    @Test
    fun jsonExportDeclaresSchemaAndDoesNotInventIdentityFields() {
        val json = LedgerJsonExporter.export(DemoLedger.snapshot())
        assertTrue("\"schemaVersion\": 1" in json)
        assertTrue("\"transactions\"" in json)
        assertFalse("\"phone\"" in json.lowercase())
        assertFalse("\"email\"" in json.lowercase())
    }

    @Test
    fun snapshotReplaceAndClearPreserveOrResetExtendedMetadata() {
        val transaction = importedTransaction("metadata", "metadata-fingerprint")
        val preferences = InsightPreferenceRecord(setOf("BUDGET_PACE"), setOf("demo:key"), 123)
        val batch = ImportBatchRecord(
            batchId = "batch_meta_001",
            sourceConnectorId = "csv-local",
            sourceDigest = "sha256:meta",
            state = ImportBatchState.COMMITTED,
            createdAtEpochMillis = 10,
            committedAtEpochMillis = 20,
            items = listOf(ImportBatchItemRecord(transaction.id.value, "metadata-fingerprint")),
        )
        val repository = InMemoryLedgerRepository(
            LedgerSnapshot(
                revision = 4,
                transactions = listOf(transaction),
                assets = emptyList(),
                maintenanceCosts = emptyList(),
                usageEvents = emptyList(),
                marketQuotes = emptyList(),
                insightPreferences = preferences,
                importBatches = listOf(batch),
            ),
        )

        assertEquals(preferences, repository.snapshot().insightPreferences)
        assertEquals(listOf(batch), repository.snapshot().importBatches)
        repository.clear()
        assertEquals(InsightPreferenceRecord(), repository.snapshot().insightPreferences)
        assertTrue(repository.snapshot().importBatches.isEmpty())
    }

    private fun importedTransaction(id: String, fingerprint: String) = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.EXPENSE,
        amount = Money(1_234, CurrencyCode.CNY),
        bookedOn = LocalDate(2026, 7, 15),
        categoryId = CategoryId("test"),
        merchant = Merchant("测试商户"),
        source = TransactionSource.FILE_IMPORT,
        importFingerprint = fingerprint,
    )
}
