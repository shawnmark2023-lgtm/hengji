package com.hengji.insights

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Money
import com.hengji.domain.UsageEvent
import com.hengji.domain.UsageEventId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetAndRankingTest {
    @Test
    fun `low-use asset with residual value becomes a sale candidate`() {
        val asset = Asset(
            id = AssetId("camera"),
            name = "Camera",
            categoryId = CategoryId("electronics"),
            purchasePrice = Money(10_000, CurrencyCode.CNY),
            purchasedOn = LocalDate(2026, 1, 1),
            currentEstimatedValue = Money(4_000, CurrencyCode.CNY),
        )
        val insights = AssetOpportunityAnalyzer.analyze(
            assets = listOf(asset),
            maintenanceCosts = emptyList(),
            usageEvents = listOf(
                UsageEvent(UsageEventId("u1"), asset.id, LocalDate(2026, 1, 20)),
                UsageEvent(UsageEventId("u2"), asset.id, LocalDate(2026, 2, 20)),
            ),
            asOf = LocalDate(2026, 4, 11),
        )

        val insight = insights.single()
        assertEquals(InsightType.SELL_CANDIDATE, insight.type)
        assertEquals(3_600, insight.estimatedImpact?.minorUnits)
        assertTrue(insight.evidence.any { it.code == "asset.uses_per_30_days" })
    }

    @Test
    fun `ranker uses impact confidence and actionability and deduplicates`() {
        val low = insight("low", "same", 4_000, 5_000, 5_000)
        val high = insight("high", "same", 8_000, 8_000, 8_000)
        val other = insight("other", "other", 5_000, 7_000, 7_000)

        val ranked = InsightRanker.rank(listOf(low, high, other))
        assertEquals(listOf("high", "other"), ranked.map { it.id })
    }

    @Test
    fun `ranker respects muted and ignored local preferences`() {
        val category = insight("category", "cat-key", 8_000, 8_000, 8_000)
        val other = insight("other", "other-key", 5_000, 5_000, 5_000).copy(type = InsightType.BUDGET_PACE)
        val ranked = InsightRanker.rank(
            listOf(category, other),
            InsightPreferences(
                mutedTypes = setOf(InsightType.CATEGORY_CONCENTRATION),
                ignoredDeduplicationKeys = setOf("other-key"),
            ),
        )
        assertTrue(ranked.isEmpty())
    }

    private fun insight(id: String, key: String, impact: Int, confidence: Int, actionability: Int) = Insight(
        id = id,
        deduplicationKey = key,
        type = InsightType.CATEGORY_CONCENTRATION,
        title = "Title",
        summary = "Summary",
        evidence = listOf(InsightEvidence("test", EvidenceValue.Count(1))),
        estimatedImpact = null,
        impact = RuleScore(impact),
        confidence = RuleScore(confidence),
        actionability = RuleScore(actionability),
        action = InsightAction("review", "Review"),
    )
}
