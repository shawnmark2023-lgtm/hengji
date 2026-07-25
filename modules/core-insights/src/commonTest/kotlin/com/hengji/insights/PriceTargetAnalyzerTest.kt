package com.hengji.insights

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.AssetStatus
import com.hengji.domain.CategoryId
import com.hengji.domain.Confidence
import com.hengji.domain.CurrencyCode
import com.hengji.domain.ItemCondition
import com.hengji.domain.MarketQuote
import com.hengji.domain.Money
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.QuoteProviderId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PriceTargetAnalyzerTest {
    private val cny = CurrencyCode.CNY
    private val asOf = LocalDate(2026, 4, 1)

    @Test
    fun `demo quotes and low sample counts never trigger a price target`() {
        val asset = asset(targetMinor = 1_500)

        val demoResult = PriceTargetAnalyzer.analyze(
            assets = listOf(asset),
            quotes = quotes(listOf(1_400, 1_500, 1_600), QuoteProvenance.DEMO),
            asOf = asOf,
        )
        val lowSampleResult = PriceTargetAnalyzer.analyze(
            assets = listOf(asset),
            quotes = quotes(listOf(1_500, 1_600), QuoteProvenance.MANUAL),
            asOf = asOf,
        )

        assertTrue(demoResult.isEmpty())
        assertTrue(lowSampleResult.isEmpty())
    }

    @Test
    fun `median equal to or above the target triggers`() {
        val asset = asset(targetMinor = 1_500)

        val equal = PriceTargetAnalyzer.analyze(
            assets = listOf(asset),
            quotes = quotes(listOf(1_400, 1_500, 1_600)),
            asOf = asOf,
        ).single()
        val above = PriceTargetAnalyzer.analyze(
            assets = listOf(asset),
            quotes = quotes(listOf(1_500, 1_600, 1_700)),
            asOf = asOf,
        ).single()

        assertEquals(InsightType.PRICE_TARGET_REACHED, equal.type)
        assertEquals(InsightType.PRICE_TARGET_REACHED, above.type)
        assertEquals("asset:camera:price-target:CNY:1500", equal.deduplicationKey)
        assertTrue(equal.evidence.any { it.code == "asset.market_median" })
        assertTrue(equal.evidence.any { it.code == "asset.newest_quote_age" })
    }

    @Test
    fun `sold asset and stale quotes do not trigger`() {
        val sold = asset(targetMinor = 1_500, status = AssetStatus.SOLD)
        val active = asset(targetMinor = 1_500)
        val freshPrices = quotes(listOf(1_500, 1_600, 1_700))
        val stalePrices = quotes(
            prices = listOf(1_500, 1_600, 1_700),
            collectedOn = LocalDate(2025, 12, 31),
        )

        assertTrue(PriceTargetAnalyzer.analyze(listOf(sold), freshPrices, asOf).isEmpty())
        assertTrue(PriceTargetAnalyzer.analyze(listOf(active), stalePrices, asOf).isEmpty())
    }

    @Test
    fun `undated asset estimate is never used as a target trigger`() {
        val asset = asset(targetMinor = 1_500, currentEstimateMinor = 2_000)

        assertTrue(PriceTargetAnalyzer.analyze(listOf(asset), emptyList(), asOf).isEmpty())
    }

    @Test
    fun `changing the target changes the deduplication key`() {
        val highQuotes = quotes(listOf(1_700, 1_800, 1_900))
        val first = PriceTargetAnalyzer.analyze(
            assets = listOf(asset(targetMinor = 1_500)),
            quotes = highQuotes,
            asOf = asOf,
        ).single()
        val changed = PriceTargetAnalyzer.analyze(
            assets = listOf(asset(targetMinor = 1_600)),
            quotes = highQuotes,
            asOf = asOf,
        ).single()

        assertNotEquals(first.deduplicationKey, changed.deduplicationKey)
        assertEquals("asset:camera:price-target:CNY:1600", changed.deduplicationKey)
    }

    private fun asset(
        targetMinor: Long,
        status: AssetStatus = AssetStatus.ACTIVE,
        currentEstimateMinor: Long? = null,
    ) = Asset(
        id = AssetId("camera"),
        name = "Camera",
        categoryId = CategoryId("electronics"),
        purchasePrice = Money(2_000, cny),
        purchasedOn = LocalDate(2025, 1, 1),
        status = status,
        currentEstimatedValue = currentEstimateMinor?.let { Money(it, cny) },
        saleTargetPrice = Money(targetMinor, cny),
    )

    private fun quotes(
        prices: List<Long>,
        provenance: QuoteProvenance = QuoteProvenance.MANUAL,
        collectedOn: LocalDate = asOf,
    ) = prices.mapIndexed { index, price ->
        MarketQuote(
            id = "quote-$index-${provenance.name}-$collectedOn",
            assetId = AssetId("camera"),
            providerId = QuoteProviderId("provider-$index"),
            provenance = provenance,
            specification = "same model",
            condition = ItemCondition.GOOD,
            price = Money(price, cny),
            collectedOn = collectedOn,
            confidence = Confidence(8_000),
        )
    }
}
