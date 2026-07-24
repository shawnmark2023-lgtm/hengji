package com.hengji.app.application

import com.hengji.data.InsightPreferenceRecord
import com.hengji.insights.InsightFeedback

internal object InsightFeedbackReducer {
    const val SNOOZE_DURATION_MILLIS: Long = 7L * 24 * 60 * 60 * 1_000

    fun apply(
        current: InsightPreferenceRecord,
        deduplicationKey: String,
        feedback: InsightFeedback,
        nowEpochMillis: Long,
    ): InsightPreferenceRecord {
        require(deduplicationKey.isNotBlank()) { "Insight deduplication key cannot be blank" }
        require(nowEpochMillis >= 0) { "Feedback update time cannot be negative" }
        require(feedback != InsightFeedback.NEW) { "NEW is not a persisted feedback action" }

        val cleared = current.copy(
            ignoredDeduplicationKeys = current.ignoredDeduplicationKeys - deduplicationKey,
            adoptedDeduplicationKeys = current.adoptedDeduplicationKeys - deduplicationKey,
            snoozedUntilEpochMillisByKey = current.snoozedUntilEpochMillisByKey - deduplicationKey,
            updatedAtEpochMillis = nowEpochMillis,
        )
        return when (feedback) {
            InsightFeedback.ADOPTED -> cleared.copy(
                adoptedDeduplicationKeys = cleared.adoptedDeduplicationKeys + deduplicationKey,
            )
            InsightFeedback.SNOOZED -> {
                require(nowEpochMillis <= Long.MAX_VALUE - SNOOZE_DURATION_MILLIS) {
                    "Snooze deadline overflow"
                }
                cleared.copy(
                    snoozedUntilEpochMillisByKey = cleared.snoozedUntilEpochMillisByKey +
                        (deduplicationKey to nowEpochMillis + SNOOZE_DURATION_MILLIS),
                )
            }
            InsightFeedback.IGNORED -> cleared.copy(
                ignoredDeduplicationKeys = cleared.ignoredDeduplicationKeys + deduplicationKey,
            )
            InsightFeedback.NEW -> error("Handled by precondition")
        }
    }

    fun reset(nowEpochMillis: Long): InsightPreferenceRecord {
        require(nowEpochMillis >= 0) { "Feedback reset time cannot be negative" }
        return InsightPreferenceRecord(updatedAtEpochMillis = nowEpochMillis)
    }
}
