package com.hengji.insights

import com.hengji.domain.Transaction
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.coroutines.cancellation.CancellationException

enum class InsightLearningStage {
    STARTING,
    LEARNING,
    PERSONALIZED,
    ESTABLISHED,
}

data class InsightTypeAffinity(
    val type: InsightType,
    val helpfulCount: Int,
    val notRelevantCount: Int,
    val snoozedCount: Int,
    val rankingMultiplierBasisPoints: Int,
) {
    init {
        require(helpfulCount >= 0 && notRelevantCount >= 0 && snoozedCount >= 0)
        require(rankingMultiplierBasisPoints in 7_000..13_000)
    }
}

/**
 * The inspectable profile and ranking layer that feeds the built-in local language model. It is
 * rebuilt from the ledger and explicit feedback so eligibility and financial claims remain
 * reproducible instead of treating generated text as truth.
 */
data class PersonalInsightProfile(
    val stage: InsightLearningStage,
    val activeTransactionCount: Int,
    val observedDays: Int,
    val feedbackCount: Int,
    val confidenceBasisPoints: Int,
    val affinities: Map<InsightType, InsightTypeAffinity>,
    val observedExpenseMonthCount: Int = 0,
    val firstAnalysisEligible: Boolean = observedDays >= FIRST_PERSONAL_ANALYSIS_DAYS,
) {
    init {
        require(activeTransactionCount >= 0)
        require(observedDays >= 0)
        require(feedbackCount >= 0)
        require(confidenceBasisPoints in 0..10_000)
        require(affinities.keys == affinities.values.mapTo(mutableSetOf()) { it.type })
        require(observedExpenseMonthCount >= 0)
    }

    val preferredTypes: List<InsightType>
        get() = affinities.values
            .filter { it.rankingMultiplierBasisPoints > 10_000 }
            .sortedWith(
                compareByDescending<InsightTypeAffinity> { it.rankingMultiplierBasisPoints }
                    .thenBy { it.type.name },
            )
            .map { it.type }

    fun rankingMultiplier(type: InsightType): Int =
        affinities[type]?.rankingMultiplierBasisPoints ?: 10_000
}

object PersonalInsightProfileBuilder {
    fun build(
        transactions: Iterable<Transaction>,
        asOf: LocalDate,
        preferences: InsightPreferences = InsightPreferences(),
    ): PersonalInsightProfile {
        val active = transactions.filter {
            !it.isDeleted && it.bookedOn <= asOf && it.kind == TransactionKind.EXPENSE
        }
        val earliest = active.minOfOrNull { it.bookedOn }
        val observedDays = earliest?.let {
            (asOf.toEpochDays() - it.toEpochDays() + 1).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } ?: 0
        val affinities = InsightPreferenceWeights.affinities(preferences)
        val feedbackCount = preferences.feedbackTypeByKey.size
        val observedExpenseMonthCount = active
            .mapTo(mutableSetOf()) { it.bookedOn.year * 12 + it.bookedOn.month.ordinal }
            .size
        val firstAnalysisEligible = observedDays >= FIRST_PERSONAL_ANALYSIS_DAYS
        val stage = when {
            !firstAnalysisEligible -> InsightLearningStage.STARTING
            active.size < 40 || feedbackCount < 2 -> InsightLearningStage.LEARNING
            active.size < 120 || feedbackCount < 8 -> InsightLearningStage.PERSONALIZED
            else -> InsightLearningStage.ESTABLISHED
        }
        val dataScore = (active.size.toLong() * 5_000L / 120L).coerceAtMost(5_000L)
        val horizonScore = (observedDays.toLong() * 2_500L / 180L).coerceAtMost(2_500L)
        val feedbackScore = (feedbackCount.toLong() * 2_500L / 12L).coerceAtMost(2_500L)
        return PersonalInsightProfile(
            stage = stage,
            activeTransactionCount = active.size,
            observedDays = observedDays,
            feedbackCount = feedbackCount,
            confidenceBasisPoints = (dataScore + horizonScore + feedbackScore).toInt(),
            affinities = affinities,
            observedExpenseMonthCount = observedExpenseMonthCount,
            firstAnalysisEligible = firstAnalysisEligible,
        )
    }
}

const val FIRST_PERSONAL_ANALYSIS_DAYS: Int = 90

