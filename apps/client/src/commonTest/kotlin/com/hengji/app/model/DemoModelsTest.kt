package com.hengji.app.model

import com.hengji.app.application.SaleTargetStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.QuoteProviderId
import com.hengji.domain.Confidence
import com.hengji.domain.Money
import kotlinx.datetime.LocalDate
import com.hengji.data.InsightPreferenceRecord
import com.hengji.data.LedgerSnapshot
import com.hengji.insights.InsightFeedback
import com.hengji.insights.EvidenceValue
import com.hengji.insights.Insight
import com.hengji.insights.InsightAction
import com.hengji.insights.InsightEvidence
import com.hengji.insights.RuleScore
import com.hengji.insights.InsightType

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
        assertEquals("\$8.00", formatMoney(800L, currencyCode = "USD"))
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
    fun manualQuoteRefreshesIntervalAndKeepsMixedSourceDisclosure() {
        val initial = DomainDemoData.initialSnapshot
        val demo = initial.marketQuotes.first()
        val projectedBefore = DomainDemoData.assets(initial, LocalDate(2026, 7, 25))
            .first { it.id == demo.assetId.value }
        val manual = demo.copy(
            id = "manual-local-quote",
            providerId = QuoteProviderId("manual-local"),
            provenance = QuoteProvenance.MANUAL,
            price = demo.price.copy(minorUnits = demo.price.minorUnits + 5_000),
            collectedOn = LocalDate(2026, 7, 25),
            sourceUrl = null,
            confidence = Confidence(5_000),
            isLive = false,
        )
        val projected = DomainDemoData.assets(
            initial.copy(marketQuotes = initial.marketQuotes + manual),
            LocalDate(2026, 7, 25),
        ).first { it.id == demo.assetId.value }

        assertEquals(projectedBefore.quoteCount + 1, projected.quoteCount)
        assertTrue("含示例/手工" in projected.quoteUpdatedLabel)
        assertTrue("非实时" in projected.quoteUpdatedLabel)
        assertTrue("7 月 25 日" in projected.quoteUpdatedLabel)
        assertTrue(projected.marketMedianMinor != null)
    }

    @Test
    fun saleTargetProjectionUsesOnlyNonDemoQuotesAndReachedInsightIsRetained() {
        val asOf = LocalDate(2026, 7, 25)
        val initial = DomainDemoData.initialSnapshot
        val originalAsset = initial.assets.first()
        val targetedAsset = originalAsset.copy(
            saleTargetPrice = Money(1, originalAsset.purchasePrice.currency),
        )
        val demoOnlySnapshot = initial.copy(
            assets = initial.assets.map { if (it.id == originalAsset.id) targetedAsset else it },
        )
        val demoOnly = DomainDemoData.assets(demoOnlySnapshot, asOf)
            .first { it.id == originalAsset.id.value }
        assertEquals(SaleTargetStatus.DEMO_ONLY, demoOnly.saleTarget.status)

        val manualQuotes = initial.marketQuotes
            .filter { it.assetId == originalAsset.id }
            .mapIndexed { index, quote ->
                quote.copy(
                    id = "target-manual-$index",
                    providerId = QuoteProviderId("manual-local"),
                    provenance = QuoteProvenance.MANUAL,
                    collectedOn = asOf,
                    sourceUrl = null,
                    confidence = Confidence(8_000),
                    isLive = false,
                )
            }
        val actionableSnapshot = demoOnlySnapshot.copy(
            marketQuotes = initial.marketQuotes + manualQuotes,
        )

        val projected = DomainDemoData.assets(actionableSnapshot, asOf)
            .first { it.id == originalAsset.id.value }
        val insights = DomainDemoData.insights(actionableSnapshot, asOf, nowEpochMillis = 0)

        assertEquals(SaleTargetStatus.REACHED, projected.saleTarget.status)
        assertTrue(insights.any { it.type == InsightType.PRICE_TARGET_REACHED })
    }

    @Test
    fun compactInsightFeedDoesNotClipReachedTargetOutsideTopFour() {
        val regular = (1..4).map { index ->
            testInsight("regular-$index", InsightType.POSSIBLE_DUPLICATE)
        }
        val reachedTarget = testInsight("target", InsightType.PRICE_TARGET_REACHED)

        assertEquals(
            regular + reachedTarget,
            retainPriceTargetInsights(regular + reachedTarget),
        )
    }

    @Test
    fun oneManualQuoteShowsIntervalButNotSinglePointMarketValue() {
        val initial = DomainDemoData.initialSnapshot
        val asset = initial.assets.first()
        val manual = initial.marketQuotes.first().copy(
            id = "single-manual",
            assetId = asset.id,
            providerId = QuoteProviderId("manual-local"),
            provenance = QuoteProvenance.MANUAL,
            sourceUrl = null,
            confidence = Confidence(5_000),
            isLive = false,
        )

        val projected = DomainDemoData.assets(
            initial.copy(marketQuotes = listOf(manual)),
            LocalDate(2026, 7, 25),
        ).first { it.id == asset.id.value }

        assertEquals(1, projected.quoteCount)
        assertNull(projected.marketMedianMinor)
        assertEquals(manual.landedPrice.minorUnits, projected.marketLowMinor)
        assertEquals(manual.landedPrice.minorUnits, projected.marketHighMinor)
        assertTrue("手工估值" in projected.quoteUpdatedLabel)
        assertTrue("非实时" in projected.quoteUpdatedLabel)
    }

    @Test
    fun currentPeriodFollowsTheProvidedDateInsteadOfAHardcodedMonth() {
        val july = DomainDemoData.transactions(DomainDemoData.initialSnapshot, LocalDate(2026, 7, 15))
        val june = DomainDemoData.transactions(DomainDemoData.initialSnapshot, LocalDate(2026, 6, 30))

        assertEquals(3, july.count { it.inCurrentPeriod })
        assertEquals(0, june.count { it.inCurrentPeriod })
    }

    @Test
    fun transactionProjectionDefensivelyExcludesTombstones() {
        val transaction = DomainDemoData.initialSnapshot.transactions.first()
        val snapshotWithTombstone = DomainDemoData.initialSnapshot.copy(
            transactions = listOf(transaction.copy(deletedAtEpochMillis = 1)),
        )

        assertTrue(
            DomainDemoData.transactions(snapshotWithTombstone, LocalDate(2026, 7, 15)).isEmpty(),
        )
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

    private fun testInsight(id: String, type: InsightType): Insight = Insight(
        id = id,
        deduplicationKey = id,
        type = type,
        title = id,
        summary = id,
        evidence = listOf(InsightEvidence("test", EvidenceValue.Text(id))),
        estimatedImpact = null,
        impact = RuleScore(1_000),
        confidence = RuleScore(1_000),
        actionability = RuleScore(1_000),
        action = InsightAction("test", "test"),
    )
}
