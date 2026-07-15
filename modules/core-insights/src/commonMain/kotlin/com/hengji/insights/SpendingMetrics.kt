package com.hengji.insights

import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.DateRange
import com.hengji.domain.ExactMath
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.MoneyRounding
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate

data class CategorySpend(
    val categoryId: CategoryId,
    val netSpend: Money,
    val shareBasisPoints: Long?,
    val transactionCount: Int,
)

data class CategoryBreakdown(
    val period: DateRange,
    val totalNetSpend: Money,
    val categories: List<CategorySpend>,
)

data class SpendingTrend(
    val currentPeriod: DateRange,
    val previousPeriod: DateRange,
    val currentNetSpend: Money,
    val previousNetSpend: Money,
    val delta: Money,
    /** Null when the previous period is zero or negative, because percentage growth would be misleading. */
    val changeBasisPoints: Long?,
)

data class Budget(
    val id: String,
    val period: DateRange,
    val amount: Money,
    val categoryId: CategoryId? = null,
) {
    init {
        require(id.isNotBlank()) { "Budget id cannot be blank" }
        require(period.days > 0) { "Budget period cannot be empty" }
        require(amount.minorUnits > 0) { "Budget amount must be positive" }
    }
}

data class BudgetBurn(
    val budget: Budget,
    val asOf: LocalDate,
    val elapsedDays: Int,
    val totalDays: Int,
    val netSpend: Money,
    val consumedSpend: Money,
    val expectedSpendToDate: Money,
    val projectedPeriodSpend: Money?,
    val budgetUsedBasisPoints: Long,
    val paceBasisPoints: Long?,
)

data class MerchantSpend(
    val merchant: Merchant,
    val netSpend: Money,
    val shareBasisPoints: Long?,
    val transactionCount: Int,
)

data class LargeExpense(
    val transaction: Transaction,
    val medianExpense: Money,
    val multipleBasisPoints: Long?,
)

object SpendingMetrics {
    fun categoryBreakdown(
        transactions: Iterable<Transaction>,
        period: DateRange,
        currency: CurrencyCode,
    ): CategoryBreakdown {
        val included = spendingTransactions(transactions, period, currency)
        val total = sumSpend(included, currency)
        val categories = included.groupBy { it.categoryId }.map { (category, rows) ->
            val net = sumSpend(rows, currency)
            CategorySpend(
                categoryId = category,
                netSpend = net,
                shareBasisPoints = ratioOrNull(net.minorUnits, total.minorUnits),
                transactionCount = rows.size,
            )
        }.sortedWith(compareByDescending<CategorySpend> { it.netSpend.minorUnits }.thenBy { it.categoryId.value })
        return CategoryBreakdown(period, total, categories)
    }

    fun comparePeriods(
        transactions: Iterable<Transaction>,
        currentPeriod: DateRange,
        previousPeriod: DateRange,
        currency: CurrencyCode,
    ): SpendingTrend {
        require(currentPeriod.days > 0 && previousPeriod.days > 0) { "Trend periods cannot be empty" }
        val current = sumSpend(spendingTransactions(transactions, currentPeriod, currency), currency)
        val previous = sumSpend(spendingTransactions(transactions, previousPeriod, currency), currency)
        val delta = current - previous
        return SpendingTrend(
            currentPeriod = currentPeriod,
            previousPeriod = previousPeriod,
            currentNetSpend = current,
            previousNetSpend = previous,
            delta = delta,
            changeBasisPoints = ratioOrNull(delta.minorUnits, previous.minorUnits),
        )
    }

    fun budgetBurn(
        budget: Budget,
        transactions: Iterable<Transaction>,
        asOf: LocalDate,
    ): BudgetBurn {
        val included = spendingTransactions(transactions, budget.period, budget.amount.currency)
            .filter { budget.categoryId == null || it.categoryId == budget.categoryId }
            .filter { it.bookedOn <= asOf }
        val net = sumSpend(included, budget.amount.currency)
        val consumed = if (net.minorUnits < 0) Money.zero(net.currency) else net
        val elapsed = when {
            asOf < budget.period.startInclusive -> 0
            asOf >= budget.period.endExclusive -> budget.period.days
            else -> ExactMath.add(
                ExactMath.subtract(asOf.toEpochDays(), budget.period.startInclusive.toEpochDays()),
                1,
            ).toInt()
        }
        val expected = budget.amount.multiplyAndDivide(elapsed.toLong(), budget.period.days.toLong())
        val projected = if (elapsed == 0) {
            null
        } else {
            consumed.multiplyAndDivide(budget.period.days.toLong(), elapsed.toLong())
        }
        return BudgetBurn(
            budget = budget,
            asOf = asOf,
            elapsedDays = elapsed,
            totalDays = budget.period.days,
            netSpend = net,
            consumedSpend = consumed,
            expectedSpendToDate = expected,
            projectedPeriodSpend = projected,
            budgetUsedBasisPoints = ratioOrZero(consumed.minorUnits, budget.amount.minorUnits),
            paceBasisPoints = if (expected.minorUnits <= 0) null else ratioOrZero(consumed.minorUnits, expected.minorUnits),
        )
    }

