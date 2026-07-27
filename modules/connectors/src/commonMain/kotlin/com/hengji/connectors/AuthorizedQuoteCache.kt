package com.hengji.connectors

data class CachedQuoteBatch(
    val cacheKey: String,
    val providerId: String,
    val fetchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val quotes: List<MarketQuote>,
) {
    init {
        require(cacheKey.matches(Regex("[A-Za-z0-9._:-]{1,160}")))
        require(providerId.isNotBlank())
        require(fetchedAtEpochMillis >= 0)
        require(expiresAtEpochMillis > fetchedAtEpochMillis)
        require(quotes.size <= 100)
        require(quotes.all {
            it.providerId == providerId &&
                it.provenance == QuoteProvenance.OFFICIAL_OR_CONTRACTED_API &&
                it.isLive
        }) { "Only live quotes from an official or contracted API may enter the authorized cache" }
    }
}

data class QuoteCacheDeletionAudit(
    val cacheKey: String,
    val deletedAtEpochMillis: Long,
    val removedQuoteCount: Int,
)

/**
 * Bounded, auditable cache. It does not perform network access and cannot turn manual/demo data into live data.
 */
class AuthorizedQuoteCache(
    private val maxEntries: Int = 100,
    private val maxTtlMillis: Long = 24 * 60 * 60 * 1_000L,
) {
    private val batches = LinkedHashMap<String, CachedQuoteBatch>()

    init {
        require(maxEntries in 1..1_000)
        require(maxTtlMillis in 60_000L..7 * 24 * 60 * 60 * 1_000L)
    }

    fun put(
        cacheKey: String,
        provider: QuoteProvider,
        quotes: List<MarketQuote>,
        fetchedAtEpochMillis: Long,
        ttlMillis: Long,
    ) {
        require(provider.provenance == QuoteProvenance.OFFICIAL_OR_CONTRACTED_API) {
            "Demo and manual providers cannot populate the authorized cache"
        }
        require(ttlMillis in 60_000L..maxTtlMillis)
        val batch = CachedQuoteBatch(
            cacheKey = cacheKey,
            providerId = provider.providerId,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            expiresAtEpochMillis = Math.addExact(fetchedAtEpochMillis, ttlMillis),
            quotes = quotes,
        )
        batches.remove(cacheKey)
        batches[cacheKey] = batch
        while (batches.size > maxEntries) {
            batches.remove(batches.keys.first())
        }
    }

    fun getFresh(cacheKey: String, nowEpochMillis: Long): CachedQuoteBatch? {
        require(nowEpochMillis >= 0)
        val batch = batches[cacheKey] ?: return null
        if (nowEpochMillis >= batch.expiresAtEpochMillis) {
            batches.remove(cacheKey)
            return null
        }
        return batch
    }

    fun delete(cacheKey: String, deletedAtEpochMillis: Long): QuoteCacheDeletionAudit {
        require(deletedAtEpochMillis >= 0)
        val removed = batches.remove(cacheKey)
        return QuoteCacheDeletionAudit(cacheKey, deletedAtEpochMillis, removed?.quotes?.size ?: 0)
    }

    fun purgeExpired(nowEpochMillis: Long): List<QuoteCacheDeletionAudit> {
        require(nowEpochMillis >= 0)
        return batches.values
            .filter { nowEpochMillis >= it.expiresAtEpochMillis }
            .map { delete(it.cacheKey, nowEpochMillis) }
    }
}
