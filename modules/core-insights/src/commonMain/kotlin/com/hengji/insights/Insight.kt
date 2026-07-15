package com.hengji.insights

import com.hengji.domain.Money

data class RuleScore(val basisPoints: Int) {
    init {
        require(basisPoints in 0..10_000) { "Rule score must be between 0 and 10,000 basis points" }
    }
}

enum class InsightType {
    CATEGORY_CONCENTRATION,
    BUDGET_PACE,
    SPENDING_TREND,
    MERCHANT_CONCENTRATION,
    LARGE_EXPENSE,
    POSSIBLE_DUPLICATE,
    POSSIBLE_SUBSCRIPTION,
    LOW_USAGE_ASSET,
    SELL_CANDIDATE,
}

enum class InsightFeedback {
    NEW,
    ADOPTED,
    SNOOZED,
    IGNORED,
}

sealed interface EvidenceValue {
    data class Amount(val value: Money) : EvidenceValue
    data class Count(val value: Long) : EvidenceValue
    data class Days(val value: Int) : EvidenceValue
    data class BasisPoints(val value: Long) : EvidenceValue
    data class Text(val value: String) : EvidenceValue
}

data class InsightEvidence(
    val code: String,
    val observed: EvidenceValue,
    val threshold: EvidenceValue? = null,
    val relatedIds: List<String> = emptyList(),
) {
    init {
        require(code.isNotBlank()) { "Evidence code cannot be blank" }
        require(relatedIds.none { it.isBlank() }) { "Evidence ids cannot be blank" }
    }
}

data class InsightAction(
    val code: String,
    val label: String,
    val targetId: String? = null,
) {
    init {
        require(code.isNotBlank()) { "Action code cannot be blank" }
        require(label.isNotBlank()) { "Action label cannot be blank" }
    }
}

data class Insight(
    val id: String,
    val deduplicationKey: String,
    val type: InsightType,
    val title: String,
    val summary: String,
    val evidence: List<InsightEvidence>,
    val estimatedImpact: Money?,
    val impact: RuleScore,
    val confidence: RuleScore,
    val actionability: RuleScore,
    val action: InsightAction,
    val feedback: InsightFeedback = InsightFeedback.NEW,
) {
    init {
        require(id.isNotBlank()) { "Insight id cannot be blank" }
        require(deduplicationKey.isNotBlank()) { "Insight deduplication key cannot be blank" }
        require(title.isNotBlank()) { "Insight title cannot be blank" }
        require(summary.isNotBlank()) { "Insight summary cannot be blank" }
        require(evidence.isNotEmpty()) { "Insight must include explainable evidence" }
        estimatedImpact?.requireNonNegative("Estimated impact")
    }

    /** impact x confidence x actionability, normalized to a 0..10,000 score. */
    val rankScore: Long
        get() = impact.basisPoints.toLong() * confidence.basisPoints * actionability.basisPoints / 100_000_000L
}

data class InsightPreferences(
    val mutedTypes: Set<InsightType> = emptySet(),
    val ignoredDeduplicationKeys: Set<String> = emptySet(),
)

object InsightRanker {
    fun rank(
        insights: Iterable<Insight>,
        preferences: InsightPreferences = InsightPreferences(),
    ): List<Insight> {
        val bestByKey = mutableMapOf<String, Insight>()
        insights.forEach { insight ->
            if (insight.type in preferences.mutedTypes ||
                insight.deduplicationKey in preferences.ignoredDeduplicationKeys ||
                insight.feedback == InsightFeedback.IGNORED
            ) {
                return@forEach
            }
            val current = bestByKey[insight.deduplicationKey]
            if (current == null || insight.rankScore > current.rankScore ||
                (insight.rankScore == current.rankScore && insight.id < current.id)
            ) {
                bestByKey[insight.deduplicationKey] = insight
            }
        }
        return bestByKey.values.sortedWith(compareByDescending<Insight> { it.rankScore }.thenBy { it.id })
    }
}
