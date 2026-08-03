package com.hengji.insights

import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersonalInsightLearningTest {
    @Test
    fun `profile becomes established only with history and explicit feedback`() {
        val feedback = (1..8).associate { index -> "trend-$index" to InsightType.SPENDING_TREND }
        val preferences = InsightPreferences(
            adoptedDeduplicationKeys = feedback.keys,
            feedbackTypeByKey = feedback,
        )
        val profile = PersonalInsightProfileBuilder.build(
            transactions = (1..130).map { index ->
                transaction(
                    id = "tx-$index",
                    bookedOn = if (index == 1) LocalDate(2025, 1, 1) else LocalDate(2025, 7, 20),
                )
            },
            asOf = LocalDate(2025, 7, 20),
            preferences = preferences,
        )

        assertEquals(InsightLearningStage.ESTABLISHED, profile.stage)
        assertEquals(130, profile.activeTransactionCount)
        assertEquals(8, profile.feedbackCount)
        assertTrue(profile.confidenceBasisPoints >= 8_000)
        assertEquals(13_000, profile.rankingMultiplier(InsightType.SPENDING_TREND))
        assertEquals(listOf(InsightType.SPENDING_TREND), profile.preferredTypes)
    }

    @Test
    fun `feedback changes ranking without changing deterministic evidence`() {
        val category = insight("category", InsightType.CATEGORY_CONCENTRATION)
        val trend = insight("trend", InsightType.SPENDING_TREND)
        val preferences = InsightPreferences(
            adoptedDeduplicationKeys = setOf("trend-feedback"),
            ignoredDeduplicationKeys = setOf("category-feedback"),
            feedbackTypeByKey = mapOf(
                "trend-feedback" to InsightType.SPENDING_TREND,
                "category-feedback" to InsightType.CATEGORY_CONCENTRATION,
            ),
        )

        val ranked = InsightRanker.rank(listOf(category, trend), preferences)

        assertEquals(listOf("trend", "category"), ranked.map { it.id })
        assertEquals(category.evidence, ranked.single { it.id == "category" }.evidence)
    }

    @Test
    fun `model context contains buckets and evidence codes but no raw transaction fields`() {
        val profile = PersonalInsightProfileBuilder.build(
            transactions = listOf(transaction("tx", LocalDate(2025, 7, 1))),
            asOf = LocalDate(2025, 7, 20),
        )
        val request = PersonalInsightModelContextFactory.create(profile, listOf(insight("trend", InsightType.SPENDING_TREND)))
        val context = request.context

        assertEquals("1-9", context.transactionCountBucket)
        assertEquals("candidate-1", context.candidates.single().candidateKey)
        assertEquals(listOf("metric.share"), context.candidates.single().evidenceCodes)
        assertEquals("40-44-percent", context.candidates.single().evidenceBuckets.getValue("metric.share"))
        assertTrue(context.toString().contains("metric.share"))
        assertTrue(!context.toString().contains("merchant"))
        assertTrue(!context.toString().contains("note"))
    }

    @Test
    fun `orchestrator fails closed when provider cites evidence outside candidate`() = runLearningSuspend {
        val profile = PersonalInsightProfileBuilder.build(emptyList(), LocalDate(2025, 7, 20))
        val request = PersonalInsightModelContextFactory.create(profile, listOf(insight("trend", InsightType.SPENDING_TREND)))
        val provider = RecordingModelProvider(
            PersonalInsightModelAnswer(
                candidateKey = "candidate-1",
                headline = "个性化分析",
                summary = "只允许使用已经核验的证据。",
                evidenceCodes = listOf("invented.fact"),
                actionLabel = "查看依据",
            ),
        )

        assertIs<PersonalInsightGenerationResult.LocalFallback>(
            PersonalInsightModelOrchestrator(provider).generate(
                consent = InsightExplanationConsent(enabled = true, consentedAtEpochMillis = 1),
                request = request,
            ),
        )
        assertEquals(1, provider.calls)
    }

    @Test
    fun `orchestrator resolves opaque candidate locally after validating model answer`() = runLearningSuspend {
        val profile = PersonalInsightProfileBuilder.build(emptyList(), LocalDate(2025, 7, 20))
        val request = PersonalInsightModelContextFactory.create(
            profile,
            listOf(insight("local-sensitive-key", InsightType.SPENDING_TREND)),
        )
        val provider = RecordingModelProvider(
            PersonalInsightModelAnswer(
                candidateKey = "candidate-1",
                headline = "为你整理的重点",
                summary = "这段表达只能基于本机白名单中的证据区间。",
                evidenceCodes = listOf("metric.share"),
                actionLabel = "查看本机依据",
            ),
        )

        val result = assertIs<PersonalInsightGenerationResult.Generated>(
            PersonalInsightModelOrchestrator(provider).generate(
                consent = InsightExplanationConsent(enabled = true, consentedAtEpochMillis = 1),
                request = request,
            ),
        )

        assertEquals("local-sensitive-key", result.localDeduplicationKey)
        assertTrue(!provider.lastContext.toString().contains("local-sensitive-key"))
    }

    @Test
    fun `model context privacy buckets cover aggregate ranges without exact values`() {
        val profiles = listOf(
            profile(active = 0, days = 0, feedback = 0),
            profile(active = 5, days = 5, feedback = 1),
            profile(active = 20, days = 30, feedback = 4),
            profile(active = 80, days = 100, feedback = 12),
            profile(active = 200, days = 300, feedback = 25),
        )
        val insights = listOf(
            insightWithEvidence(
                id = "amounts",
                impactMinor = null,
                EvidenceValue.Amount(Money(1, CurrencyCode.CNY)),
                EvidenceValue.Amount(Money(10_000, CurrencyCode.CNY)),
                EvidenceValue.Amount(Money(100_000, CurrencyCode.CNY)),
                EvidenceValue.Amount(Money(500_000, CurrencyCode.CNY)),
            ),
            insightWithEvidence(
                id = "counts",
                impactMinor = 1,
                EvidenceValue.Count(0),
                EvidenceValue.Count(1),
                EvidenceValue.Count(5),
                EvidenceValue.Count(20),
                EvidenceValue.Count(100),
            ),
            insightWithEvidence(
                id = "days",
                impactMinor = 10_000,
                EvidenceValue.Days(0),
                EvidenceValue.Days(1),
                EvidenceValue.Days(7),
                EvidenceValue.Days(30),
                EvidenceValue.Days(90),
            ),
            insightWithEvidence(
                id = "basis",
                impactMinor = 100_000,
                EvidenceValue.BasisPoints(0),
                EvidenceValue.BasisPoints(10_000),
                EvidenceValue.Text("local-only-value"),
            ),
            insightWithEvidence(
                id = "large-impact",
                impactMinor = 500_000,
                EvidenceValue.Count(2),
            ),
        )

        val contexts = profiles.map { PersonalInsightModelContextFactory.create(it, insights).context }

        assertEquals(listOf("0", "1-9", "10-39", "40-119", "120+"), contexts.map { it.transactionCountBucket })
        assertEquals(listOf("0", "1-13", "14-59", "60-179", "180+"), contexts.map { it.historyDaysBucket })
        assertEquals(listOf("0", "1", "2-7", "8-19", "20+"), contexts.map { it.feedbackCountBucket })
        assertEquals(listOf("none", "low", "medium", "high", "very-high"), contexts.first().candidates.map { it.impactBand })
        val allBuckets = contexts.first().candidates.flatMap { it.evidenceBuckets.values }
        assertTrue("present" in allBuckets)
        assertTrue("100+" in allBuckets)
        assertTrue("90+-days" in allBuckets)
        assertTrue("100-100-percent" in allBuckets)
        assertTrue(allBuckets.none { it.contains("local-only-value") })
    }

    @Test
    fun `orchestrator blocks disabled missing unreviewed and failed providers`() = runLearningSuspend {
        val request = PersonalInsightModelContextFactory.create(
            profile(active = 1, days = 1, feedback = 0),
            listOf(insight("candidate", InsightType.SPENDING_TREND)),
        )
        assertEquals(
            "model-consent-disabled",
            assertIs<PersonalInsightGenerationResult.LocalFallback>(
                PersonalInsightModelOrchestrator().generate(InsightExplanationConsent(), request),
            ).reason,
        )
        assertEquals(
            "model-provider-unavailable",
            assertIs<PersonalInsightGenerationResult.LocalFallback>(
                PersonalInsightModelOrchestrator().generate(
                    InsightExplanationConsent(true, consentedAtEpochMillis = 1),
                    request,
                ),
            ).reason,
        )
        val unreviewed = object : PersonalInsightModelProvider {
            override val providerId = "unreviewed"
            override val privacyReviewed = false
            override suspend fun generate(context: PersonalInsightModelContext): PersonalInsightModelAnswer =
                error("must not be called")
        }
        assertEquals(
            "model-provider-not-privacy-reviewed",
            assertIs<PersonalInsightGenerationResult.LocalFallback>(
                PersonalInsightModelOrchestrator(unreviewed).generate(
                    InsightExplanationConsent(true, consentedAtEpochMillis = 1),
                    request,
                ),
            ).reason,
        )
        val failing = object : PersonalInsightModelProvider {
            override val providerId = "failing"
            override val privacyReviewed = true
            override suspend fun generate(context: PersonalInsightModelContext): PersonalInsightModelAnswer =
                error("simulated provider failure")
        }
        assertEquals(
            "model-provider-failed",
            assertIs<PersonalInsightGenerationResult.LocalFallback>(
                PersonalInsightModelOrchestrator(failing).generate(
                    InsightExplanationConsent(true, consentedAtEpochMillis = 1),
                    request,
                ),
            ).reason,
        )
    }

    @Test
    fun `orchestrator blocks bidi spoofing and sanitizes unsafe provider labels`() = runLearningSuspend {
        val request = PersonalInsightModelContextFactory.create(
            profile(active = 1, days = 1, feedback = 0),
            listOf(insight("candidate", InsightType.SPENDING_TREND)),
        )
        val spoofedAnswer = PersonalInsightModelAnswer(
            candidateKey = "candidate-1",
            headline = "安全标题\u202Egpj.exe",
            summary = "本机白名单摘要",
            evidenceCodes = listOf("metric.share"),
            actionLabel = "查看依据",
        )
        val spoofedProvider = RecordingModelProvider(spoofedAnswer)
        assertEquals(
            "model-returned-unsafe-display-characters",
            assertIs<PersonalInsightGenerationResult.LocalFallback>(
                PersonalInsightModelOrchestrator(spoofedProvider).generate(
                    InsightExplanationConsent(true, consentedAtEpochMillis = 1),
                    request,
                ),
            ).reason,
        )

        val safeAnswer = spoofedAnswer.copy(headline = "安全标题")
        val unsafeLabelProvider = RecordingModelProvider(safeAnswer, providerId = "provider\u202Eevil")
        val generated = assertIs<PersonalInsightGenerationResult.Generated>(
            PersonalInsightModelOrchestrator(unsafeLabelProvider).generate(
                InsightExplanationConsent(true, consentedAtEpochMillis = 1),
                request,
            ),
        )
        assertTrue(generated.providerDisclosure.startsWith("由 已配置模型 "))
        assertTrue(!generated.providerDisclosure.contains("evil"))
    }

    private fun transaction(id: String, bookedOn: LocalDate) = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.EXPENSE,
        amount = Money(1_000, CurrencyCode("CNY")),
        bookedOn = bookedOn,
        categoryId = CategoryId("food"),
    )

    private fun insight(id: String, type: InsightType) = Insight(
        id = id,
        deduplicationKey = id,
        type = type,
        title = "Title",
        summary = "Summary",
        evidence = listOf(InsightEvidence("metric.share", EvidenceValue.BasisPoints(4_000))),
        estimatedImpact = Money(10_000, CurrencyCode("CNY")),
        impact = RuleScore(6_000),
        confidence = RuleScore(8_000),
        actionability = RuleScore(7_000),
        action = InsightAction("review", "Review"),
    )

    private fun insightWithEvidence(
        id: String,
        impactMinor: Long?,
        vararg values: EvidenceValue,
    ): Insight = insight(id, InsightType.SPENDING_TREND).copy(
        evidence = values.mapIndexed { index, value -> InsightEvidence("metric.$id.$index", value) },
        estimatedImpact = impactMinor?.let { Money(it, CurrencyCode.CNY) },
    )

    private fun profile(active: Int, days: Int, feedback: Int) = PersonalInsightProfile(
        stage = InsightLearningStage.LEARNING,
        activeTransactionCount = active,
        observedDays = days,
        feedbackCount = feedback,
        confidenceBasisPoints = 5_000,
        affinities = emptyMap(),
    )

    private class RecordingModelProvider(
        private val answer: PersonalInsightModelAnswer,
        override val providerId: String = "reviewed",
    ) : PersonalInsightModelProvider {
        override val privacyReviewed: Boolean = true
        var calls: Int = 0
        lateinit var lastContext: PersonalInsightModelContext

        override suspend fun generate(context: PersonalInsightModelContext): PersonalInsightModelAnswer {
            calls += 1
            lastContext = context
            return answer
        }
    }
}

private fun runLearningSuspend(block: suspend () -> Unit) {
    var completion: Result<Unit>? = null
    block.startCoroutine(
        object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                completion = result
            }
        },
    )
    checkNotNull(completion).getOrThrow()
}