object InsightPreferenceWeights {
    fun affinities(preferences: InsightPreferences): Map<InsightType, InsightTypeAffinity> {
        val feedbackByType = preferences.feedbackTypeByKey.entries.groupBy({ it.value }, { it.key })
        return enumValues<InsightType>().associateWith { type ->
            val keys = feedbackByType[type].orEmpty()
            val helpful = keys.count(preferences.adoptedDeduplicationKeys::contains)
            val notRelevant = keys.count(preferences.ignoredDeduplicationKeys::contains)
            val snoozed = keys.count(preferences.snoozedUntilEpochMillisByKey::containsKey)
            val multiplier = (
                10_000L +
                    helpful.toLong() * 800L -
                    notRelevant.toLong() * 1_000L -
                    snoozed.toLong() * 300L
                ).coerceIn(7_000L, 13_000L).toInt()
            InsightTypeAffinity(
                type = type,
                helpfulCount = helpful,
                notRelevantCount = notRelevant,
                snoozedCount = snoozed,
                rankingMultiplierBasisPoints = multiplier,
            )
        }
    }

    fun rankingMultiplier(preferences: InsightPreferences, type: InsightType): Int =
        affinities(preferences).getValue(type).rankingMultiplierBasisPoints
}

/**
 * Whitelisted local-only context for the built-in LLM. No transaction rows, merchant names,
 * notes, account identifiers or import payloads can be represented by this contract.
 */
data class PersonalInsightModelContext(
    val protocolVersion: Int = 2,
    val learningStage: String,
    val confidenceBasisPoints: Int,
    val transactionCountBucket: String,
    val historyDaysBucket: String,
    val feedbackCountBucket: String,
    val preferredInsightTypes: List<String>,
    val candidates: List<PersonalInsightModelCandidate>,
    val exactExpenseCount: Int = 0,
    val exactHistoryDays: Int = 0,
    val observedExpenseMonthCount: Int = 0,
    val priorAnalysisSummaries: List<String> = emptyList(),
) {
    init {
        require(protocolVersion == 2)
        require(learningStage.matches(Regex("[a-z-]{1,32}")))
        require(confidenceBasisPoints in 0..10_000)
        require(transactionCountBucket in setOf("0", "1-9", "10-39", "40-119", "120+"))
        require(historyDaysBucket in setOf("0", "1-13", "14-59", "60-179", "180+"))
        require(feedbackCountBucket in setOf("0", "1", "2-7", "8-19", "20+"))
        require(preferredInsightTypes.size <= 5)
        require(candidates.size in 1..5)
        require(candidates.distinctBy { it.candidateKey }.size == candidates.size)
        require(exactExpenseCount >= 0 && exactHistoryDays >= 0 && observedExpenseMonthCount >= 0)
        require(priorAnalysisSummaries.size <= 3)
        require(priorAnalysisSummaries.all { it.length in 1..300 && !containsUnsafeModelText(it) })
    }
}

data class PersonalInsightModelCandidate(
    val candidateKey: String,
    val insightType: String,
    val evidenceCodes: List<String>,
    val evidenceBuckets: Map<String, String>,
    val confidenceBasisPoints: Int,
    val impactBand: String,
    val exactEvidence: List<PersonalInsightModelEvidence> = emptyList(),
) {
    init {
        require(candidateKey.matches(Regex("[A-Za-z0-9:._+-]{1,160}")))
        require(insightType.matches(Regex("[a-z0-9_-]{1,48}")))
        require(evidenceCodes.size in 1..8)
        require(evidenceCodes.all { it.matches(Regex("[a-z0-9._-]{1,80}")) })
        require(evidenceBuckets.keys.all(evidenceCodes::contains))
        require(evidenceBuckets.values.all { it.matches(Regex("[a-z0-9+-]{1,32}")) })
        require(confidenceBasisPoints in 0..10_000)
        require(impactBand in setOf("none", "low", "medium", "high", "very-high"))
        require(exactEvidence.size in 1..8)
        require(exactEvidence.map { it.code }.toSet() == evidenceCodes.toSet())
    }
}

data class PersonalInsightModelEvidence(
    val code: String,
    val kind: String,
    val numericValue: Long? = null,
    val textValue: String? = null,
) {
    init {
        require(code.matches(Regex("[a-z0-9._-]{1,80}")))
        require(kind in setOf("amount-minor", "basis-points", "count", "days", "text"))
        require((kind == "text") == (textValue != null))
        require((kind == "text") != (numericValue != null))
        require(textValue == null || (textValue.length in 1..80 && !containsUnsafeModelText(textValue)))
    }
}

