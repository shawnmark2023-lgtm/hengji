package com.hengji.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CostCalculatorTest {
    private val asset = Asset(
        id = AssetId("camera"),
        name = "Camera",
        categoryId = CategoryId("electronics"),
        purchasePrice = Money(10_000, CurrencyCode.CNY),
        purchasedOn = LocalDate(2026, 1, 1),
    )

    @Test
    fun `calculates total daily net and per-use cost exactly`() {
        val metrics = AssetCostCalculator.calculate(
            asset = asset,
            maintenanceCosts = listOf(
                MaintenanceCost(
                    MaintenanceCostId("m1"),
                    asset.id,
                    Money(500, CurrencyCode.CNY),
                    LocalDate(2026, 1, 5),
                ),
            ),
            usageEvents = listOf(
                UsageEvent(UsageEventId("u1"), asset.id, LocalDate(2026, 1, 2), 2),
                UsageEvent(UsageEventId("u2"), asset.id, LocalDate(2026, 1, 8), 1),
            ),
            residualValue = Money(3_000, CurrencyCode.CNY),
            asOf = LocalDate(2026, 1, 11),
        )

        assertEquals(10, metrics.ownedDays)
        assertEquals(3, metrics.useQuantity)
        assertEquals(10_500, metrics.totalOwnershipCost.minorUnits)
        assertEquals(7_500, metrics.netCost.minorUnits)
        assertEquals(1_050, metrics.grossDailyOwnershipCost?.minorUnits)
        assertEquals(750, metrics.netDailyCost?.minorUnits)
        assertEquals(2_500, metrics.netCostPerUse?.minorUnits)
    }

    @Test
    fun `purchase-day snapshot and zero use do not divide by zero`() {
        val metrics = AssetCostCalculator.calculate(asset = asset, asOf = asset.purchasedOn)
        assertEquals(0, metrics.ownedDays)
        assertNull(metrics.grossDailyOwnershipCost)
        assertNull(metrics.netDailyCost)
        assertNull(metrics.netCostPerUse)
    }

    @Test
    fun `residual above total cost produces a valid negative net cost`() {
        val metrics = AssetCostCalculator.calculate(
            asset = asset,
            usageEvents = listOf(UsageEvent(UsageEventId("u1"), asset.id, LocalDate(2026, 1, 2), 2)),
            residualValue = Money(15_000, CurrencyCode.CNY),
            asOf = LocalDate(2026, 1, 11),
        )

        assertEquals(-5_000, metrics.netCost.minorUnits)
        assertEquals(-500, metrics.netDailyCost?.minorUnits)
        assertEquals(-2_500, metrics.netCostPerUse?.minorUnits)
    }

    @Test
    fun `future costs and usage are excluded from snapshot`() {
        val metrics = AssetCostCalculator.calculate(
            asset = asset,
            maintenanceCosts = listOf(
                MaintenanceCost(MaintenanceCostId("future"), asset.id, Money(9_000, CurrencyCode.CNY), LocalDate(2027, 1, 1)),
            ),
            usageEvents = listOf(UsageEvent(UsageEventId("future"), asset.id, LocalDate(2027, 1, 1), 4)),
            asOf = LocalDate(2026, 2, 1),
        )
        assertEquals(10_000, metrics.totalOwnershipCost.minorUnits)
        assertEquals(0, metrics.useQuantity)
        assertNull(metrics.netCostPerUse)
    }
}
