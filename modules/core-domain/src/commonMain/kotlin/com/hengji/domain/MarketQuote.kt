package com.hengji.domain

import kotlinx.datetime.LocalDate

data class QuoteProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Quote provider id cannot be blank" }
    }
}

enum class QuoteProvenance {
    MANUAL,
    DEMO,
    OFFICIAL_API,
    LICENSED_AGGREGATOR,
}

enum class ItemCondition {
    NEW,
    LIKE_NEW,
    GOOD,
    FAIR,
    POOR,
}

data class Confidence(val basisPoints: Int) {
    init {
        require(basisPoints in 0..10_000) { "Confidence must be between 0 and 10,000 basis points" }
    }

    companion object {
        val NONE = Confidence(0)
        val FULL = Confidence(10_000)
    }
}

data class MarketQuote(
    val id: String,
    val assetId: AssetId,
    val providerId: QuoteProviderId,
    val provenance: QuoteProvenance,
    val specification: String,
    val condition: ItemCondition,
    val price: Money,
    val shipping: Money = Money.zero(price.currency),
    val collectedOn: LocalDate,
    val sourceUrl: String? = null,
    val confidence: Confidence,
    val isLive: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Market quote id cannot be blank" }
        require(specification.isNotBlank()) { "Quote specification cannot be blank" }
        price.requireNonNegative("Quote price")
        shipping.requireNonNegative("Quote shipping")
        require(shipping.currency == price.currency) { "Quote shipping must use quote currency" }
        require(sourceUrl == null || sourceUrl.isNotBlank()) { "Source URL cannot be blank" }
        require(provenance != QuoteProvenance.DEMO || sourceUrl == null) {
            "Demo quotes cannot claim an external source URL"
        }
        require(!isLive || provenance == QuoteProvenance.OFFICIAL_API || provenance == QuoteProvenance.LICENSED_AGGREGATOR) {
            "Only approved official or licensed providers can mark a quote live"
        }
        require(!isLive || sourceUrl != null) { "A live quote must retain an auditable source URL" }
    }

    val landedPrice: Money
        get() = price + shipping

    val isLiveSource: Boolean
        get() = isLive
}

data class MarketEstimate(
    val assetId: AssetId,
    val asOf: LocalDate,
    val currency: CurrencyCode,
    val minimum: Money,
    val firstQuartile: Money,
    /** Robust median retained for interval math and audits. */
    val statisticalMedian: Money,
    /** User-presentable single point; absent when sample size or confidence is too low. */
    val median: Money?,
    val thirdQuartile: Money,
    val maximum: Money,
    val acceptedQuoteCount: Int,
    val rejectedOutlierCount: Int,
    val confidence: Confidence,
    val includesDemoData: Boolean,
    val includesManualData: Boolean,
    val includesLiveData: Boolean,
    val isEntirelyLiveData: Boolean,
    val newestAcceptedQuoteOn: LocalDate,
    val rejectedStaleQuoteCount: Int,
)

data class MarketEstimatePolicy(
    val maximumQuoteAgeDays: Int = DEFAULT_MAXIMUM_QUOTE_AGE_DAYS,
) {
    init {
        require(maximumQuoteAgeDays >= 0) { "Maximum quote age cannot be negative" }
    }

    companion object {
        const val DEFAULT_MAXIMUM_QUOTE_AGE_DAYS: Int = 90
    }
}

object MarketQuoteEstimator {
    /**
     * Uses an IQR fence when at least five samples exist, then reports robust quartiles and a
     * conservative confidence. Four-point samples are too sparse for the discrete percentile
     * method: applying a fence there can incorrectly discard both valid endpoints.
     */
    fun estimate(
        assetId: AssetId,
        quotes: Iterable<MarketQuote>,
        asOf: LocalDate,
        policy: MarketEstimatePolicy = MarketEstimatePolicy(),
    ): MarketEstimate? {
        val historicalCandidates = quotes.filter { it.assetId == assetId && it.collectedOn <= asOf }
        val candidates = historicalCandidates.filter {
            asOf.toEpochDays() - it.collectedOn.toEpochDays() <= policy.maximumQuoteAgeDays
        }
        if (candidates.isEmpty()) return null
        val currency = candidates.first().price.currency
        require(candidates.all { it.price.currency == currency }) { "Market estimate cannot mix currencies" }

        val sorted = candidates.sortedBy { it.landedPrice.minorUnits }
        val initialValues = sorted.map { it.landedPrice.minorUnits }
        val accepted = if (sorted.size < 5) {
            sorted
        } else {
            val q1 = percentile(initialValues, 25)
            val q3 = percentile(initialValues, 75)
            val iqr = q3 - q1
            val fenceOffset = saturatingMultiplyAndDivide(iqr, 3, 2)
            val lowerFence = (q1 - fenceOffset).coerceAtLeast(0)
            val upperFence = saturatingAdd(q3, fenceOffset)
            sorted.filter { it.landedPrice.minorUnits in lowerFence..upperFence }
        }
        if (accepted.isEmpty()) return null

        val values = accepted.map { it.landedPrice.minorUnits }
        val averageSourceConfidence = accepted.sumOf { it.confidence.basisPoints.toLong() } / accepted.size
        val sampleConfidence = (accepted.size * 1_500).coerceAtMost(10_000)
        val confidence = Confidence(minOf(averageSourceConfidence.toInt(), sampleConfidence))
        fun money(units: Long) = Money(units, currency)

        return MarketEstimate(
            assetId = assetId,
            asOf = asOf,
            currency = currency,
            minimum = money(values.first()),
            firstQuartile = money(percentile(values, 25)),
            statisticalMedian = money(percentile(values, 50)),
            median = if (accepted.size >= 3 && confidence.basisPoints >= 4_000) {
                money(percentile(values, 50))
            } else {
                null
            },
            thirdQuartile = money(percentile(values, 75)),
            maximum = money(values.last()),
            acceptedQuoteCount = accepted.size,
            rejectedOutlierCount = candidates.size - accepted.size,
            confidence = confidence,
            includesDemoData = accepted.any { it.provenance == QuoteProvenance.DEMO },
            includesManualData = accepted.any { it.provenance == QuoteProvenance.MANUAL },
            includesLiveData = accepted.any { it.isLiveSource },
            isEntirelyLiveData = accepted.all { it.isLiveSource },
            newestAcceptedQuoteOn = accepted.maxOf { it.collectedOn },
            rejectedStaleQuoteCount = historicalCandidates.size - candidates.size,
        )
    }

    private fun percentile(sorted: List<Long>, percent: Int): Long {
        require(sorted.isNotEmpty())
        if (sorted.size == 1) return sorted.first()
        val index = ExactMath.multiplyDivideRounded(
            value = (sorted.size - 1).toLong(),
            multiplier = percent.toLong(),
            divisor = 100,
            rounding = MoneyRounding.HALF_AWAY_FROM_ZERO,
        ).toInt()
        return sorted[index]
    }

    private fun saturatingMultiplyAndDivide(value: Long, multiplier: Long, divisor: Long): Long =
        try {
            ExactMath.multiplyDivideRounded(value, multiplier, divisor, MoneyRounding.TOWARD_ZERO)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }

    private fun saturatingAdd(left: Long, right: Long): Long =
        try {
            ExactMath.add(left, right)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
}