private fun containsUnsafeModelText(value: String): Boolean = value.any { character ->
    character.code < 32 && character !in setOf('\n', '\t') ||
        character.code == 127 ||
        character in '\u200B'..'\u200F' ||
        character in '\u202A'..'\u202E' ||
        character in '\u2060'..'\u206F' ||
        character == '\uFEFF'
}

/**
 * Keeps the mapping from opaque model candidates back to local insights outside the provider
 * payload. Deduplication keys can contain local entity identifiers and must never leave the app.
 */
data class PreparedPersonalInsightModelRequest(
    val context: PersonalInsightModelContext,
    private val localDeduplicationKeyByCandidate: Map<String, String>,
) {
    init {
        require(localDeduplicationKeyByCandidate.keys == context.candidates.mapTo(mutableSetOf()) { it.candidateKey })
        require(localDeduplicationKeyByCandidate.values.none(String::isBlank))
    }

    fun localDeduplicationKey(candidateKey: String): String? = localDeduplicationKeyByCandidate[candidateKey]
}

data class PersonalInsightModelAnswer(
    val candidateKey: String,
    val headline: String,
    val summary: String,
    val evidenceCodes: List<String>,
    val actionLabel: String,
) {
    init {
        require(candidateKey.isNotBlank())
        require(headline.length in 1..80)
        require(summary.length in 1..500)
        require(evidenceCodes.size in 1..8)
        require(actionLabel.length in 1..80)
    }
}

interface PersonalInsightModelProvider {
    val providerId: String
    val privacyReviewed: Boolean

    suspend fun generate(context: PersonalInsightModelContext): PersonalInsightModelAnswer
}

sealed interface PersonalInsightGenerationResult {
    data class Generated(
        val localDeduplicationKey: String,
        val answer: PersonalInsightModelAnswer,
        val providerDisclosure: String,
    ) : PersonalInsightGenerationResult

    data class LocalFallback(val reason: String) : PersonalInsightGenerationResult
}

class PersonalInsightModelOrchestrator(
    private val provider: PersonalInsightModelProvider? = null,
) {
    suspend fun generate(
        consent: InsightExplanationConsent,
        request: PreparedPersonalInsightModelRequest,
    ): PersonalInsightGenerationResult {
        if (!consent.enabled) return PersonalInsightGenerationResult.LocalFallback("model-consent-disabled")
        val activeProvider = provider
            ?: return PersonalInsightGenerationResult.LocalFallback("model-provider-unavailable")
        if (!activeProvider.privacyReviewed) {
            return PersonalInsightGenerationResult.LocalFallback("model-provider-not-privacy-reviewed")
        }
        val answer = try {
            activeProvider.generate(request.context)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return PersonalInsightGenerationResult.LocalFallback("model-provider-failed")
        }
        if (listOf(answer.headline, answer.summary, answer.actionLabel).any(::containsUnsafeDisplayCharacter)) {
            return PersonalInsightGenerationResult.LocalFallback("model-returned-unsafe-display-characters")
        }
        val candidate = request.context.candidates.firstOrNull { it.candidateKey == answer.candidateKey }
            ?: return PersonalInsightGenerationResult.LocalFallback("model-selected-unknown-candidate")
        if (answer.evidenceCodes.any { it !in candidate.evidenceCodes }) {
            return PersonalInsightGenerationResult.LocalFallback("model-used-unapproved-evidence")
        }
        val localDeduplicationKey = request.localDeduplicationKey(answer.candidateKey)
            ?: return PersonalInsightGenerationResult.LocalFallback("model-candidate-mapping-missing")
        val providerId = activeProvider.providerId
            .trim()
            .take(60)
            .takeUnless(::containsUnsafeDisplayCharacter)
            ?.ifBlank { null }
            ?: "已配置模型"
        return PersonalInsightGenerationResult.Generated(
            localDeduplicationKey = localDeduplicationKey,
            answer = answer,
            providerDisclosure =
                "由 $providerId 基于本机汇总生成；金额与证据以本机计算为准。",
        )
    }

    private fun containsUnsafeDisplayCharacter(value: String): Boolean = value.any { character ->
        character.code < 32 ||
            character.code == 127 ||
            character in '\u200B'..'\u200F' ||
            character in '\u202A'..'\u202E' ||
            character in '\u2060'..'\u206F' ||
            character == '\uFEFF'
    }
}

