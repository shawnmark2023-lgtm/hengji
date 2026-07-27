package com.hengji.insights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class AggregateInsightExplainerTest {
    private val prompt = InsightAggregatePrompt(
        insightType = "subscription",
        periodDays = 90,
        transactionCountBucket = "5-19",
        amountBand = "medium",
        percentageBasisPoints = 1_250,
        confidenceBasisPoints = 8_400,
        deterministicEvidence = listOf("近 90 天出现 3 次稳定周期扣款"),
    )

    @Test
    fun `default and revoked consent produce zero provider calls`() = runSuspend {
        val provider = RecordingProvider()
        val explainer = OptionalAggregateInsightExplainer(provider)

        assertIs<OptionalExplanationResult.Offline>(
            explainer.explain(InsightExplanationConsent(), prompt, "离线解释"),
        )
        assertIs<OptionalExplanationResult.Offline>(
            explainer.explain(
                InsightExplanationConsent(false, consentedAtEpochMillis = 10, revokedAtEpochMillis = 20),
                prompt,
                "离线解释",
            ),
        )
        assertEquals(0, provider.calls)
    }

    @Test
    fun `reviewed provider receives only aggregate contract`() = runSuspend {
        val provider = RecordingProvider()
        val result = OptionalAggregateInsightExplainer(provider).explain(
            InsightExplanationConsent(true, consentedAtEpochMillis = 10),
            prompt,
            "离线解释",
        )

        assertIs<OptionalExplanationResult.Explained>(result)
        assertEquals(1, provider.calls)
        assertEquals(prompt, provider.lastPrompt)
    }

    private class RecordingProvider : AggregateExplanationProvider {
        override val providerId: String = "reviewed-provider"
        override val privacyReviewed: Boolean = true
        var calls = 0
        var lastPrompt: InsightAggregatePrompt? = null

        override suspend fun explain(prompt: InsightAggregatePrompt): String {
            calls += 1
            lastPrompt = prompt
            return "这是一段仅使用聚合数据的可选解释。"
        }
    }
}

private fun runSuspend(block: suspend () -> Unit) {
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
