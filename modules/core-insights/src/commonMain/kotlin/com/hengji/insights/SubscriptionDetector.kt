package com.hengji.insights

import com.hengji.domain.CurrencyCode
import com.hengji.domain.ExactMath
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate

enum class SubscriptionCadence(val minimumDays: Int, val maximumDays: Int) {
    WEEKLY(5, 9),
    MONTHLY(25, 35),
    YEARLY(350, 380),
}

data class SubscriptionCandidate(
    val merchant: Merchant,
    val cadence: SubscriptionCadence,
    val typicalAmount: Money,
    val paymentCount: Int,
    val medianIntervalDays: Int,
    val nextExpectedOn: LocalDate,
    val confidence: RuleScore,
    val transactionIds: List<TransactionId>,
)

data class SubscriptionDetectorConfig(
    val minimumPayments: Int = 3,
    val amountToleranceBasisPoints: Long = 750,
) {
    init {
        require(minimumPayments >= 3) { "Subscription detection requires at least three payments" }
        require(amountToleranceBasisPoints in 0..5_000) { "Amount tolerance must be between 0% and 50%" }
    }
}

object SubscriptionDetector {
    fun detect(
        transactions: Iterable<Transaction>,
        currency: CurrencyCode,
        config: SubscriptionDetectorConfig = SubscriptionDetectorConfig(),
    ): List<SubscriptionCandidate> {
        val expenses = transactions.filter {
            !it.isDeleted && it.kind == TransactionKind.EXPENSE && it.merchant != null
        }
        require(expenses.all { it.amount.currency == currency }) {
            "Subscription detection cannot mix currencies"
        }
        return expenses.groupBy { it.merchant!!.normalizedName }.mapNotNull { (_, merchantRows) ->
            candidateFor(merchantRows.sortedBy { it.bookedOn }, config)
        }.sortedWith(compareByDescending<SubscriptionCandidate> { it.confidence.basisPoints }
            .thenByDescending { it.typicalAmount.minorUnits }
            .thenBy { it.merchant.normalizedName })
    }

    private fun candidateFor(
        rows: List<Transaction>,
        config: SubscriptionDetectorConfig,
    ): SubscriptionCandidate? {
        if (rows.size < config.minimumPayments) return null
        val medianAmount = SpendingMetrics.medianMoney(rows.map { it.amount })
        if (medianAmount.minorUnits <= 0) return null
        val stableAmountRows = rows.filter {
            val delta = if (it.amount >= medianAmount) it.amount - medianAmount else medianAmount - it.amount
            SpendingMetrics.ratioOrZero(delta.minorUnits, medianAmount.minorUnits) <= config.amountToleranceBasisPoints
        }
        if (stableAmountRows.size < config.minimumPayments) return null

        val intervals = stableAmountRows.zipWithNext { first, second ->
            ExactMath.subtract(second.bookedOn.toEpochDays(), first.bookedOn.toEpochDays())
        }.filter { it > 0 }
        if (intervals.size < config.minimumPayments - 1) return null
        val medianInterval = medianLong(intervals).also {
            require(it <= Int.MAX_VALUE) { "Subscription interval exceeds supported day range" }
        }.toInt()
        val cadence = SubscriptionCadence.entries.firstOrNull {
            medianInterval in it.minimumDays..it.maximumDays
        } ?: return null
        val consistentIntervals = intervals.count { it in cadence.minimumDays..cadence.maximumDays }
        if (consistentIntervals * 4 < intervals.size * 3) return null

        val amountConsistency = stableAmountRows.size * 10_000 / rows.size
        val intervalConsistency = consistentIntervals * 10_000 / intervals.size
        val sampleBonus = ((stableAmountRows.size - config.minimumPayments) * 500).coerceAtMost(1_500)
        val confidence = (4_000 + amountConsistency / 4 + intervalConsistency / 4 + sampleBonus).coerceAtMost(10_000)
        val lastDate = stableAmountRows.last().bookedOn
        val nextEpochDay = ExactMath.add(lastDate.toEpochDays(), medianInterval.toLong())
        return SubscriptionCandidate(
            merchant = stableAmountRows.first().merchant!!,
            cadence = cadence,
            typicalAmount = medianAmount,
            paymentCount = stableAmountRows.size,
            medianIntervalDays = medianInterval,
            nextExpectedOn = LocalDate.fromEpochDays(nextEpochDay),
            confidence = RuleScore(confidence),
            transactionIds = stableAmountRows.map { it.id },
        )
    }

    private fun medianLong(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            val lower = sorted[middle - 1]
            lower + (sorted[middle] - lower) / 2
        }
    }
}

data class DuplicateChargeCandidate(
    val merchant: Merchant,
    val amount: Money,
    val transactionIds: List<TransactionId>,
    val firstChargedOn: LocalDate,
    val lastChargedOn: LocalDate,
)

object DuplicateChargeDetector {
    fun detect(
        transactions: Iterable<Transaction>,
        currency: CurrencyCode,
        maximumDayGap: Int = 1,
    ): List<DuplicateChargeCandidate> {
        require(maximumDayGap >= 0) { "Duplicate window cannot be negative" }
        val rows = transactions.filter {
            !it.isDeleted && it.kind == TransactionKind.EXPENSE && it.merchant != null
        }
        require(rows.all { it.amount.currency == currency }) { "Duplicate detection cannot mix currencies" }
        data class Key(val merchant: String, val amount: Long)
        return rows.groupBy { Key(it.merchant!!.normalizedName, it.amount.minorUnits) }.flatMap { (_, matches) ->
            val sorted = matches.sortedBy { it.bookedOn }
            val clusters = mutableListOf<MutableList<Transaction>>()
            sorted.forEach { transaction ->
                val cluster = clusters.lastOrNull()
                val previous = cluster?.lastOrNull()
                if (previous != null &&
                    transaction.bookedOn.toEpochDays() - previous.bookedOn.toEpochDays() <= maximumDayGap.toLong()
                ) {
                    cluster.add(transaction)
                } else {
                    clusters.add(mutableListOf(transaction))
                }
            }
            clusters.filter { it.size >= 2 }.map { cluster ->
                DuplicateChargeCandidate(
                    merchant = cluster.first().merchant!!,
                    amount = cluster.first().amount,
                    transactionIds = cluster.map { it.id },
                    firstChargedOn = cluster.first().bookedOn,
                    lastChargedOn = cluster.last().bookedOn,
                )
            }
        }.sortedWith(compareByDescending<DuplicateChargeCandidate> { it.amount.minorUnits }
            .thenBy { it.merchant.normalizedName })
    }
}