    fun merchantConcentration(
        transactions: Iterable<Transaction>,
        period: DateRange,
        currency: CurrencyCode,
    ): List<MerchantSpend> {
        val rows = spendingTransactions(transactions, period, currency).filter { it.merchant != null }
        val total = sumSpend(rows, currency)
        return rows.groupBy { it.merchant!!.normalizedName }.map { (_, merchantRows) ->
            val net = sumSpend(merchantRows, currency)
            MerchantSpend(
                merchant = merchantRows.first().merchant!!,
                netSpend = net,
                shareBasisPoints = ratioOrNull(net.minorUnits, total.minorUnits),
                transactionCount = merchantRows.size,
            )
        }.sortedWith(compareByDescending<MerchantSpend> { it.netSpend.minorUnits }.thenBy { it.merchant.normalizedName })
    }

    fun largeExpenses(
        transactions: Iterable<Transaction>,
        period: DateRange,
        currency: CurrencyCode,
        thresholdMultipleBasisPoints: Long = 30_000,
    ): List<LargeExpense> {
        require(thresholdMultipleBasisPoints >= 10_000) { "Large-expense multiple must be at least 1x" }
        val expenses = transactions.filter {
            !it.isDeleted && it.kind == TransactionKind.EXPENSE && it.bookedOn in period
        }.also { rows -> requireCurrency(rows, currency) }.sortedBy { it.amount.minorUnits }
        if (expenses.isEmpty()) return emptyList()
        val median = medianMoney(expenses.map { it.amount })
        if (median.minorUnits <= 0) return emptyList()
        val threshold = median.multiplyAndDivide(thresholdMultipleBasisPoints, 10_000)
        return expenses.filter { it.amount >= threshold }.map {
            LargeExpense(it, median, ratioOrNull(it.amount.minorUnits, median.minorUnits))
        }.sortedByDescending { it.transaction.amount.minorUnits }
    }

    internal fun ratioOrNull(numerator: Long, denominator: Long): Long? =
        if (denominator <= 0) null else ExactMath.multiplyDivideRounded(
            value = numerator,
            multiplier = 10_000,
            divisor = denominator,
            rounding = MoneyRounding.HALF_AWAY_FROM_ZERO,
        )

    internal fun ratioOrZero(numerator: Long, denominator: Long): Long = ratioOrNull(numerator, denominator) ?: 0

    internal fun medianMoney(values: List<Money>): Money {
        require(values.isNotEmpty())
        val currency = values.first().currency
        require(values.all { it.currency == currency }) { "Cannot calculate median across currencies" }
        val sorted = values.map { it.minorUnits }.sorted()
        val middle = sorted.size / 2
        val units = if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            val lower = sorted[middle - 1]
            lower + (sorted[middle] - lower) / 2
        }
        return Money(units, currency)
    }

    private fun spendingTransactions(
        transactions: Iterable<Transaction>,
        period: DateRange,
        currency: CurrencyCode,
    ): List<Transaction> = transactions.filter {
        !it.isDeleted && it.kind != TransactionKind.INCOME && it.bookedOn in period
    }.also { requireCurrency(it, currency) }

    private fun requireCurrency(transactions: Iterable<Transaction>, currency: CurrencyCode) {
        require(transactions.all { it.amount.currency == currency }) {
            "Spending metrics cannot mix currencies; convert before analysis"
        }
    }

    private fun sumSpend(rows: Iterable<Transaction>, currency: CurrencyCode): Money =
        rows.fold(Money.zero(currency)) { sum, transaction -> sum + transaction.spendingContribution() }
}
