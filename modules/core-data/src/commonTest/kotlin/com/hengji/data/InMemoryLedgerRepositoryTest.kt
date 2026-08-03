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
import kotlin.test.assertFailsWith
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
    fun deletingAssetPurchaseTransactionDoesNotCascadeAssetCostRecords() {
        val initial = DemoLedger.snapshot()
        val transaction = initial.transactions.first { it.assetId != null }
        val repository = InMemoryLedgerRepository(initial)

        assertTrue(repository.softDeleteTransaction(transaction.id, deletedAtEpochMillis = 123))

        val visible = repository.snapshot()
        assertEquals(initial.assets, visible.assets)
        assertEquals(initial.maintenanceCosts, visible.maintenanceCosts)
        assertEquals(initial.usageEvents, visible.usageEvents)
        assertEquals(initial.marketQuotes, visible.marketQuotes)
        assertFalse(visible.transactions.any { it.id == transaction.id })
    }

    @Test
    fun restoreRequiresExactDeletionTokenAndOnlySuccessAdvancesRevision() {
        val repository = InMemoryLedgerRepository()
        val transaction = importedTransaction("restore-me", "restore-fingerprint")
        repository.upsertTransaction(transaction)
        assertTrue(repository.softDeleteTransaction(transaction.id, deletedAtEpochMillis = 123))
        val deletedRevision = repository.snapshot().revision

        assertFalse(repository.restoreTransaction(transaction.id, expectedDeletedAtEpochMillis = 122))
        assertFalse(repository.restoreTransaction(TransactionId("unknown"), expectedDeletedAtEpochMillis = 123))
        assertEquals(deletedRevision, repository.snapshot().revision)
        assertTrue(repository.snapshot().transactions.isEmpty())
        assertEquals(123, repository.snapshot(includeDeleted = true).transactions.single().deletedAtEpochMillis)

        assertTrue(repository.restoreTransaction(transaction.id, expectedDeletedAtEpochMillis = 123))
        assertEquals(deletedRevision + 1, repository.snapshot().revision)
        assertEquals(null, repository.snapshot().transactions.single().deletedAtEpochMillis)
        assertFalse(repository.restoreTransaction(transaction.id, expectedDeletedAtEpochMillis = 123))
        assertEquals(deletedRevision + 1, repository.snapshot().revision)
        assertFailsWith<IllegalArgumentException> {
            repository.restoreTransaction(transaction.id, expectedDeletedAtEpochMillis = -1)
        }
    }

    @Test
    fun deletedTransactionFingerprintRemainsReserved() {
        val repository = InMemoryLedgerRepository()
        val transaction = importedTransaction("original-import", "reserved-fingerprint")
        repository.upsertTransaction(transaction)
        assertTrue(repository.softDeleteTransaction(transaction.id, deletedAtEpochMillis = 123))
        val deletedRevision = repository.snapshot().revision

        assertEquals(
            UpsertTransactionResult.DUPLICATE_IMPORT_SKIPPED,
            repository.upsertTransaction(importedTransaction("reimport", "reserved-fingerprint")),
        )
        assertEquals(deletedRevision, repository.snapshot().revision)
        assertEquals(
            listOf(transaction.id),
            repository.snapshot(includeDeleted = true).transactions.map { it.id },
        )
    }

    @Test
    fun activeRefundPreventsDeletingItsOriginalExpense() {
        val repository = InMemoryLedgerRepository()
        val expense = importedTransaction("expense-with-refund", "expense-fingerprint")
        val refund = expense.copy(
            id = TransactionId("active-refund"),
            kind = TransactionKind.REFUND,
            originalTransactionId = expense.id,
            importFingerprint = "refund-fingerprint",
        )
        repository.upsertTransaction(expense)
        repository.upsertTransaction(refund)
        val before = repository.snapshot().revision

        assertFalse(repository.softDeleteTransaction(expense.id, deletedAtEpochMillis = 123))
        assertEquals(before, repository.snapshot().revision)
        assertEquals(2, repository.snapshot().transactions.size)
    }

    @Test
    fun refundCannotBeRestoredAfterItsOriginalWasDeleted() {
        val repository = InMemoryLedgerRepository()
        val expense = importedTransaction("refund-original", "refund-original-fingerprint")
        val refund = expense.copy(
            id = TransactionId("refund-to-restore"),
            kind = TransactionKind.REFUND,
            originalTransactionId = expense.id,
            importFingerprint = "refund-to-restore-fingerprint",
        )
        repository.upsertTransaction(expense)
        repository.upsertTransaction(refund)
        assertTrue(repository.softDeleteTransaction(refund.id, deletedAtEpochMillis = 100))
        assertTrue(repository.softDeleteTransaction(expense.id, deletedAtEpochMillis = 200))
        val before = repository.snapshot().revision

        assertFalse(repository.restoreTransaction(refund.id, expectedDeletedAtEpochMillis = 100))
        assertEquals(before, repository.snapshot().revision)
        assertEquals(100, repository.snapshot(includeDeleted = true).singleTransaction(refund.id).deletedAtEpochMillis)
    }

    @Test
    fun replacementRevisionNeverMovesBackward() {
        val repository = InMemoryLedgerRepository(emptySnapshot(revision = 10))

        repository.replaceWith(emptySnapshot(revision = 2))
        assertEquals(11, repository.snapshot().revision)
        repository.replaceWith(emptySnapshot(revision = 20))
        assertEquals(21, repository.snapshot().revision)

        assertFailsWith<ArithmeticException> {
            repository.replaceWith(emptySnapshot(revision = Long.MAX_VALUE))
        }
        assertEquals(21, repository.snapshot().revision)
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
        assertTrue("\"schemaVersion\": 5" in json)
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

    @Test
    fun insightPreferencesCanBeSavedOverwrittenAndReset() {
        val repository: LedgerRepository = InMemoryLedgerRepository()
        val first = InsightPreferenceRecord(
            mutedTypes = setOf("BUDGET_PACE"),
            ignoredDeduplicationKeys = setOf("ignored:key"),
            updatedAtEpochMillis = 10,
            adoptedDeduplicationKeys = setOf("adopted:key"),
            snoozedUntilEpochMillisByKey = mapOf("snoozed:key" to 100),
        )
        repository.saveInsightPreferences(first)
        val firstRevision = repository.snapshot().revision
        assertEquals(first, repository.snapshot().insightPreferences)

        val overwritten = InsightPreferenceRecord(
            updatedAtEpochMillis = 20,
            adoptedDeduplicationKeys = setOf("new:key"),
        )
        repository.saveInsightPreferences(overwritten)
        assertEquals(overwritten, repository.snapshot().insightPreferences)
        assertTrue(repository.snapshot().revision > firstRevision)

        val reset = InsightPreferenceRecord(updatedAtEpochMillis = 30)
        repository.saveInsightPreferences(reset)
        assertEquals(reset, repository.snapshot().insightPreferences)
    }

    @Test
    fun insightPreferenceFeedbackStatesAreMutuallyExclusive() {
        assertFailsWith<IllegalArgumentException> {
            InsightPreferenceRecord(
                ignoredDeduplicationKeys = setOf("same:key"),
                adoptedDeduplicationKeys = setOf("same:key"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InsightPreferenceRecord(
                adoptedDeduplicationKeys = setOf("same:key"),
                snoozedUntilEpochMillisByKey = mapOf("same:key" to 100),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InsightPreferenceRecord(
                ignoredDeduplicationKeys = setOf("same:key"),
                snoozedUntilEpochMillisByKey = mapOf("same:key" to 100),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InsightPreferenceRecord(snoozedUntilEpochMillisByKey = mapOf("key" to -1))
        }
    }

    @Test
    fun marketQuoteMustUseReferencedAssetCurrency() {
        val seed = DemoLedger.snapshot()
        val repository = InMemoryLedgerRepository(seed)
        val quote = seed.marketQuotes.first().copy(
            id = "wrong-currency-memory",
            price = Money(100, CurrencyCode("USD")),
            shipping = Money.zero(CurrencyCode("USD")),
        )

        assertFailsWith<IllegalArgumentException> {
            repository.addMarketQuote(quote)
        }
        assertFalse(repository.snapshot().marketQuotes.any { it.id == quote.id })
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

    private fun emptySnapshot(revision: Long) = LedgerSnapshot(
        revision = revision,
        transactions = emptyList(),
        assets = emptyList(),
        maintenanceCosts = emptyList(),
        usageEvents = emptyList(),
        marketQuotes = emptyList(),
    )

    private fun LedgerSnapshot.singleTransaction(id: TransactionId): Transaction =
        transactions.single { it.id == id }
}
