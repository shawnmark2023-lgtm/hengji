package com.hengji.app.application

import com.hengji.domain.Asset
import com.hengji.domain.MarketEstimate
import com.hengji.domain.Money
import kotlinx.datetime.LocalDate

enum class SaleTargetStatus {
    NOT_SET,
    WAITING,
    REACHED,
    INSUFFICIENT_SAMPLE,
    STALE_QUOTES,
    DEMO_ONLY,
}

data class SaleTargetProjection(
    val status: SaleTargetStatus,
    val targetPriceMinor: Long?,
    val observedMedianMinor: Long? = null,
    val newestAcceptedQuoteOn: LocalDate? = null,
    val rejectedStaleQuoteCount: Int = 0,
)

/**
 * Projects an application-only target state from reviewed, non-demo quotes.
 *
 * Demo quotes can still support a visibly labelled estimate range, but never satisfy a sale target.
 */
object AssetSaleTargetProjector {
    fun project(
        asset: Asset,
        actionableEstimate: MarketEstimate?,
        hasNonDemoQuotes: Boolean,
        hasDemoQuotes: Boolean,
    ): SaleTargetProjection {
        val target = asset.saleTargetPrice ?: return SaleTargetProjection(
            status = SaleTargetStatus.NOT_SET,
            targetPriceMinor = null,
        )
        require(actionableEstimate == null || actionableEstimate.currency == target.currency) {
            "Sale target projection must use the asset purchase currency"
        }

        if (!hasNonDemoQuotes) {
            return SaleTargetProjection(
                status = if (hasDemoQuotes) SaleTargetStatus.DEMO_ONLY else SaleTargetStatus.INSUFFICIENT_SAMPLE,
                targetPriceMinor = target.minorUnits,
            )
        }
        if (actionableEstimate == null) {
            return SaleTargetProjection(
                status = SaleTargetStatus.STALE_QUOTES,
                targetPriceMinor = target.minorUnits,
            )
        }
        val median = actionableEstimate.median ?: return SaleTargetProjection(
            status = SaleTargetStatus.INSUFFICIENT_SAMPLE,
            targetPriceMinor = target.minorUnits,
            newestAcceptedQuoteOn = actionableEstimate.newestAcceptedQuoteOn,
            rejectedStaleQuoteCount = actionableEstimate.rejectedStaleQuoteCount,
        )
        return SaleTargetProjection(
            status = if (median.minorUnits >= target.minorUnits) {
                SaleTargetStatus.REACHED
            } else {
                SaleTargetStatus.WAITING
            },
            targetPriceMinor = target.minorUnits,
            observedMedianMinor = median.minorUnits,
            newestAcceptedQuoteOn = actionableEstimate.newestAcceptedQuoteOn,
            rejectedStaleQuoteCount = actionableEstimate.rejectedStaleQuoteCount,
        )
    }
}

object AssetSaleTargetEditor {
    fun set(asset: Asset, targetPriceMinor: Long): Asset {
        require(targetPriceMinor > 0) { "Sale target price must be positive" }
        return asset.copy(
            saleTargetPrice = Money(targetPriceMinor, asset.purchasePrice.currency),
        )
    }

    fun clear(asset: Asset): Asset = asset.copy(saleTargetPrice = null)
}
