package com.hengji.app.application

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.CategoryId
import com.hengji.domain.Confidence
import com.hengji.domain.CurrencyCode
import com.hengji.domain.ItemCondition
import com.hengji.domain.MarketQuote
import com.hengji.domain.MarketQuoteEstimator
import com.hengji.domain.Money
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.QuoteProviderId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssetSaleTargetTest {
    private val asOf = LocalDate(2026, 7, 25)
    private val asset = Asset(
        id = AssetId("asset-1"),
        name = "Camera",
        categoryId = CategoryId("digital"),
        purchasePrice = Money(100_000, CurrencyCode.USD),
        purchasedOn = LocalDate(2025, 1, 1),
    )

    @Test
    fun editorSetsAndClearsExactMinorUnitsInPurchaseCurrency() {
        val updated = AssetSaleTargetEditor.set(asset, 82_345)

        assertEquals(82_345, updated.saleTargetPrice?.minorUnits)
        assertEquals(CurrencyCode.USD, updated.saleTargetPrice?.currency)
        assertNull(AssetSaleTargetEditor.clear(updated).saleTargetPrice)
        assertFailsWith<IllegalArgumentException> { AssetSaleTargetEditor.set(asset, 0) }
    }

    @Test
    fun projectionRequiresThreePresentableNonDemoQuotesToReachTarget() {
        val targeted = AssetSaleTargetEditor.set(asset, 80_000)
        val actionable = MarketQuoteEstimator.estimate(
            assetId = asset.id,
            quotes = listOf(79_000L, 82_000L, 85_000L).mapIndexed(::quote),
            asOf = asOf,
        )

        val reached = AssetSaleTargetProjector.project(
            asset = targeted,
            actionableEstimate = actionable,
            hasNonDemoQuotes = true,
            hasDemoQuotes = true,
        )

        assertEquals(SaleTargetStatus.REACHED, reached.status)
        assertEquals(82_000, reached.observedMedianMinor)
        assertEquals(
            SaleTargetStatus.WAITING,
            AssetSaleTargetProjector.project(
                asset = AssetSaleTargetEditor.set(asset, 90_000),
                actionableEstimate = actionable,
                hasNonDemoQuotes = true,
                hasDemoQuotes = false,
            ).status,
        )
        assertEquals(
            SaleTargetStatus.NOT_SET,
            AssetSaleTargetProjector.project(
                asset = asset,
                actionableEstimate = actionable,
                hasNonDemoQuotes = true,
                hasDemoQuotes = false,
            ).status,
        )
    }

    @Test
    fun projectionDistinguishesDemoOnlyStaleAndInsufficientStates() {
        val targeted = AssetSaleTargetEditor.set(asset, 80_000)
        val sparseEstimate = MarketQuoteEstimator.estimate(
            assetId = asset.id,
            quotes = listOf(81_000L, 82_000L).mapIndexed(::quote),
            asOf = asOf,
        )

        assertEquals(
            SaleTargetStatus.DEMO_ONLY,
            AssetSaleTargetProjector.project(targeted, null, hasNonDemoQuotes = false, hasDemoQuotes = true).status,
        )
        assertEquals(
            SaleTargetStatus.STALE_QUOTES,
            AssetSaleTargetProjector.project(targeted, null, hasNonDemoQuotes = true, hasDemoQuotes = false).status,
        )
        assertEquals(
            SaleTargetStatus.INSUFFICIENT_SAMPLE,
            AssetSaleTargetProjector.project(
                targeted,
                sparseEstimate,
                hasNonDemoQuotes = true,
                hasDemoQuotes = false,
            ).status,
        )
    }

    private fun quote(index: Int, priceMinor: Long): MarketQuote = MarketQuote(
        id = "quote-$index",
        assetId = asset.id,
        providerId = QuoteProviderId("manual-local"),
        provenance = QuoteProvenance.MANUAL,
        specification = "reference-$index",
        condition = ItemCondition.GOOD,
        price = Money(priceMinor, CurrencyCode.USD),
        collectedOn = asOf,
        confidence = Confidence(8_000),
    )
}
