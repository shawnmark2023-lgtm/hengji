package com.hengji.app.application

import com.hengji.data.InsightPreferenceRecord
import com.hengji.insights.InsightFeedback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsightFeedbackReducerTest {
    @Test
    fun feedbackActionsAreMutuallyExclusiveAndUpdateTimestamp() {
        val key = "merchant:coffee:concentration"
        val initial = InsightPreferenceRecord(
            mutedTypes = setOf("BUDGET_PACE"),
            ignoredDeduplicationKeys = setOf("ignored-key"),
            adoptedDeduplicationKeys = setOf("another-key"),
            snoozedUntilEpochMillisByKey = mapOf(key to 99),
            updatedAtEpochMillis = 1,
        )

        val adopted = InsightFeedbackReducer.apply(
            initial,
            key,
            com.hengji.insights.InsightType.SPENDING_TREND,
            InsightFeedback.ADOPTED,
            10,
        )
        assertTrue(key in adopted.adoptedDeduplicationKeys)
        assertFalse(key in adopted.ignoredDeduplicationKeys)
        assertFalse(key in adopted.snoozedUntilEpochMillisByKey)
        assertEquals(setOf("BUDGET_PACE"), adopted.mutedTypes)
        assertEquals(10, adopted.updatedAtEpochMillis)

        val snoozed = InsightFeedbackReducer.apply(
            adopted,
            key,
            com.hengji.insights.InsightType.SPENDING_TREND,
            InsightFeedback.SNOOZED,
            20,
        )
        assertFalse(key in snoozed.adoptedDeduplicationKeys)
        assertFalse(key in snoozed.ignoredDeduplicationKeys)
        assertEquals(
            20 + InsightFeedbackReducer.SNOOZE_DURATION_MILLIS,
            snoozed.snoozedUntilEpochMillisByKey[key],
        )
        assertEquals(20, snoozed.updatedAtEpochMillis)

        val ignored = InsightFeedbackReducer.apply(
            snoozed,
            key,
            com.hengji.insights.InsightType.SPENDING_TREND,
            InsightFeedback.IGNORED,
            30,
        )
        assertTrue(key in ignored.ignoredDeduplicationKeys)
        assertFalse(key in ignored.adoptedDeduplicationKeys)
        assertFalse(key in ignored.snoozedUntilEpochMillisByKey)
        assertEquals(30, ignored.updatedAtEpochMillis)
    }

    @Test
    fun resetClearsAllLearningPreferences() {
        val reset = InsightFeedbackReducer.reset(nowEpochMillis = 200)

        assertTrue(reset.mutedTypes.isEmpty())
        assertTrue(reset.ignoredDeduplicationKeys.isEmpty())
        assertTrue(reset.adoptedDeduplicationKeys.isEmpty())
        assertTrue(reset.snoozedUntilEpochMillisByKey.isEmpty())
        assertEquals(200, reset.updatedAtEpochMillis)
    }
}
