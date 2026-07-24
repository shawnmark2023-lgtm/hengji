package com.hengji.app.application

import com.hengji.data.DemoLedger
import com.hengji.data.InMemoryLedgerRepository
import com.hengji.data.InsightPreferenceRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLedgerGatewayInsightPreferencesTest {
    @Test
    fun previewGatewayPersistsPreferencesBumpsRevisionAndCanReset() = runTest {
        val repository = InMemoryLedgerRepository(DemoLedger.snapshot())
        val gateway = PreviewLedgerGateway(repository)
        val before = gateway.snapshot()
        val adopted = InsightPreferenceRecord(
            adoptedDeduplicationKeys = setOf("asset:headphones:usage"),
            updatedAtEpochMillis = 100,
        )

        gateway.saveInsightPreferences(adopted)
        val saved = gateway.snapshot()

        assertTrue(saved.revision > before.revision)
        assertEquals(adopted, saved.insightPreferences)

        val reset = InsightFeedbackReducer.reset(nowEpochMillis = 200)
        gateway.saveInsightPreferences(reset)
        val restored = gateway.snapshot()

        assertTrue(restored.revision > saved.revision)
        assertEquals(reset, restored.insightPreferences)
    }
}
