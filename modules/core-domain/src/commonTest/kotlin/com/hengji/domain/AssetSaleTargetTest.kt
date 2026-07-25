package com.hengji.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssetSaleTargetTest {
    private val cny = CurrencyCode.CNY

    @Test
    fun `sale target must be positive and use the purchase currency`() {
        assertFailsWith<IllegalArgumentException> {
            asset(Money(0, cny))
        }
        assertFailsWith<IllegalArgumentException> {
            asset(Money(-1, cny))
        }
        assertFailsWith<IllegalArgumentException> {
            asset(Money(1_000, CurrencyCode("USD")))
        }
    }

    @Test
    fun `positive same-currency sale target is retained`() {
        val target = Money(1_500, cny)

        assertEquals(target, asset(target).saleTargetPrice)
    }

    private fun asset(target: Money) = Asset(
        id = AssetId("sale-target"),
        name = "Camera",
        categoryId = CategoryId("electronics"),
        purchasePrice = Money(2_000, cny),
        purchasedOn = LocalDate(2026, 1, 1),
        saleTargetPrice = target,
    )
}
