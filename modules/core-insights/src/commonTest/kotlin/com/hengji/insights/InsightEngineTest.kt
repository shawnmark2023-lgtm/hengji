package com.hengji.insights

import com.hengji.domain.CategoryId
import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.Confidence
import com.hengji.domain.CurrencyCode
import com.hengji.domain.DateRange
import com.hengji.domain.ItemCondition
import com.hengji.domain.MarketQuote
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.QuoteProviderId
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsightEngineTest {
    @Test
    fun `engine returns ranked deterministic insights with evidence`() {
        val cny = CurrencyCode.CNY
        val insights = InsightEngine().generate(snapshot(cny))

        assertTrue(insights.isNotEmpty())
        assertTrue(insights.all { it.evidence.isNotEmpty() })
        assertTrue(insights.zipWithNext().all { (first, second) -> first.rankScore >= second.rankScore })
        assertTrue(insights.any { it.type == InsightType.BUDGET_PACE })
        assertTrue(insights.any { it.type == InsightType.POSSIBLE_DUPLICATE })
    }

    @Test
    fun `engine applies deterministic snooze deadline before returning ranked insights`() {
        val engine = InsightEngine()
        val snapshot = snapshot(CurrencyCode.CNY)
        val baseline = engine.generate(snapshot)
        val target = baseline.first()
        val preferences = InsightPreferences(
            snoozedUntilEpochMillisByKey = mapOf(target.deduplicationKey to 10_000L),
        )

        val before = engine.generate(snapshot, preferences, nowEpochMillis = 9_999L)
        val boundary = engine.generate(snapshot, preferences, nowEpochMillis = 10_000L)

        assertFalse(before.any { it.deduplicationKey == target.deduplicationKey })
        assertEquals(
            InsightFeedback.NEW,
            boundary.single { it.deduplicationKey == target.deduplicationKey }.feedback,
        )
    }

    @Test
    fun `engine includes a price target insight from raw quote history`() {
        val cny = CurrencyCode.CNY
        val base = snapshot(cny)
        val asset = Asset(
            id = AssetId("camera"),
            name = "Camera",
            categoryId = CategoryId("electronics"),
            purchasePrice = Money(2_000, cny),
            purchasedOn = LocalDate(2025, 1, 1),
            saleTargetPrice = Money(1_500, cny),
        )
        val quotes = listOf(1_400L, 1_500L, 1_600L).mapIndexed { index, price ->
            MarketQuote(
                id = "engine-price-$index",
                assetId = asset.id,
                providerId = QuoteProviderId("manual-$index"),
                provenance = QuoteProvenance.MANUAL,
                specification = "same model",
                condition = ItemCondition.GOOD,
                price = Money(price, cny),
                collectedOn = base.asOf,
                confidence = Confidence(8_000),
            )
        }

        val insights = InsightEngine().generate(
            base.copy(assets = listOf(asset), marketQuotes = quotes),
        )

        assertTrue(insights.any { it.type == InsightType.PRICE_TARGET_REACHED })
    }

    private fun snapshot(cny: CurrencyCode): InsightSnapshot {
        val current = DateRange(LocalDate(2026, 2, 1), LocalDate(2026, 3, 1))
        val previous = DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1))
        return InsightSnapshot(
            asOf = LocalDate(2026, 2, 10),
            currency = cny,
            currentPeriod = current,
            previousPeriod = previous,
            transactions = listOf(
                tx("old", 2_000, LocalDate(2026, 1, 5), cny),
                tx("new1", 5_000, LocalDate(2026, 2, 2), cny),
                tx("new2", 5_000, LocalDate(2026, 2, 3), cny),
            ),
            budgets = listOf(Budget("monthly", current, Money(8_000, cny))),
        )
    }

    private fun tx(id: String, amount: Long, date: LocalDate, currency: CurrencyCode) = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.EXPENSE,
        amount = Money(amount, currency),
        bookedOn = date,
        categoryId = CategoryId("food"),
        merchant = Merchant("Market", "market"),
    )
}
