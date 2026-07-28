package com.hengji.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthorizedQuoteCacheTest {
    @Test
    fun `cache accepts only authorized live provenance and expires deterministically`() {
        val cache = AuthorizedQuoteCache()
        val provider = OfficialProvider()
        val quote = officialQuote()

        cache.put("phone:CNY:good", provider, listOf(quote), 1_000L, 60_000L)

        assertEquals(listOf(quote), cache.getFresh("phone:CNY:good", 60_999L)?.quotes)
        assertNull(cache.getFresh("phone:CNY:good", 61_000L))
    }

    @Test
    fun `demo provider cannot populate production cache`() {
        val cache = AuthorizedQuoteCache()

        assertFailsWith<IllegalArgumentException> {
            cache.put("demo:CNY", DemoQuoteProvider(), emptyList(), 0, 60_000)
        }
    }

    @Test
    fun `deletion produces auditable count`() {
        val cache = AuthorizedQuoteCache()
        cache.put("phone:CNY", OfficialProvider(), listOf(officialQuote()), 0, 60_000)

        assertEquals(1, cache.delete("phone:CNY", 1).removedQuoteCount)
        assertEquals(0, cache.delete("phone:CNY", 2).removedQuoteCount)
    }

    @Test
    fun `expiry rejects epoch overflow without a JVM-only arithmetic API`() {
        val cache = AuthorizedQuoteCache()

        assertFailsWith<IllegalArgumentException> {
            cache.put(
                cacheKey = "phone:CNY",
                provider = OfficialProvider(),
                quotes = listOf(officialQuote()),
                fetchedAtEpochMillis = Long.MAX_VALUE - 59_999L,
                ttlMillis = 60_000L,
            )
        }
    }

    private fun officialQuote() = MarketQuote(
        providerId = "contracted-market",
        title = "Phone 256G",
        model = "256G",
        condition = ItemCondition.GOOD,
        priceMinor = 420_000,
        shippingMinor = 1_200,
        currency = "CNY",
        observedAt = "2026-07-27T10:00:00+08:00",
        sourceUrl = "https://licensed.example/items/opaque-id",
        matchConfidence = 0.91,
        provenance = QuoteProvenance.OFFICIAL_OR_CONTRACTED_API,
        isLive = true,
        disclosure = "授权 API 报价，含运费。",
    )

    private class OfficialProvider : QuoteProvider {
        override val providerId: String = "contracted-market"
        override val provenance: QuoteProvenance = QuoteProvenance.OFFICIAL_OR_CONTRACTED_API
        override suspend fun query(query: QuoteQuery): List<MarketQuote> = emptyList()
    }
}
