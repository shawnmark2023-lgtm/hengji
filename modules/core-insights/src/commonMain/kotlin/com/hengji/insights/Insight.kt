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
    PRICE_TARGET_REACHED,
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
    val adoptedDeduplicationKeys: Set<String> = emptySet(),
    val snoozedUntilEpochMillisByKey: Map<String, Long> = emptyMap(),
    val feedbackTypeByKey: Map<String, InsightType> = emptyMap(),
) {
    init {
        require(ignoredDeduplicationKeys.none(String::isBlank)) {
            "Ignored insight keys cannot be blank"
        }
        require(adoptedDeduplicationKeys.none(String::isBlank)) {
            "Adopted insight keys cannot be blank"
        }
        require(snoozedUntilEpochMillisByKey.keys.none(String::isBlank)) {
            "Snoozed insight keys cannot be blank"
        }
        require(snoozedUntilEpochMillisByKey.values.all { it >= 0 }) {
            "Snooze deadlines cannot be negative"
        }
        require(feedbackTypeByKey.keys.none(String::isBlank)) {
            "Feedback insight keys cannot be blank"
        }

        val snoozedKeys = snoozedUntilEpochMillisByKey.keys
        require(adoptedDeduplicationKeys.intersect(ignoredDeduplicationKeys).isEmpty() &&
            adoptedDeduplicationKeys.intersect(snoozedKeys).isEmpty() &&
            ignoredDeduplicationKeys.intersect(snoozedKeys).isEmpty()
        ) {
            "Adopted, ignored, and snoozed insight keys must be mutually exclusive"
        }
        require(
            feedbackTypeByKey.keys.all {
                it in adoptedDeduplicationKeys ||
                    it in ignoredDeduplicationKeys ||
                    it in snoozedUntilEpochMillisByKey
            },
        ) {
            "Feedback type metadata must reference a persisted feedback action"
        }
    }
}

object InsightRanker {
    fun rank(
        insights: Iterable<Insight>,
        preferences: InsightPreferences = InsightPreferences(),
        nowEpochMillis: Long = 0L,
    ): List<Insight> {
        require(nowEpochMillis >= 0) { "Current time cannot be negative" }
        val bestByKey = mutableMapOf<String, Insight>()
        insights.forEach { insight ->
            val key = insight.deduplicationKey
            val snoozedUntilEpochMillis = preferences.snoozedUntilEpochMillisByKey[key]
            if (insight.type in preferences.mutedTypes ||
                key in preferences.ignoredDeduplicationKeys ||
                insight.feedback == InsightFeedback.IGNORED ||
                (snoozedUntilEpochMillis != null && nowEpochMillis < snoozedUntilEpochMillis)
            ) {
                return@forEach
            }
            val candidate = when {
                key in preferences.adoptedDeduplicationKeys -> insight.copy(feedback = InsightFeedback.ADOPTED)
                snoozedUntilEpochMillis != null -> insight.copy(feedback = InsightFeedback.NEW)
                else -> insight
            }
            val current = bestByKey[key]
            if (current == null || candidate.rankScore > current.rankScore ||
                (candidate.rankScore == current.rankScore && candidate.id < current.id)
            ) {
                bestByKey[key] = candidate
            }
        }
        val affinities = InsightPreferenceWeights.affinities(preferences)
        return bestByKey.values.sortedWith(
            compareByDescending<Insight> {
                it.rankScore * affinities.getValue(it.type).rankingMultiplierBasisPoints / 10_000L
            }.thenBy { it.id },
        )
    }
}
