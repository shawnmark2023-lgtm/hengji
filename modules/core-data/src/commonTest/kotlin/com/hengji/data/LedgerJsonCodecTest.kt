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
        val assetWithSaleTarget = DemoLedger.snapshot().assets.first().copy(
            saleTargetPrice = Money(18_800, CurrencyCode.CNY),
        )
        val snapshot = DemoLedger.snapshot().copy(
            revision = 42,
            transactions = DemoLedger.snapshot().transactions + imported,
            assets = DemoLedger.snapshot().assets.map {
                if (it.id == assetWithSaleTarget.id) assetWithSaleTarget else it
            },
            insightPreferences = InsightPreferenceRecord(
                mutedTypes = setOf("BUDGET_PACE"),
                ignoredDeduplicationKeys = setOf("merchant:demo"),
                updatedAtEpochMillis = 100,
                adoptedDeduplicationKeys = setOf("budget:monthly:pace"),
                snoozedUntilEpochMillisByKey = mapOf("spending:trend-increase" to 200),
                feedbackTypeByKey = mapOf(
                    "merchant:demo" to "MERCHANT_CONCENTRATION",
                    "budget:monthly:pace" to "BUDGET_PACE",
                    "spending:trend-increase" to "SPENDING_TREND",
                ),
                personalAiEnabled = false,
                onboardingCompletedAtEpochMillis = 90,
                personalAnalysisHistory = listOf(
                    PersonalAnalysisRecord(
                        createdAtEpochMillis = 80,
                        localDeduplicationKey = "spending:trend-increase",
                        headline = "最近支出有变化",
                        summary = "先从变化最大的类别开始看看。",
                        actionLabel = "查看支出分类",
                        evidenceCodes = listOf("change_basis_points"),
                    ),
                ),
                monthlyBudgetMinor = 650_000,
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
        assertEquals(Money(18_800, CurrencyCode.CNY), restored.assets.first().saleTargetPrice)
        assertTrue("\"schemaVersion\": 6" in LedgerJsonCodec.export(snapshot))
    }

    @Test
    fun migratesLegacySchemaZeroToCurrentVersion() {
        val legacy = """{"revision":7,"transactions":[],"assets":[]}"""

        val restored = LedgerJsonCodec.restore(legacy)

        assertEquals(7, restored.revision)
        assertTrue(restored.transactions.isEmpty())
        assertTrue(restored.importBatches.isEmpty())
    }

    @Test
    fun deletionTombstoneRoundTripsAndRestoredExportClearsIt() {
        val deleted = importedTransaction("json-deleted", "hj1_json_deleted").copy(
            deletedAtEpochMillis = 123,
        )
        val snapshot = LedgerSnapshot(
            revision = 5,
            transactions = listOf(deleted),
            assets = emptyList(),
            maintenanceCosts = emptyList(),
            usageEvents = emptyList(),
            marketQuotes = emptyList(),
        )

        val restored = LedgerJsonCodec.restore(LedgerJsonCodec.export(snapshot))
        assertEquals(123, restored.transactions.single().deletedAtEpochMillis)

        val repository = InMemoryLedgerRepository(restored)
        assertTrue(repository.restoreTransaction(deleted.id, expectedDeletedAtEpochMillis = 123))
        val restoredExport = LedgerJsonCodec.export(repository.snapshot(includeDeleted = true))
        assertTrue("\"deletedAtEpochMillis\": null" in restoredExport)
        assertEquals(
            null,
            LedgerJsonCodec.restore(restoredExport).transactions.single().deletedAtEpochMillis,
        )
    }

    @Test
    fun migratesSchemaOnePreferencesToVersionTwoDefaults() {
        val legacy = """
            {
              "schemaVersion": 1,
              "revision": 8,
              "insightPreferences": {
                "mutedTypes": ["BUDGET_PACE"],
                "ignoredDeduplicationKeys": ["ignored:key"],
                "updatedAtEpochMillis": 77
              }
            }
        """.trimIndent()

        val restored = LedgerJsonCodec.restore(legacy)

        assertEquals(setOf("BUDGET_PACE"), restored.insightPreferences.mutedTypes)
        assertEquals(setOf("ignored:key"), restored.insightPreferences.ignoredDeduplicationKeys)
        assertEquals(77, restored.insightPreferences.updatedAtEpochMillis)
        assertTrue(restored.insightPreferences.adoptedDeduplicationKeys.isEmpty())
        assertTrue(restored.insightPreferences.snoozedUntilEpochMillisByKey.isEmpty())
        assertTrue(restored.insightPreferences.feedbackTypeByKey.isEmpty())
        assertTrue(restored.insightPreferences.personalAiEnabled)
        assertEquals(null, restored.insightPreferences.onboardingCompletedAtEpochMillis)
        assertTrue(restored.insightPreferences.personalAnalysisHistory.isEmpty())
        assertEquals(null, restored.insightPreferences.monthlyBudgetMinor)
    }

    @Test
    fun migratesSchemaFiveBudgetToUnsetWithoutInventingAValue() {
        val legacy = """
            {
              "schemaVersion": 5,
              "revision": 12,
              "insightPreferences": {
                "personalAiEnabled": true,
                "personalAnalysisHistory": []
              }
            }
        """.trimIndent()

        val restored = LedgerJsonCodec.restore(legacy)

        assertEquals(null, restored.insightPreferences.monthlyBudgetMinor)
        assertTrue("\"monthlyBudgetMinor\": null" in LedgerJsonCodec.export(restored))
    }

    @Test
    fun migratesSchemaTwoAssetSaleTargetToNull() {
        val legacy = """
            {
              "schemaVersion": 2,
              "revision": 9,
              "assets": [{
                "id": "asset-v2",
                "name": "Legacy asset",
                "categoryId": "electronics",
                "purchaseMinorUnits": 123400,
                "currency": "CNY",
                "purchasedOn": "2026-01-02",
                "status": "ACTIVE"
              }]
            }
        """.trimIndent()

        val restored = LedgerJsonCodec.restore(legacy)

        assertEquals(null, restored.assets.single().saleTargetPrice)
        assertTrue("\"saleTargetMinorUnits\": null" in LedgerJsonCodec.export(restored))
    }

    @Test
    fun restoreRejectsQuoteWhoseCurrencyDiffersFromAsset() {
        val seed = DemoLedger.snapshot()
        val invalid = seed.copy(
            marketQuotes = seed.marketQuotes.mapIndexed { index, quote ->
                if (index == 0) {
                    quote.copy(
                        price = Money(100, CurrencyCode("USD")),
                        shipping = Money.zero(CurrencyCode("USD")),
                    )
                } else {
                    quote
                }
            },
        )

        assertFailsWith<IllegalArgumentException> {
            LedgerJsonCodec.restore(LedgerJsonCodec.export(invalid))
        }
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
