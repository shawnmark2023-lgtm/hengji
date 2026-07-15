package com.hengji.connectors

import kotlinx.serialization.Serializable

@Serializable
enum class ItemCondition {
    NEW,
    LIKE_NEW,
    GOOD,
    FAIR,
    POOR,
}

@Serializable
enum class QuoteProvenance {
    MANUAL,
    DEMO_NON_LIVE,
    OFFICIAL_OR_CONTRACTED_API,
}

@Serializable
data class QuoteQuery(
    val normalizedProductName: String,
    val model: String? = null,
    val condition: ItemCondition? = null,
    val currency: String = "CNY",
    val limit: Int = 20,
) {
    init {
        require(normalizedProductName.isNotBlank())
        require(currency.matches(Regex("[A-Z]{3}")))
        require(limit in 1..100)
    }
}

@Serializable
data class MarketQuote(
    val providerId: String,
    val title: String,
    val model: String?,
    val condition: ItemCondition,
    val priceMinor: Long,
    val shippingMinor: Long,
    val currency: String,
    val observedAt: String,
    val sourceUrl: String?,
    val matchConfidence: Double,
    val provenance: QuoteProvenance,
    val isLive: Boolean,
    val disclosure: String,
) {
    init {
        require(providerId.isNotBlank() && title.isNotBlank())
        require(priceMinor >= 0 && shippingMinor >= 0)
        require(priceMinor <= Long.MAX_VALUE - shippingMinor) { "Delivered price exceeds 64-bit range" }
        require(currency.matches(Regex("[A-Z]{3}")))
        require(matchConfidence in 0.0..1.0)
        if (provenance == QuoteProvenance.DEMO_NON_LIVE) {
            require(!isLive) { "Demonstration quotes can never be live" }
            require(disclosure.contains("非实时") || disclosure.contains("non-live", true))
        }
        if (provenance == QuoteProvenance.MANUAL) require(!isLive)
    }

    val deliveredPriceMinor: Long get() = priceMinor + shippingMinor
}

interface QuoteProvider {
    val providerId: String
    val provenance: QuoteProvenance
    suspend fun query(query: QuoteQuery): List<MarketQuote>
}

class ManualQuoteProvider(
    private val quotes: MutableList<MarketQuote> = mutableListOf(),
) : QuoteProvider {
    override val providerId: String = "manual"
    override val provenance: QuoteProvenance = QuoteProvenance.MANUAL

    fun add(quote: MarketQuote) {
        require(quote.provenance == QuoteProvenance.MANUAL && !quote.isLive)
        require(quote.providerId == providerId)
        quotes += quote
    }

    override suspend fun query(query: QuoteQuery): List<MarketQuote> = quotes
        .asSequence()
        .filter { it.currency == query.currency }
        .filter { it.title.contains(query.normalizedProductName, ignoreCase = true) }
        .filter { query.model == null || it.model.equals(query.model, ignoreCase = true) }
        .filter { query.condition == null || it.condition == query.condition }
        .take(query.limit)
        .toList()
}

class DemoQuoteProvider : QuoteProvider {
    override val providerId: String = "demo-non-live"
    override val provenance: QuoteProvenance = QuoteProvenance.DEMO_NON_LIVE

    override suspend fun query(query: QuoteQuery): List<MarketQuote> {
        val base = listOf(420_000L, 458_000L, 489_900L, 510_000L)
        return base.take(query.limit).mapIndexed { index, price ->
            MarketQuote(
                providerId = providerId,
                title = "${query.normalizedProductName} 演示报价 ${index + 1}",
                model = query.model,
                condition = query.condition ?: ItemCondition.GOOD,
                priceMinor = price,
                shippingMinor = if (index % 2 == 0) 1_200 else 0,
                currency = query.currency,
                observedAt = "2026-06-${(index + 10).toString().padStart(2, '0')}T10:00:00+08:00",
                sourceUrl = null,
                matchConfidence = 0.72 + index * 0.04,
                provenance = QuoteProvenance.DEMO_NON_LIVE,
                isLive = false,
                disclosure = "演示行情，非实时、非平台抓取，不用于成交决策。",
            )
        }
    }
}
