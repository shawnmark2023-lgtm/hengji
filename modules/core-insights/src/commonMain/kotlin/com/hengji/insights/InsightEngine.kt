package com.hengji.insights

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.DateRange
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MarketEstimate
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.UsageEvent
import kotlinx.datetime.LocalDate

data class InsightRuleConfig(
    val categoryConcentrationBasisPoints: Long = 3_500,
    val merchantConcentrationBasisPoints: Long = 3_000,
    val budgetPaceBasisPoints: Long = 12_000,
    val trendIncreaseBasisPoints: Long = 2_000,
    val optimizationTargetBasisPoints: Long = 1_000,
    val largeExpenseMultipleBasisPoints: Long = 30_000,
) {
    init {
        require(categoryConcentrationBasisPoints in 0..10_000)
        require(merchantConcentrationBasisPoints in 0..10_000)
        require(budgetPaceBasisPoints >= 10_000)
        require(trendIncreaseBasisPoints >= 0)
        require(optimizationTargetBasisPoints in 0..10_000)
        require(largeExpenseMultipleBasisPoints >= 10_000)
    }
}

data class InsightSnapshot(
    val asOf: LocalDate,
    val currency: CurrencyCode,
    val currentPeriod: DateRange,
    val previousPeriod: DateRange,
    val transactions: List<Transaction>,
    val budgets: List<Budget> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val maintenanceCosts: List<MaintenanceCost> = emptyList(),
    val usageEvents: List<UsageEvent> = emptyList(),
    val marketEstimates: Map<AssetId, MarketEstimate> = emptyMap(),
)

