package com.hengji.domain

import kotlinx.datetime.LocalDate

data class AssetCostMetrics(
    val assetId: AssetId,
    val asOf: LocalDate,
    val ownedDays: Int,
    val useQuantity: Long,
    val totalOwnershipCost: Money,
    val residualValue: Money,
    val netCost: Money,
    val grossDailyOwnershipCost: Money?,
    val netDailyCost: Money?,
    val netCostPerUse: Money?,
)

object AssetCostCalculator {
    /**
     * Ownership days are elapsed full calendar days. On the purchase date this is zero, so daily metrics are absent.
     * Costs and events after [asOf] are ignored; passing data for another asset is rejected.
     */
    fun calculate(
        asset: Asset,
        maintenanceCosts: Iterable<MaintenanceCost> = emptyList(),
        usageEvents: Iterable<UsageEvent> = emptyList(),
        asOf: LocalDate,
        residualValue: Money = asset.currentEstimatedValue ?: Money.zero(asset.purchasePrice.currency),
    ): AssetCostMetrics {
        require(asOf >= asset.purchasedOn) { "Cost snapshot cannot predate purchase" }
        residualValue.requireNonNegative("Residual value")
        require(residualValue.currency == asset.purchasePrice.currency) {
            "Residual value must use purchase currency"
        }

        var totalCost = asset.purchasePrice
        maintenanceCosts.forEach { cost ->
            require(cost.assetId == asset.id) { "Maintenance cost belongs to another asset" }
            if (cost.occurredOn <= asOf) {
                require(cost.amount.currency == asset.purchasePrice.currency) {
                    "Maintenance cost must use purchase currency"
                }
                totalCost += cost.amount
            }
        }

        var useQuantity = 0L
        usageEvents.forEach { event ->
            require(event.assetId == asset.id) { "Usage event belongs to another asset" }
            if (event.occurredOn <= asOf) {
                useQuantity = ExactMath.add(useQuantity, event.quantity)
            }
        }

        val ownedDays = daysBetween(asset.purchasedOn, asOf)
        val netCost = totalCost - residualValue
        return AssetCostMetrics(
            assetId = asset.id,
            asOf = asOf,
            ownedDays = ownedDays,
            useQuantity = useQuantity,
            totalOwnershipCost = totalCost,
            residualValue = residualValue,
            netCost = netCost,
            grossDailyOwnershipCost = totalCost.perPositiveUnitOrNull(ownedDays.toLong()),
            netDailyCost = netCost.perPositiveUnitOrNull(ownedDays.toLong()),
            netCostPerUse = netCost.perPositiveUnitOrNull(useQuantity),
        )
    }

    private fun Money.perPositiveUnitOrNull(divisor: Long): Money? =
        if (divisor <= 0) null else dividedBy(divisor, MoneyRounding.HALF_AWAY_FROM_ZERO)
}