object PersonalInsightModelContextFactory {
    fun create(
        profile: PersonalInsightProfile,
        rankedInsights: List<Insight>,
        priorAnalysisSummaries: List<String> = emptyList(),
    ): PreparedPersonalInsightModelRequest {
        require(rankedInsights.isNotEmpty()) { "At least one insight is required" }
        val selected = rankedInsights.take(5)
        val localKeys = selected.mapIndexed { index, insight -> "candidate-${index + 1}" to insight.deduplicationKey }.toMap()
        val context = PersonalInsightModelContext(
            learningStage = profile.stage.name.lowercase(),
            confidenceBasisPoints = profile.confidenceBasisPoints,
            transactionCountBucket = bucket(profile.activeTransactionCount, 10, 40, 120),
            historyDaysBucket = bucket(profile.observedDays, 14, 60, 180),
            feedbackCountBucket = when (profile.feedbackCount) {
                0 -> "0"
                1 -> "1"
                in 2..7 -> "2-7"
                in 8..19 -> "8-19"
                else -> "20+"
            },
            preferredInsightTypes = profile.preferredTypes.take(5).map { it.name.lowercase() },
            exactExpenseCount = profile.activeTransactionCount,
            exactHistoryDays = profile.observedDays,
            observedExpenseMonthCount = profile.observedExpenseMonthCount,
            priorAnalysisSummaries = priorAnalysisSummaries.takeLast(3),
            candidates = selected.mapIndexed { index, insight ->
                PersonalInsightModelCandidate(
                    candidateKey = "candidate-${index + 1}",
                    insightType = insight.type.name.lowercase(),
                    evidenceCodes = insight.evidence.map { it.code }.distinct().take(8),
                    evidenceBuckets = insight.evidence
                        .distinctBy { it.code }
                        .take(8)
                        .associate { it.code to it.observed.toPrivacyBucket() },
                    confidenceBasisPoints = insight.confidence.basisPoints,
                    impactBand = insight.estimatedImpact.toImpactBand(),
                    exactEvidence = insight.evidence
                        .distinctBy { it.code }
                        .take(8)
                        .map { evidence -> evidence.observed.toExactModelEvidence(evidence.code) },
                )
            },
        )
        return PreparedPersonalInsightModelRequest(context, localKeys)
    }

    private fun bucket(value: Int, first: Int, second: Int, third: Int): String = when {
        value == 0 -> "0"
        value < first -> "1-${first - 1}"
        value < second -> "$first-${second - 1}"
        value < third -> "$second-${third - 1}"
        else -> "$third+"
    }

    private fun com.hengji.domain.Money?.toImpactBand(): String {
        val amount = this?.minorUnits ?: return "none"
        return when {
            amount < 10_000 -> "low"
            amount < 100_000 -> "medium"
            amount < 500_000 -> "high"
            else -> "very-high"
        }
    }

    private fun EvidenceValue.toPrivacyBucket(): String = when (this) {
        is EvidenceValue.Amount -> value.toImpactBand()
        is EvidenceValue.BasisPoints -> {
            val lower = (value.coerceIn(0, 10_000) / 500) * 5
            val upper = (lower + 4).coerceAtMost(100)
            "$lower-$upper-percent"
        }
        is EvidenceValue.Count -> when {
            value <= 0 -> "0"
            value < 5 -> "1-4"
            value < 20 -> "5-19"
            value < 100 -> "20-99"
            else -> "100+"
        }
        is EvidenceValue.Days -> when {
            value <= 0 -> "0"
            value < 7 -> "1-6-days"
            value < 30 -> "7-29-days"
            value < 90 -> "30-89-days"
            else -> "90+-days"
        }
        is EvidenceValue.Text -> "present"
    }

    private fun EvidenceValue.toExactModelEvidence(code: String): PersonalInsightModelEvidence = when (this) {
        is EvidenceValue.Amount -> PersonalInsightModelEvidence(
            code = code,
            kind = "amount-minor",
            numericValue = value.minorUnits,
            textValue = null,
        )
        is EvidenceValue.BasisPoints -> PersonalInsightModelEvidence(code, "basis-points", value)
        is EvidenceValue.Count -> PersonalInsightModelEvidence(code, "count", value)
        is EvidenceValue.Days -> PersonalInsightModelEvidence(code, "days", value.toLong())
        is EvidenceValue.Text -> PersonalInsightModelEvidence(code, "text", textValue = value)
    }
}
