package com.hengji.insights

import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.DateRange
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpendingMetricsTest {
    private val cny = CurrencyCode.CNY
    private val food = CategoryId("food")
    private val travel = CategoryId("travel")

    @Test
    fun `category shares use net spending after refunds and ignore income`() {
        val period = DateRange(LocalDate(2026, 2, 1), LocalDate(2026, 3, 1))
        val breakdown = SpendingMetrics.categoryBreakdown(
            listOf(
                tx("food", TransactionKind.EXPENSE, 10_000, LocalDate(2026, 2, 2), food),
                tx("refund", TransactionKind.REFUND, 2_000, LocalDate(2026, 2, 5), food),
                tx("travel", TransactionKind.EXPENSE, 2_000, LocalDate(2026, 2, 7), travel),
                tx("income", TransactionKind.INCOME, 50_000, LocalDate(2026, 2, 10), food),
            ),
            period,
            cny,
        )

        assertEquals(10_000, breakdown.totalNetSpend.minorUnits)
        assertEquals(8_000, breakdown.categories.single { it.categoryId == food }.shareBasisPoints)
        assertEquals(2_000, breakdown.categories.single { it.categoryId == travel }.shareBasisPoints)
    }

    @Test
    fun `cross-period refund is applied on its booking date`() {
        val previous = DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1))
        val current = DateRange(LocalDate(2026, 2, 1), LocalDate(2026, 3, 1))
        val januaryExpense = tx("jan", TransactionKind.EXPENSE, 10_000, LocalDate(2026, 1, 31), food)
        val refund = tx(
            "refund",
            TransactionKind.REFUND,
            5_000,
            LocalDate(2026, 2, 5),
            food,
            original = januaryExpense.id,
        )
        val trend = SpendingMetrics.comparePeriods(
            listOf(
                januaryExpense,
                tx("feb", TransactionKind.EXPENSE, 20_000, LocalDate(2026, 2, 2), food),
                refund,
            ),
            current,
            previous,
            cny,
        )

        assertEquals(10_000, trend.previousNetSpend.minorUnits)
        assertEquals(15_000, trend.currentNetSpend.minorUnits)
        assertEquals(5_000, trend.changeBasisPoints)
    }

    @Test
    fun `trend percentage is absent when previous net spending is zero`() {
        val trend = SpendingMetrics.comparePeriods(
            listOf(tx("feb", TransactionKind.EXPENSE, 100, LocalDate(2026, 2, 2), food)),
            DateRange(LocalDate(2026, 2, 1), LocalDate(2026, 3, 1)),
            DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1)),
            cny,
        )
        assertNull(trend.changeBasisPoints)
    }

    @Test
    fun `budget burn exposes pace and projection without floating point`() {
        val period = DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1))
        val burn = SpendingMetrics.budgetBurn(
            Budget("monthly", period, Money(10_000, cny)),
            listOf(tx("spend", TransactionKind.EXPENSE, 5_000, LocalDate(2026, 1, 5), food)),
            LocalDate(2026, 1, 10),
        )

        assertEquals(10, burn.elapsedDays)
        assertEquals(3_226, burn.expectedSpendToDate.minorUnits)
        assertEquals(15_500, burn.projectedPeriodSpend?.minorUnits)
        assertEquals(5_000, burn.budgetUsedBasisPoints)
        assertEquals(15_499, burn.paceBasisPoints)
    }

    @Test
    fun `budget before period has no projected pace`() {
        val period = DateRange(LocalDate(2026, 2, 1), LocalDate(2026, 3, 1))
        val burn = SpendingMetrics.budgetBurn(
            Budget("future", period, Money(10_000, cny)),
            emptyList(),
            LocalDate(2026, 1, 31),
        )
        assertEquals(0, burn.elapsedDays)
        assertNull(burn.projectedPeriodSpend)
        assertNull(burn.paceBasisPoints)
    }

    private fun tx(
        id: String,
        kind: TransactionKind,
        amount: Long,
        date: LocalDate,
        category: CategoryId,
        original: TransactionId? = null,
    ) = Transaction(
        id = TransactionId(id),
        kind = kind,
        amount = Money(amount, cny),
        bookedOn = date,
        categoryId = category,
        originalTransactionId = original,
    )
}
