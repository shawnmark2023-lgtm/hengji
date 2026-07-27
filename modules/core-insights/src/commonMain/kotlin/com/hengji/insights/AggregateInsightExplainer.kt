package com.hengji.insights

data class InsightExplanationConsent(
    val enabled: Boolean = false,
    val consentedAtEpochMillis: Long? = null,
    val revokedAtEpochMillis: Long? = null,
) {
    init {
        require(consentedAtEpochMillis == null || consentedAtEpochMillis >= 0)
        require(revokedAtEpochMillis == null || revokedAtEpochMillis >= 0)
        require(enabled == (consentedAtEpochMillis != null && revokedAtEpochMillis == null))
    }
}

/**
 * Explicit whitelist for optional model explanations. Raw transactions, merchant names, notes,
 * account identifiers and import payloads have no field in this contract.
 */
data class InsightAggregatePrompt(
    val insightType: String,
    val periodDays: Int,
    val transactionCountBucket: String,
    val amountBand: String,
    val percentageBasisPoints: Int?,
    val confidenceBasisPoints: Int,
    val deterministicEvidence: List<String>,
) {
    init {
        require(insightType.matches(Regex("[a-z0-9_-]{1,48}")))
        require(periodDays in 1..366)
        require(transactionCountBucket in setOf("0", "1-4", "5-19", "20-99", "100+"))
        require(amountBand in setOf("none", "low", "medium", "high", "very-high"))
        require(percentageBasisPoints == null || percentageBasisPoints in 0..10_000)
        require(confidenceBasisPoints in 0..10_000)
        require(deterministicEvidence.size in 1..8)
        require(deterministicEvidence.all { it.length in 1..160 })
    }
}

sealed interface OptionalExplanationResult {
    data class Explained(val text: String, val providerDisclosure: String) : OptionalExplanationResult
    data class Offline(val deterministicText: String, val reason: String) : OptionalExplanationResult
}

interface AggregateExplanationProvider {
    val providerId: String
    val privacyReviewed: Boolean
    suspend fun explain(prompt: InsightAggregatePrompt): String
}

class OptionalAggregateInsightExplainer(
    private val provider: AggregateExplanationProvider? = null,
) {
    suspend fun explain(
        consent: InsightExplanationConsent,
        prompt: InsightAggregatePrompt,
        deterministicText: String,
    ): OptionalExplanationResult {
        if (!consent.enabled) {
            return OptionalExplanationResult.Offline(deterministicText, "模型解释默认关闭或同意已撤回")
        }
        val activeProvider = provider
            ?: return OptionalExplanationResult.Offline(deterministicText, "未配置经过隐私评审的模型提供方")
        if (!activeProvider.privacyReviewed) {
            return OptionalExplanationResult.Offline(deterministicText, "模型提供方未通过隐私评审")
        }
        val explanation = activeProvider.explain(prompt).trim()
        if (explanation.isBlank() || explanation.length > 1_000) {
            return OptionalExplanationResult.Offline(deterministicText, "模型解释为空或超过本地显示上限")
        }
        return OptionalExplanationResult.Explained(
            text = explanation,
            providerDisclosure = "由 ${activeProvider.providerId} 基于脱敏聚合生成；规则结论仍以本机计算为准。",
        )
    }
}
