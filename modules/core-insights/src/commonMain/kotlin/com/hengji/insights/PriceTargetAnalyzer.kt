package com.hengji.insights

import com.hengji.domain.Asset
import com.hengji.domain.AssetStatus
import com.hengji.domain.MarketEstimatePolicy
import com.hengji.domain.MarketQuote
import com.hengji.domain.MarketQuoteEstimator
import com.hengji.domain.QuoteProvenance
import kotlinx.datetime.LocalDate

/**
 * Evaluates user-defined sale targets from local quote history.
 *
 * Demo data, stale quotes, low-sample intervals and the asset's undated manual estimate can never
 * trigger this rule. Platform notification delivery remains outside the deterministic insight layer.
 */
object PriceTargetAnalyzer {
    fun analyze(
        assets: Iterable<Asset>,
        quotes: Iterable<MarketQuote>,
        asOf: LocalDate,
        policy: MarketEstimatePolicy = MarketEstimatePolicy(),
    ): List<Insight> {
        val quotesByAsset = quotes.groupBy { it.assetId }
        return assets.mapNotNull { asset ->
            val target = asset.saleTargetPrice ?: return@mapNotNull null
            if (asset.status != AssetStatus.ACTIVE && asset.status != AssetStatus.STORED) {
                return@mapNotNull null
            }

            val eligibleQuotes = quotesByAsset[asset.id].orEmpty().filter {
                it.provenance != QuoteProvenance.DEMO && it.price.currency == target.currency
            }
            val estimate = MarketQuoteEstimator.estimate(
                assetId = asset.id,
                quotes = eligibleQuotes,
                asOf = asOf,
                policy = policy,
            ) ?: return@mapNotNull null
            val median = estimate.median ?: return@mapNotNull null
            if (estimate.acceptedQuoteCount < 3 || median < target || estimate.includesDemoData) {
                return@mapNotNull null
            }

            val quoteAgeDays = (asOf.toEpochDays() - estimate.newestAcceptedQuoteOn.toEpochDays()).toInt()
            val sourceLabel = when {
                estimate.isEntirelyLiveData -> "market_live_or_licensed"
                estimate.includesLiveData && estimate.includesManualData -> "market_mixed_live_manual"
                estimate.includesLiveData -> "market_live_or_licensed"
                else -> "market_manual_quotes"
            }
            val targetKey = "${asset.id.value}:${target.currency.value}:${target.minorUnits}"
            Insight(
                id = "price-target-reached:$targetKey",
                deduplicationKey = "asset:${asset.id.value}:price-target:${target.currency.value}:${target.minorUnits}",
                type = InsightType.PRICE_TARGET_REACHED,
                title = "${asset.name} reached its sale target",
                summary = "The fresh, presentable market median meets or exceeds the sale target you set.",
                evidence = listOf(
                    InsightEvidence(
                        code = "asset.market_median",
                        observed = EvidenceValue.Amount(median),
                        threshold = EvidenceValue.Amount(target),
                        relatedIds = listOf(asset.id.value),
                    ),
                    InsightEvidence(
                        code = "asset.accepted_quote_count",
                        observed = EvidenceValue.Count(estimate.acceptedQuoteCount.toLong()),
                        threshold = EvidenceValue.Count(3),
                    ),
                    InsightEvidence(
                        code = "asset.newest_quote_age",
                        observed = EvidenceValue.Days(quoteAgeDays),
                        threshold = EvidenceValue.Days(policy.maximumQuoteAgeDays),
                    ),
                    InsightEvidence(
                        code = "asset.estimate_source",
                        observed = EvidenceValue.Text(sourceLabel),
                    ),
                ),
                estimatedImpact = null,
                impact = RuleScore(8_000),
                confidence = RuleScore(estimate.confidence.basisPoints),
                actionability = RuleScore(9_000),
                action = InsightAction("asset.review_sale", "Review sale options", asset.id.value),
            )
        }
    }
}
