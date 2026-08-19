package com.hengji.app.model

import com.hengji.app.application.SaleTargetProjection
import com.hengji.app.application.SaleTargetStatus
import com.hengji.insights.InsightFeedback
import com.hengji.insights.InsightLearningStage
import com.hengji.insights.PersonalInsightGenerationResult
import com.hengji.insights.PersonalInsightModelAnswer
import com.hengji.insights.PreparedPersonalInsightModelRequest
import com.hengji.insights.InsightType
import com.hengji.data.PersonalAnalysisRecord
import kotlinx.datetime.LocalDate

enum class EntryKind {
    Expense,
    Income,
    Refund,
}

data class DemoTransaction(
    val id: String,
    val merchant: String,
    val category: String,
    val amountMinor: Long,
    val bookedOn: LocalDate,
    val dateLabel: String,
    val sourceLabel: String,
    val kind: EntryKind = EntryKind.Expense,
    val inCurrentPeriod: Boolean = true,
)

data class DemoAsset(
    val id: String,
    val name: String,
    val variant: String,
    val ownedDays: Int,
    val usageCount: Int,
    val totalCostMinor: Long,
    val currentValueMinor: Long,
    val marketLowMinor: Long,
    val marketHighMinor: Long,
    val marketMedianMinor: Long? = null,
    val quoteCount: Int = 0,
    val marketConfidence: Int,
    val quoteUpdatedLabel: String,
    val currencyCode: String = "CNY",
    val saleTarget: SaleTargetProjection = SaleTargetProjection(
        status = SaleTargetStatus.NOT_SET,
        targetPriceMinor = null,
    ),
    val dailyCostMinor: Long = totalCostMinor / ownedDays.coerceAtLeast(1),
    val netDailyCostMinor: Long = (totalCostMinor - currentValueMinor) / ownedDays.coerceAtLeast(1),
    val costPerUseMinor: Long = (totalCostMinor - currentValueMinor) / usageCount.coerceAtLeast(1),
) {
    init {
        require(ownedDays >= 0)
        require(usageCount >= 0)
        require(quoteCount >= 0)
        require(marketConfidence in 0..100)
    }
}

enum class InsightPriority {
    High,
    Medium,
    Low,
}

data class DemoInsight(
    val deduplicationKey: String,
    val type: InsightType,
    val title: String,
    val summary: String,
    val evidence: String,
    val action: String,
    val impactMinor: Long,
    val confidence: Int,
    val priority: InsightPriority,
    val feedback: InsightFeedback = InsightFeedback.NEW,
    val personalizationReason: String? = null,
    val modelDisclosure: String? = null,
)

data class PersonalInsightFeed(
    val items: List<DemoInsight>,
    val learningStage: InsightLearningStage,
    val learningPercent: Int,
    val observedTransactionCount: Int,
    val observedDays: Int,
    val feedbackCount: Int,
    val observedExpenseMonthCount: Int = 0,
    val firstAnalysisEligible: Boolean = false,
    val daysUntilFirstAnalysis: Int = 90,
) {
    init {
        require(learningPercent in 0..100)
        require(observedTransactionCount >= 0 && observedDays >= 0 && feedbackCount >= 0)
        require(observedExpenseMonthCount >= 0 && daysUntilFirstAnalysis >= 0)
    }
}

data class PersonalInsightComputation(
    val feed: PersonalInsightFeed,
    val modelRequest: PreparedPersonalInsightModelRequest?,
)

fun PersonalInsightFeed.withModelResult(
    result: PersonalInsightGenerationResult.Generated?,
): PersonalInsightFeed {
    if (result == null) return this
    return copy(
        items = items.map { insight ->
            if (insight.deduplicationKey != result.localDeduplicationKey) {
                insight
            } else {
                insight.copy(
                    title = result.answer.headline.trim(),
                    summary = result.answer.summary.trim(),
                    action = result.answer.actionLabel.trim(),
                    modelDisclosure = result.providerDisclosure,
                )
            }
        },
    )
}

fun PersonalAnalysisRecord.toGeneratedModelResult(): PersonalInsightGenerationResult.Generated =
    PersonalInsightGenerationResult.Generated(
        localDeduplicationKey = localDeduplicationKey,
        answer = PersonalInsightModelAnswer(
            candidateKey = "saved-local-analysis",
            headline = headline,
            summary = summary,
            evidenceCodes = evidenceCodes,
            actionLabel = actionLabel,
        ),
        providerDisclosure = "由恒迹内置本机模型生成；数据未离开设备，金额与依据以账本计算为准。",
    )

fun PersonalInsightGenerationResult.Generated.toPersonalAnalysisRecord(
    createdAtEpochMillis: Long,
): PersonalAnalysisRecord = PersonalAnalysisRecord(
    createdAtEpochMillis = createdAtEpochMillis,
    localDeduplicationKey = localDeduplicationKey,
    headline = answer.headline.trim(),
    summary = answer.summary.trim(),
    actionLabel = answer.actionLabel.trim(),
    evidenceCodes = answer.evidenceCodes,
)

private val demoAsOf = LocalDate(2026, 7, 15)
val sampleTransactions: List<DemoTransaction> = DomainDemoData.transactions(DomainDemoData.initialSnapshot, demoAsOf)
val sampleAssets: List<DemoAsset> = DomainDemoData.assets(DomainDemoData.initialSnapshot, demoAsOf)
val sampleInsights: List<DemoInsight> = DomainDemoData.insights(
    snapshot = DomainDemoData.initialSnapshot,
    asOf = demoAsOf,
    nowEpochMillis = 0,
)

fun formatMoney(
    minorUnits: Long,
    showSign: Boolean = false,
    currencyCode: String = "CNY",
): String {
    val negative = minorUnits < 0
    val absolute = if (negative) -minorUnits else minorUnits
    val whole = (absolute / 100).toString()
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    val cents = (absolute % 100).toString().padStart(2, '0')
    val prefix = when {
        negative -> "−"
        showSign && minorUnits > 0 -> "+"
        else -> ""
    }
    return "$prefix${currencyDisplayPrefix(currencyCode)}$grouped.$cents"
}

fun currencyDisplayPrefix(currencyCode: String): String = when (currencyCode) {
    "CNY" -> "¥"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "¥"
    else -> "$currencyCode "
}

fun parseMoneyToMinor(input: String): Long? {
    val normalized = input.trim().replace(",", "").removePrefix("¥")
    if (!Regex("^-?\\d+(\\.\\d{0,2})?$").matches(normalized)) return null
    val negative = normalized.startsWith('-')
    val unsigned = normalized.removePrefix("-")
    val parts = unsigned.split('.', limit = 2)
    val whole = parts[0].toLongOrNull() ?: return null
    val cents = parts.getOrElse(1) { "" }.padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    if (whole > (Long.MAX_VALUE - cents) / 100) return null
    val minor = whole * 100 + cents
    return if (negative) -minor else minor
}
