package com.hengji.app

import androidx.test.platform.app.InstrumentationRegistry
import com.hengji.insights.PersonalInsightModelCandidate
import com.hengji.insights.PersonalInsightModelContext
import com.hengji.insights.PersonalInsightModelEvidence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInPersonalInsightModelInstrumentedTest {
    @Test
    fun bundledModelInstallsAndGeneratesOfflineOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelDirectory = AndroidBuiltInModelAssets.ensureInstalled(context)
        val modelContext = PersonalInsightModelContext(
            learningStage = "learning",
            confidenceBasisPoints = 7_200,
            transactionCountBucket = "40-119",
            historyDaysBucket = "60-179",
            feedbackCountBucket = "2-7",
            preferredInsightTypes = listOf("category_concentration"),
            candidates = listOf(
                PersonalInsightModelCandidate(
                    candidateKey = "candidate-1",
                    insightType = "category_concentration",
                    evidenceCodes = listOf("category_share_basis_points"),
                    evidenceBuckets = mapOf("category_share_basis_points" to "high"),
                    confidenceBasisPoints = 8_100,
                    impactBand = "high",
                    exactEvidence = listOf(
                        PersonalInsightModelEvidence(
                            code = "category_share_basis_points",
                            kind = "basis-points",
                            numericValue = 4_600,
                        ),
                    ),
                ),
            ),
            exactExpenseCount = 96,
            exactHistoryDays = 96,
            observedExpenseMonthCount = 4,
            priorAnalysisSummaries = listOf("外卖支出较集中：先核对高频消费。"),
        )

        BuiltInPersonalInsightModelProvider { modelDirectory.absolutePath }.use { provider ->
            val answer = provider.generate(modelContext)

            assertEquals("candidate-1", answer.candidateKey)
            assertEquals(listOf("category_share_basis_points"), answer.evidenceCodes)
            assertTrue(answer.headline.any { it.code in 0x4E00..0x9FFF })
            assertFalse((answer.headline + answer.summary + answer.actionLabel).any(Char::isDigit))
        }
    }
}
