package com.hengji.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionAndMarketTest {
    @Test
    fun `transaction magnitude cannot be negative and refund direction is explicit`() {
        assertFailsWith<IllegalArgumentException> {
            transaction("bad", TransactionKind.EXPENSE, -1)
        }
        val refund = transaction("refund", TransactionKind.REFUND, 250)
        assertEquals(-250, refund.spendingContribution().minorUnits)
        assertEquals(250, refund.cashFlowContribution().minorUnits)
    }

    @Test
    fun `market estimator rejects an IQR outlier and labels source classes`() {
        val assetId = AssetId("phone")
        val prices = listOf(10_000L, 10_500L, 11_000L, 11_500L, 100_000L)
        val quotes = prices.mapIndexed { index, price ->
            MarketQuote(
                id = "q$index",
                assetId = assetId,
                providerId = QuoteProviderId("provider"),
                provenance = if (index == 0) QuoteProvenance.DEMO else QuoteProvenance.OFFICIAL_API,
                specification = "128GB",
                condition = ItemCondition.GOOD,
                price = Money(price, CurrencyCode.CNY),
                collectedOn = LocalDate(2026, 7, 1),
                confidence = Confidence(8_000),
                sourceUrl = if (index == 0) null else "https://example.invalid/q$index",
                isLive = index != 0,
            )
        }

        val estimate = MarketQuoteEstimator.estimate(assetId, quotes, LocalDate(2026, 7, 15))!!
        assertEquals(4, estimate.acceptedQuoteCount)
        assertEquals(1, estimate.rejectedOutlierCount)
        assertEquals(11_000, estimate.median?.minorUnits)
        assertTrue(estimate.includesDemoData)
        assertTrue(estimate.includesLiveData)
        assertFalse(estimate.isEntirelyLiveData)
        assertFalse(estimate.confidence == Confidence.FULL)

        val liveOnly = MarketQuoteEstimator.estimate(assetId, quotes.drop(1).take(3), LocalDate(2026, 7, 15))!!
        assertTrue(liveOnly.isEntirelyLiveData)
    }

    @Test
    fun `low confidence estimate keeps interval but hides single point`() {
        val assetId = AssetId("single-quote")
        val estimate = MarketQuoteEstimator.estimate(
            assetId,
            listOf(
                MarketQuote(
                    id = "single",
                    assetId = assetId,
                    providerId = QuoteProviderId("manual"),
                    provenance = QuoteProvenance.MANUAL,
                    specification = "manual reference",
                    condition = ItemCondition.GOOD,
                    price = Money(50_000, CurrencyCode.CNY),
                    collectedOn = LocalDate(2026, 7, 1),
                    confidence = Confidence(8_000),
                ),
            ),
            LocalDate(2026, 7, 15),
        )!!

        assertEquals(50_000, estimate.minimum.minorUnits)
        assertEquals(50_000, estimate.statisticalMedian.minorUnits)
        assertEquals(null, estimate.median)
        assertTrue(estimate.includesManualData)
        assertFalse(estimate.includesLiveData)
    }

    @Test
    fun `four reasonable quotes keep their full interval`() {
        val assetId = AssetId("four-quotes")
        val quotes = listOf(17_600L, 18_100L, 18_200L, 18_800L).mapIndexed { index, price ->
            MarketQuote(
                id = "four-$index",
                assetId = assetId,
                providerId = QuoteProviderId("manual"),
                provenance = QuoteProvenance.MANUAL,
                specification = "same model",
                condition = ItemCondition.GOOD,
                price = Money(price, CurrencyCode.CNY),
                collectedOn = LocalDate(2026, 7, 25),
                confidence = Confidence(5_000),
            )
        }

        val estimate = MarketQuoteEstimator.estimate(assetId, quotes, LocalDate(2026, 7, 25))!!

        assertEquals(4, estimate.acceptedQuoteCount)
        assertEquals(0, estimate.rejectedOutlierCount)
        assertEquals(17_600, estimate.minimum.minorUnits)
        assertEquals(18_800, estimate.maximum.minorUnits)
        assertTrue(estimate.median != null)
    }

    @Test
    fun `quote is accepted at ninety days and rejected at ninety one days`() {
        val assetId = AssetId("freshness-boundary")
        val quote = marketQuote(
            id = "boundary",
            assetId = assetId,
            collectedOn = LocalDate(2026, 1, 1),
        )

        val atNinetyDays = MarketQuoteEstimator.estimate(
            assetId = assetId,
            quotes = listOf(quote),
            asOf = LocalDate(2026, 4, 1),
        )
        val atNinetyOneDays = MarketQuoteEstimator.estimate(
            assetId = assetId,
            quotes = listOf(quote),
            asOf = LocalDate(2026, 4, 2),
        )

        assertEquals(1, atNinetyDays?.acceptedQuoteCount)
        assertEquals(LocalDate(2026, 1, 1), atNinetyDays?.newestAcceptedQuoteOn)
        assertEquals(null, atNinetyOneDays)
    }

    @Test
    fun `estimate reports stale rejections separately from retained price history`() {
        val assetId = AssetId("mixed-freshness")
        val estimate = MarketQuoteEstimator.estimate(
            assetId = assetId,
            quotes = listOf(
                marketQuote("stale", assetId, LocalDate(2025, 12, 31)),
                marketQuote("boundary", assetId, LocalDate(2026, 1, 1)),
                marketQuote("latest", assetId, LocalDate(2026, 3, 20)),
            ),
            asOf = LocalDate(2026, 4, 1),
        )!!

        assertEquals(2, estimate.acceptedQuoteCount)
        assertEquals(1, estimate.rejectedStaleQuoteCount)
        assertEquals(LocalDate(2026, 3, 20), estimate.newestAcceptedQuoteOn)
    }

    private fun marketQuote(
        id: String,
        assetId: AssetId,
        collectedOn: LocalDate,
    ) = MarketQuote(
        id = id,
        assetId = assetId,
        providerId = QuoteProviderId("manual"),
        provenance = QuoteProvenance.MANUAL,
        specification = "same model",
        condition = ItemCondition.GOOD,
        price = Money(50_000, CurrencyCode.CNY),
        collectedOn = collectedOn,
        confidence = Confidence(8_000),
    )

    private fun transaction(id: String, kind: TransactionKind, amount: Long) = Transaction(
        id = TransactionId(id),
        kind = kind,
        amount = Money(amount, CurrencyCode.CNY),
        bookedOn = LocalDate(2026, 1, 1),
        categoryId = CategoryId("general"),
    )
}
