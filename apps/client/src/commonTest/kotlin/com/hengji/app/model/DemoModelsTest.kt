package com.hengji.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.hengji.domain.QuoteProvenance
import kotlinx.datetime.LocalDate
import com.hengji.data.InsightPreferenceRecord
import com.hengji.data.LedgerSnapshot
import com.hengji.insights.InsightFeedback

class DemoModelsTest {
    @Test
    fun parsesMoneyWithoutUsingFloatingPoint() {
        assertEquals(12_340L, parseMoneyToMinor("¥123.40"))
        assertEquals(1_200L, parseMoneyToMinor("12"))
        assertEquals(-99L, parseMoneyToMinor("-0.99"))
        assertNull(parseMoneyToMinor("12.345"))
        assertNull(parseMoneyToMinor("not money"))
        assertNull(parseMoneyToMinor("92233720368547758.08"))
    }

    @Test
    fun formatsMinorUnitsPredictably() {
        assertEquals("¥12,345.67", formatMoney(1_234_567L))
        assertEquals("−¥4.59", formatMoney(-459L))
        assertEquals("+¥8.00", formatMoney(800L, showSign = true))
    }

    @Test
    fun derivesAssetCostMetricsFromIntegerMinorUnits() {
        val asset = DemoAsset(
            id = "test",
            name = "Test",
            variant = "Test",
            ownedDays = 10,
            usageCount = 4,
            totalCostMinor = 10_000,
            currentValueMinor = 4_000,
            marketLowMinor = 3_500,
            marketHighMinor = 4_500,
            marketConfidence = 80,
            quoteUpdatedLabel = "test",
        )

        assertEquals(1_000, asset.dailyCostMinor)
        assertEquals(600, asset.netDailyCostMinor)
        assertEquals(1_500, asset.costPerUseMinor)
    }

    @Test
    fun overviewSeedIsDerivedFromCurrentPeriodDomainRecords() {
        val currentSpend = sampleTransactions
            .filter { it.inCurrentPeriod && it.kind == EntryKind.Expense }
            .sumOf { it.amountMinor }

        assertEquals(26_180L, currentSpend)
        assertEquals(387_000L, sampleAssets.sumOf { it.currentValueMinor })
        assertTrue(sampleAssets.all { "非实时" in it.quoteUpdatedLabel })
        assertTrue(sampleInsights.isNotEmpty())
        assertTrue(sampleInsights.all { it.evidence.isNotBlank() })
    }

    @Test
    fun mixedDemoAndLiveQuotesAreNeverPresentedAsPureLive() {
        val initial = DomainDemoData.initialSnapshot
        val demo = initial.marketQuotes.first()
        val mixed = initial.copy(
            marketQuotes = initial.marketQuotes + demo.copy(
                id = "mixed-official",
                provenance = QuoteProvenance.OFFICIAL_API,
                sourceUrl = "https://example.invalid/official-quote",
                isLive = true,
            ),
        )
        val asset = DomainDemoData.assets(mixed, LocalDate(2026, 7, 15)).first { it.id == demo.assetId.value }

        assertTrue("混合来源" in asset.quoteUpdatedLabel)
        assertTrue("非实时" in asset.quoteUpdatedLabel)
    }

    @Test
    fun currentPeriodFollowsTheProvidedDateInsteadOfAHardcodedMonth() {
        val july = DomainDemoData.transactions(DomainDemoData.initialSnapshot, LocalDate(2026, 7, 15))
        val june = DomainDemoData.transactions(DomainDemoData.initialSnapshot, LocalDate(2026, 6, 30))

        assertEquals(3, july.count { it.inCurrentPeriod })
        assertEquals(0, june.count { it.inCurrentPeriod })
    }

    @Test
    fun emptyLedgerProducesAnEmptySafeUiProjection() {
        val empty = LedgerSnapshot(0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val asOf = LocalDate(2026, 7, 15)

        assertTrue(DomainDemoData.transactions(empty, asOf).isEmpty())
        assertTrue(DomainDemoData.assets(empty, asOf).isEmpty())
        assertTrue(DomainDemoData.insights(empty, asOf, nowEpochMillis = 0).isEmpty())
    }

    @Test
    fun insightProjectionPreservesStableKeysAcrossLedgerRevisions() {
        val asOf = LocalDate(2026, 7, 15)
        val initial = DomainDemoData.initialSnapshot
        val first = DomainDemoData.insights(initial, asOf, nowEpochMillis = 1_000)
        val revised = DomainDemoData.insights(
            initial.copy(revision = initial.revision + 1),
            asOf,
            nowEpochMillis = 2_000,
        )

        assertTrue(first.isNotEmpty())
        assertEquals(first.map { it.deduplicationKey }, revised.map { it.deduplicationKey })
        assertEquals(first.size, first.map { it.deduplicationKey }.distinct().size)
    }

    @Test
    fun insightProjectionAppliesAdoptedSnoozedAndIgnoredPreferencesBeforeTakingFour() {
        val asOf = LocalDate(2026, 7, 15)
        val now = 10_000L
        val initial = DomainDemoData.insights(DomainDemoData.initialSnapshot, asOf, now)
        val target = initial.first()

        val adopted = DomainDemoData.insights(
            DomainDemoData.initialSnapshot.copy(
                insightPreferences = InsightPreferenceRecord(
                    adoptedDeduplicationKeys = setOf(target.deduplicationKey),
                    updatedAtEpochMillis = now,
                ),
            ),
            asOf,
            now,
        )
        assertEquals(
            InsightFeedback.ADOPTED,
            adopted.first { it.deduplicationKey == target.deduplicationKey }.feedback,
        )

        val snoozed = DomainDemoData.insights(
            DomainDemoData.initialSnapshot.copy(
                insightPreferences = InsightPreferenceRecord(
                    snoozedUntilEpochMillisByKey = mapOf(target.deduplicationKey to now + 1),
                    updatedAtEpochMillis = now,
                ),
            ),
            asOf,
            now,
        )
        assertTrue(snoozed.none { it.deduplicationKey == target.deduplicationKey })

        val ignored = DomainDemoData.insights(
            DomainDemoData.initialSnapshot.copy(
                insightPreferences = InsightPreferenceRecord(
                    ignoredDeduplicationKeys = setOf(target.deduplicationKey),
                    updatedAtEpochMillis = now,
                ),
            ),
            asOf,
            now,
        )
        assertTrue(ignored.none { it.deduplicationKey == target.deduplicationKey })
    }
}
