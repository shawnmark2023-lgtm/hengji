package com.hengji.app.application

import com.hengji.data.InsightPreferenceRecord
import com.hengji.insights.InsightFeedback
import com.hengji.insights.InsightType

internal object InsightFeedbackReducer {
    const val SNOOZE_DURATION_MILLIS: Long = 7L * 24 * 60 * 60 * 1_000

    fun apply(
        current: InsightPreferenceRecord,
        deduplicationKey: String,
        insightType: InsightType,
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
            feedbackTypeByKey = current.feedbackTypeByKey - deduplicationKey,
            updatedAtEpochMillis = nowEpochMillis,
        )
        return when (feedback) {
            InsightFeedback.ADOPTED -> cleared.copy(
                adoptedDeduplicationKeys = cleared.adoptedDeduplicationKeys + deduplicationKey,
                feedbackTypeByKey = cleared.feedbackTypeByKey + (deduplicationKey to insightType.name),
            )
            InsightFeedback.SNOOZED -> {
                require(nowEpochMillis <= Long.MAX_VALUE - SNOOZE_DURATION_MILLIS) {
                    "Snooze deadline overflow"
                }
                cleared.copy(
                    snoozedUntilEpochMillisByKey = cleared.snoozedUntilEpochMillisByKey +
                        (deduplicationKey to nowEpochMillis + SNOOZE_DURATION_MILLIS),
                    feedbackTypeByKey = cleared.feedbackTypeByKey + (deduplicationKey to insightType.name),
                )
            }
            InsightFeedback.IGNORED -> cleared.copy(
                ignoredDeduplicationKeys = cleared.ignoredDeduplicationKeys + deduplicationKey,
                feedbackTypeByKey = cleared.feedbackTypeByKey + (deduplicationKey to insightType.name),
            )
            InsightFeedback.NEW -> error("Handled by precondition")
        }
    }

    fun reset(nowEpochMillis: Long): InsightPreferenceRecord {
        require(nowEpochMillis >= 0) { "Feedback reset time cannot be negative" }
        return InsightPreferenceRecord(updatedAtEpochMillis = nowEpochMillis)
    }
}
