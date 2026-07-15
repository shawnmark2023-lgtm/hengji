package com.hengji.data

import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LedgerJsonCodecTest {
    @Test
    fun fullSnapshotRoundTripsWithoutLosingBatchOrPreferences() {
        val imported = importedTransaction("json-import", "hj1_json")
        val snapshot = DemoLedger.snapshot().copy(
            revision = 42,
            transactions = DemoLedger.snapshot().transactions + imported,
            insightPreferences = InsightPreferenceRecord(
                mutedTypes = setOf("BUDGET_PACE"),
                ignoredDeduplicationKeys = setOf("merchant:demo"),
                updatedAtEpochMillis = 100,
            ),
            importBatches = listOf(
                ImportBatchRecord(
                    batchId = "batch_json_001",
                    sourceConnectorId = "csv-local",
                    sourceDigest = "sha256:demo",
                    state = ImportBatchState.COMMITTED,
                    createdAtEpochMillis = 10,
                    committedAtEpochMillis = 20,
                    items = listOf(ImportBatchItemRecord(imported.id.value, "hj1_json")),
                ),
            ),
        )

        val restored = LedgerJsonCodec.restore(LedgerJsonCodec.export(snapshot))

        assertEquals(snapshot, restored)
        assertTrue("\"schemaVersion\": 1" in LedgerJsonCodec.export(snapshot))
    }

    @Test
    fun migratesLegacySchemaZeroToVersionOne() {
        val legacy = """{"revision":7,"transactions":[],"assets":[]}"""

        val restored = LedgerJsonCodec.restore(legacy)

        assertEquals(7, restored.revision)
        assertTrue(restored.transactions.isEmpty())
        assertTrue(restored.importBatches.isEmpty())
    }

    @Test
    fun rejectsFutureSchema() {
        assertFailsWith<IllegalArgumentException> {
            LedgerJsonCodec.restore("""{"schemaVersion":999}""")
        }
    }

    private fun importedTransaction(id: String, fingerprint: String) = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.EXPENSE,
        amount = Money(1_234, CurrencyCode.CNY),
        bookedOn = LocalDate(2026, 7, 15),
        categoryId = CategoryId("test"),
        source = TransactionSource.FILE_IMPORT,
        importFingerprint = fingerprint,
    )
}
