package com.hengji.app.application

import com.hengji.domain.AssetId
import com.hengji.domain.ItemCondition
import com.hengji.domain.QuoteProvenance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class ManualMarketQuoteFactoryTest {
    @Test
    fun createsAuditableLocalOnlyQuoteWithExactMinorUnits() {
        val quote = ManualMarketQuoteFactory.create(
            id = "manual-quote-1",
            assetId = AssetId("asset-1"),
            specification = "  256GB · 良好  ",
            condition = ItemCondition.GOOD,
            priceMinor = 125_099,
            shippingMinor = 1_200,
            collectedOn = LocalDate(2026, 7, 25),
            asOf = LocalDate(2026, 7, 25),
        )

        assertEquals(125_099, quote.price.minorUnits)
        assertEquals(1_200, quote.shipping.minorUnits)
        assertEquals(126_299, quote.landedPrice.minorUnits)
        assertEquals("256GB · 良好", quote.specification)
        assertEquals(QuoteProvenance.MANUAL, quote.provenance)
        assertEquals("manual-local", quote.providerId.value)
        assertFalse(quote.isLive)
        assertNull(quote.sourceUrl)
    }

    @Test
    fun rejectsBlankOversizedOrNonPositiveManualQuoteInput() {
        val valid = {
            specification: String,
            priceMinor: Long,
            shippingMinor: Long ->
            ManualMarketQuoteFactory.create(
                id = "manual-quote-invalid",
                assetId = AssetId("asset-1"),
                specification = specification,
                condition = ItemCondition.GOOD,
                priceMinor = priceMinor,
                shippingMinor = shippingMinor,
                collectedOn = LocalDate(2026, 7, 25),
                asOf = LocalDate(2026, 7, 25),
            )
        }

        assertFailsWith<IllegalArgumentException> { valid(" ", 1, 0) }
        assertFailsWith<IllegalArgumentException> { valid("x".repeat(121), 1, 0) }
        assertFailsWith<IllegalArgumentException> { valid("reference", 0, 0) }
        assertFailsWith<IllegalArgumentException> { valid("reference", 1, -1) }
        assertFailsWith<IllegalArgumentException> {
            ManualMarketQuoteFactory.create(
                id = "manual-quote-future",
                assetId = AssetId("asset-1"),
                specification = "reference",
                condition = ItemCondition.GOOD,
                priceMinor = 1,
                shippingMinor = 0,
                collectedOn = LocalDate(2026, 7, 26),
                asOf = LocalDate(2026, 7, 25),
            )
        }
    }
}
