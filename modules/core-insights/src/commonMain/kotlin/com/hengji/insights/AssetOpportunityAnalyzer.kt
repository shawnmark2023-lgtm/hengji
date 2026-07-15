package com.hengji.insights

import com.hengji.domain.Asset
import com.hengji.domain.AssetCostCalculator
import com.hengji.domain.AssetId
import com.hengji.domain.AssetStatus
import com.hengji.domain.ExactMath
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MarketEstimate
import com.hengji.domain.Money
import com.hengji.domain.UsageEvent
import kotlinx.datetime.LocalDate

data class AssetOpportunityConfig(
    val minimumOwnedDays: Int = 90,
    val maximumUsesPerThirtyDaysBasisPoints: Long = 10_000,
    val conservativeSaleProceedsBasisPoints: Long = 9_000,
) {
    init {
        require(minimumOwnedDays > 0) { "Minimum ownership days must be positive" }
        require(maximumUsesPerThirtyDaysBasisPoints >= 0) { "Use-rate threshold cannot be negative" }
        require(conservativeSaleProceedsBasisPoints in 0..10_000) { "Sale proceeds factor must be 0%..100%" }
    }
}

object AssetOpportunityAnalyzer {
    fun analyze(
        assets: Iterable<Asset>,
        maintenanceCosts: Iterable<MaintenanceCost>,
        usageEvents: Iterable<UsageEvent>,
        asOf: LocalDate,
        marketEstimates: Map<AssetId, MarketEstimate> = emptyMap(),
        config: AssetOpportunityConfig = AssetOpportunityConfig(),
    ): List<Insight> {
        val costsByAsset = maintenanceCosts.groupBy { it.assetId }
        val usageByAsset = usageEvents.groupBy { it.assetId }
        return assets.filter { it.status == AssetStatus.ACTIVE || it.status == AssetStatus.STORED }.flatMap { asset ->
            analyzeAsset(
                asset = asset,
                maintenanceCosts = costsByAsset[asset.id].orEmpty(),
                usageEvents = usageByAsset[asset.id].orEmpty(),
                marketEstimate = marketEstimates[asset.id],
                asOf = asOf,
                config = config,
            )
        }
    }

    private fun analyzeAsset(
        asset: Asset,
        maintenanceCosts: List<MaintenanceCost>,
        usageEvents: List<UsageEvent>,
        marketEstimate: MarketEstimate?,
        asOf: LocalDate,
        config: AssetOpportunityConfig,
    ): List<Insight> {
        if (asOf < asset.purchasedOn) return emptyList()
        val residual = marketEstimate?.median ?: asset.currentEstimatedValue ?: Money.zero(asset.purchasePrice.currency)
        val metrics = AssetCostCalculator.calculate(
            asset = asset,
            maintenanceCosts = maintenanceCosts,
            usageEvents = usageEvents,
            asOf = asOf,
            residualValue = residual,
        )
        if (metrics.ownedDays < config.minimumOwnedDays) return emptyList()
        val usesPerThirtyDaysBasisPoints = if (metrics.ownedDays == 0) {
            0
        } else {
            SpendingMetrics.ratioOrZero(
                ExactMath.multiply(metrics.useQuantity, 30),
                metrics.ownedDays.toLong(),
            )
        }
        if (usesPerThirtyDaysBasisPoints > config.maximumUsesPerThirtyDaysBasisPoints) return emptyList()

        val commonEvidence = listOf(
            InsightEvidence(
                code = "asset.owned_days",
                observed = EvidenceValue.Days(metrics.ownedDays),
                threshold = EvidenceValue.Days(config.minimumOwnedDays),
                relatedIds = listOf(asset.id.value),
            ),
            InsightEvidence(
                code = "asset.uses_per_30_days",
                observed = EvidenceValue.BasisPoints(usesPerThirtyDaysBasisPoints),
                threshold = EvidenceValue.BasisPoints(config.maximumUsesPerThirtyDaysBasisPoints),
                relatedIds = usageEvents.map { it.id.value },
            ),
            InsightEvidence(
                code = "asset.net_cost",
                observed = EvidenceValue.Amount(metrics.netCost),
                relatedIds = listOf(asset.id.value),
            ),
        )

        if (residual.minorUnits > 0) {
            val proceeds = residual.multiplyAndDivide(config.conservativeSaleProceedsBasisPoints, 10_000)
            val recoveryRatio = if (metrics.totalOwnershipCost.minorUnits <= 0) {
                0
            } else {
                SpendingMetrics.ratioOrZero(residual.minorUnits, metrics.totalOwnershipCost.minorUnits).coerceIn(0, 10_000)
            }
            val sourceConfidence = marketEstimate?.confidence?.basisPoints ?: 4_000
            val sourceLabel = when {
                marketEstimate == null -> "asset_manual_estimate"
                marketEstimate.includesLiveData -> "market_live_or_licensed"
                marketEstimate.includesDemoData -> "market_includes_demo"
                else -> "market_manual_quotes"
            }
            return listOf(
                Insight(
                    id = "sell-candidate:${asset.id.value}",
                    deduplicationKey = "asset:${asset.id.value}:exit",
                    type = InsightType.SELL_CANDIDATE,
                    title = "Consider selling ${asset.name}",
                    summary = "Usage is below the configured threshold and the asset still has recoverable value.",
                    evidence = commonEvidence + listOf(
                        InsightEvidence("asset.residual_value", EvidenceValue.Amount(residual)),
                        InsightEvidence("asset.estimate_source", EvidenceValue.Text(sourceLabel)),
                    ),
                    estimatedImpact = proceeds,
                    impact = RuleScore((4_000 + recoveryRatio * 6 / 10).toInt().coerceAtMost(10_000)),
                    confidence = RuleScore(sourceConfidence.coerceIn(0, 10_000)),
                    actionability = RuleScore(8_500),
                    action = InsightAction("asset.review_sale", "Review sale options", asset.id.value),
                ),
            )
        }

        val monthlyAvoidableCost = metrics.netDailyCost
            ?.takeIf { it.minorUnits > 0 }
            ?.times(30)
        return listOf(
            Insight(
                id = "low-usage:${asset.id.value}",
                deduplicationKey = "asset:${asset.id.value}:usage",
                type = InsightType.LOW_USAGE_ASSET,
                title = "Review usage of ${asset.name}",
                summary = "The asset has been owned long enough to assess, but recorded use remains low.",
                evidence = commonEvidence,
                estimatedImpact = monthlyAvoidableCost,
                impact = RuleScore(3_500),
                confidence = RuleScore(if (usageEvents.isEmpty()) 5_000 else 7_500),
                actionability = RuleScore(7_500),
                action = InsightAction("asset.add_usage_or_plan", "Record usage or set a usage plan", asset.id.value),
            ),
        )
    }
}
