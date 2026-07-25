package com.hengji.app.application

import com.hengji.domain.AssetId
import com.hengji.domain.Confidence
import com.hengji.domain.CurrencyCode
import com.hengji.domain.ItemCondition
import com.hengji.domain.MarketQuote
import com.hengji.domain.Money
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.QuoteProviderId
import kotlinx.datetime.LocalDate

private const val MAX_MANUAL_QUOTE_SPECIFICATION_LENGTH = 120
private const val MANUAL_QUOTE_CONFIDENCE_BASIS_POINTS = 5_000

/**
 * Converts a reviewed, local-only form into the domain quote contract.
 *
 * Manual quotes can influence local estimates, but can never claim an external URL or live data.
 */
object ManualMarketQuoteFactory {
    fun create(
        id: String,
        assetId: AssetId,
        specification: String,
        condition: ItemCondition,
        priceMinor: Long,
        shippingMinor: Long,
        collectedOn: LocalDate,
        asOf: LocalDate,
        currency: CurrencyCode,
    ): MarketQuote {
        val normalizedSpecification = specification.trim()
        require(normalizedSpecification.isNotEmpty()) { "Manual quote specification cannot be blank" }
        require(normalizedSpecification.length <= MAX_MANUAL_QUOTE_SPECIFICATION_LENGTH) {
            "Manual quote specification is too long"
        }
        require(priceMinor > 0) { "Manual quote price must be positive" }
        require(shippingMinor >= 0) { "Manual quote shipping cannot be negative" }
        require(collectedOn <= asOf) { "Manual quote date cannot be in the future" }
        return MarketQuote(
            id = id,
            assetId = assetId,
            providerId = QuoteProviderId("manual-local"),
            provenance = QuoteProvenance.MANUAL,
            specification = normalizedSpecification,
            condition = condition,
            price = Money(priceMinor, currency),
            shipping = Money(shippingMinor, currency),
            collectedOn = collectedOn,
            sourceUrl = null,
            confidence = Confidence(MANUAL_QUOTE_CONFIDENCE_BASIS_POINTS),
            isLive = false,
        )
    }
}