class InsightEngine(
    private val config: InsightRuleConfig = InsightRuleConfig(),
    private val assetConfig: AssetOpportunityConfig = AssetOpportunityConfig(),
) {
    fun generate(
        snapshot: InsightSnapshot,
        preferences: InsightPreferences = InsightPreferences(),
    ): List<Insight> {
        require(snapshot.currentPeriod.days > 0 && snapshot.previousPeriod.days > 0) {
            "Insight periods cannot be empty"
        }
        val insights = buildList {
            addCategoryInsights(snapshot)
            addTrendInsight(snapshot)
            addBudgetInsights(snapshot)
            addMerchantInsight(snapshot)
            addLargeExpenseInsights(snapshot)
            addDuplicateInsights(snapshot)
            addSubscriptionInsights(snapshot)
            addAll(
                AssetOpportunityAnalyzer.analyze(
                    assets = snapshot.assets,
                    maintenanceCosts = snapshot.maintenanceCosts,
                    usageEvents = snapshot.usageEvents,
                    asOf = snapshot.asOf,
                    marketEstimates = snapshot.marketEstimates,
                    config = assetConfig,
                ),
            )
        }
        return InsightRanker.rank(insights, preferences)
    }

    private fun MutableList<Insight>.addCategoryInsights(snapshot: InsightSnapshot) {
        val breakdown = SpendingMetrics.categoryBreakdown(
            snapshot.transactions,
            snapshot.currentPeriod,
            snapshot.currency,
        )
        breakdown.categories.filter {
            it.netSpend.minorUnits > 0 && (it.shareBasisPoints ?: Long.MIN_VALUE) >= config.categoryConcentrationBasisPoints
        }.forEach { category ->
            val impact = category.netSpend.multiplyAndDivide(config.optimizationTargetBasisPoints, 10_000)
            add(
                Insight(
                    id = "category-concentration:${category.categoryId.value}:${snapshot.currentPeriod.startInclusive}",
                    deduplicationKey = "category:${category.categoryId.value}:concentration",
                    type = InsightType.CATEGORY_CONCENTRATION,
                    title = "Review ${category.categoryId.value} spending",
                    summary = "This category represents a large share of net spending in the current period.",
                    evidence = listOf(
                        InsightEvidence(
                            code = "category.share",
                            observed = EvidenceValue.BasisPoints(category.shareBasisPoints ?: 0),
                            threshold = EvidenceValue.BasisPoints(config.categoryConcentrationBasisPoints),
                            relatedIds = listOf(category.categoryId.value),
                        ),
                        InsightEvidence("category.net_spend", EvidenceValue.Amount(category.netSpend)),
                    ),
                    estimatedImpact = impact,
                    impact = RuleScore((category.shareBasisPoints ?: 0).toInt().coerceIn(0, 10_000)),
                    confidence = RuleScore(9_500),
                    actionability = RuleScore(7_500),
                    action = InsightAction("transactions.filter_category", "Review category transactions", category.categoryId.value),
                ),
            )
        }
    }

    private fun MutableList<Insight>.addTrendInsight(snapshot: InsightSnapshot) {
        val trend = SpendingMetrics.comparePeriods(
            snapshot.transactions,
            snapshot.currentPeriod,
            snapshot.previousPeriod,
            snapshot.currency,
        )
        val change = trend.changeBasisPoints ?: return
        if (change < config.trendIncreaseBasisPoints || trend.delta.minorUnits <= 0) return
        add(
            Insight(
                id = "spending-trend:${snapshot.currentPeriod.startInclusive}",
                deduplicationKey = "spending:trend-increase",
                type = InsightType.SPENDING_TREND,
                title = "Spending increased from the previous period",
                summary = "Net expenses are above the comparison period after refunds are applied to their booking dates.",
                evidence = listOf(
                    InsightEvidence(
                        "spending.change",
                        EvidenceValue.BasisPoints(change),
                        EvidenceValue.BasisPoints(config.trendIncreaseBasisPoints),
                    ),
                    InsightEvidence("spending.delta", EvidenceValue.Amount(trend.delta)),
                    InsightEvidence("spending.previous", EvidenceValue.Amount(trend.previousNetSpend)),
                ),
                estimatedImpact = trend.delta,
                impact = RuleScore(change.toInt().coerceIn(0, 10_000)),
                confidence = RuleScore(9_500),
                actionability = RuleScore(6_500),
                action = InsightAction("analytics.compare_periods", "Compare changed categories"),
            ),
        )
    }

    private fun MutableList<Insight>.addBudgetInsights(snapshot: InsightSnapshot) {
        snapshot.budgets.forEach { budget ->
            require(budget.amount.currency == snapshot.currency) { "Budgets must use snapshot currency" }
            val burn = SpendingMetrics.budgetBurn(budget, snapshot.transactions, snapshot.asOf)
            val pace = burn.paceBasisPoints ?: return@forEach
            val projected = burn.projectedPeriodSpend ?: return@forEach
            if (pace < config.budgetPaceBasisPoints || projected <= budget.amount) return@forEach
            val overrun = projected - budget.amount
            add(
                Insight(
                    id = "budget-pace:${budget.id}:${snapshot.asOf}",
                    deduplicationKey = "budget:${budget.id}:pace",
                    type = InsightType.BUDGET_PACE,
                    title = "${budget.id} budget is burning quickly",
                    summary = "Current pace projects spending above the configured period budget.",
                    evidence = listOf(
                        InsightEvidence(
                            "budget.pace",
                            EvidenceValue.BasisPoints(pace),
                            EvidenceValue.BasisPoints(config.budgetPaceBasisPoints),
                            listOf(budget.id),
                        ),
                        InsightEvidence("budget.projected", EvidenceValue.Amount(projected)),
                        InsightEvidence("budget.limit", EvidenceValue.Amount(budget.amount)),
                    ),
                    estimatedImpact = overrun,
                    impact = RuleScore((pace - 10_000).toInt().coerceIn(1_000, 10_000)),
                    confidence = RuleScore(if (burn.elapsedDays * 4 >= burn.totalDays) 9_000 else 7_000),
                    actionability = RuleScore(9_000),
                    action = InsightAction("budget.review", "Review remaining budget", budget.id),
                ),
            )
        }
    }

    private fun MutableList<Insight>.addMerchantInsight(snapshot: InsightSnapshot) {
        val merchant = SpendingMetrics.merchantConcentration(
            snapshot.transactions,
            snapshot.currentPeriod,
            snapshot.currency,
        ).firstOrNull { it.netSpend.minorUnits > 0 } ?: return
        val share = merchant.shareBasisPoints ?: return
        if (share < config.merchantConcentrationBasisPoints) return
        add(
            Insight(
                id = "merchant-concentration:${merchant.merchant.normalizedName}:${snapshot.currentPeriod.startInclusive}",
                deduplicationKey = "merchant:${merchant.merchant.normalizedName}:concentration",
                type = InsightType.MERCHANT_CONCENTRATION,
                title = "Spending is concentrated at ${merchant.merchant.displayName}",
                summary = "One merchant accounts for a high share of merchant-tagged net spending.",
                evidence = listOf(
                    InsightEvidence(
                        "merchant.share",
                        EvidenceValue.BasisPoints(share),
                        EvidenceValue.BasisPoints(config.merchantConcentrationBasisPoints),
                    ),
                    InsightEvidence("merchant.net_spend", EvidenceValue.Amount(merchant.netSpend)),
                    InsightEvidence("merchant.transactions", EvidenceValue.Count(merchant.transactionCount.toLong())),
                ),
                estimatedImpact = merchant.netSpend.multiplyAndDivide(config.optimizationTargetBasisPoints, 10_000),
                impact = RuleScore(share.toInt().coerceIn(0, 10_000)),
                confidence = RuleScore(9_000),
                actionability = RuleScore(7_000),
                action = InsightAction("transactions.filter_merchant", "Review merchant transactions", merchant.merchant.normalizedName),
            ),
        )
    }

    private fun MutableList<Insight>.addLargeExpenseInsights(snapshot: InsightSnapshot) {
        SpendingMetrics.largeExpenses(
            snapshot.transactions,
            snapshot.currentPeriod,
            snapshot.currency,
            config.largeExpenseMultipleBasisPoints,
        ).take(3).forEach { anomaly ->
            add(
                Insight(
                    id = "large-expense:${anomaly.transaction.id.value}",
                    deduplicationKey = "transaction:${anomaly.transaction.id.value}:large",
                    type = InsightType.LARGE_EXPENSE,
                    title = "Large expense detected",
                    summary = "This expense is materially higher than the period's median expense.",
                    evidence = listOf(
                        InsightEvidence(
                            "expense.multiple",
                            EvidenceValue.BasisPoints(anomaly.multipleBasisPoints ?: 0),
                            EvidenceValue.BasisPoints(config.largeExpenseMultipleBasisPoints),
                            listOf(anomaly.transaction.id.value),
                        ),
                        InsightEvidence("expense.amount", EvidenceValue.Amount(anomaly.transaction.amount)),
                        InsightEvidence("expense.median", EvidenceValue.Amount(anomaly.medianExpense)),
                    ),
                    estimatedImpact = null,
                    impact = RuleScore(((anomaly.multipleBasisPoints ?: 10_000) / 3).toInt().coerceIn(2_000, 10_000)),
                    confidence = RuleScore(9_500),
                    actionability = RuleScore(5_500),
                    action = InsightAction("transaction.review", "Verify and categorize expense", anomaly.transaction.id.value),
                ),
            )
        }
    }

    private fun MutableList<Insight>.addDuplicateInsights(snapshot: InsightSnapshot) {
        DuplicateChargeDetector.detect(snapshot.transactions, snapshot.currency).forEach { candidate ->
            add(
                Insight(
                    id = "duplicate:${candidate.transactionIds.joinToString("+") { it.value }}",
                    deduplicationKey = "duplicate:${candidate.transactionIds.joinToString("+") { it.value }}",
                    type = InsightType.POSSIBLE_DUPLICATE,
                    title = "Possible duplicate charge from ${candidate.merchant.displayName}",
                    summary = "The same merchant and amount were recorded within the duplicate-charge window.",
                    evidence = listOf(
                        InsightEvidence(
                            "duplicate.count",
                            EvidenceValue.Count(candidate.transactionIds.size.toLong()),
                            EvidenceValue.Count(2),
                            candidate.transactionIds.map { it.value },
                        ),
                        InsightEvidence("duplicate.amount", EvidenceValue.Amount(candidate.amount)),
                    ),
                    estimatedImpact = candidate.amount,
                    impact = RuleScore(8_000),
                    confidence = RuleScore(7_000),
                    actionability = RuleScore(9_500),
                    action = InsightAction("transactions.compare", "Compare possible duplicate charges"),
                ),
            )
        }
    }

    private fun MutableList<Insight>.addSubscriptionInsights(snapshot: InsightSnapshot) {
        SubscriptionDetector.detect(snapshot.transactions, snapshot.currency).forEach { candidate ->
            val target = candidate.merchant.normalizedName
            add(
                Insight(
                    id = "subscription:$target",
                    deduplicationKey = "merchant:$target:subscription",
                    type = InsightType.POSSIBLE_SUBSCRIPTION,
                    title = "Possible ${candidate.cadence.name.lowercase()} subscription",
                    summary = "Similar charges recur at a stable interval; confirm before treating them as a subscription.",
                    evidence = listOf(
                        InsightEvidence(
                            "subscription.payment_count",
                            EvidenceValue.Count(candidate.paymentCount.toLong()),
                            EvidenceValue.Count(3),
                            candidate.transactionIds.map { it.value },
                        ),
                        InsightEvidence("subscription.interval", EvidenceValue.Days(candidate.medianIntervalDays)),
                        InsightEvidence("subscription.typical_amount", EvidenceValue.Amount(candidate.typicalAmount)),
                    ),
                    estimatedImpact = candidate.typicalAmount,
                    impact = RuleScore(6_000),
                    confidence = candidate.confidence,
                    actionability = RuleScore(9_000),
                    action = InsightAction("subscription.confirm", "Confirm or dismiss subscription", target),
                ),
            )
        }
    }
}
