package com.hengji.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    private val cny = CurrencyCode.CNY

    @Test
    fun `checked arithmetic rejects overflow`() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, cny) + Money(1, cny) }
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE, cny).unaryMinus() }
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, cny) * 2 }
    }

    @Test
    fun `currency mismatch is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Money(100, cny) + Money(100, CurrencyCode.USD)
        }
    }

    @Test
    fun `rounding is symmetric around zero`() {
        assertEquals(3, Money(5, cny).dividedBy(2).minorUnits)
        assertEquals(-3, Money(-5, cny).dividedBy(2).minorUnits)
        assertEquals(2, Money(5, cny).dividedBy(2, MoneyRounding.TOWARD_ZERO).minorUnits)
    }

    @Test
    fun `scaled division avoids an overflowing intermediate product`() {
        val scaled = Money(Long.MAX_VALUE - 1, cny).multiplyAndDivide(10_000, Long.MAX_VALUE)
        assertEquals(Money(10_000, cny), scaled)
        assertEquals(
            -5_000,
            Money(-(Long.MAX_VALUE - 1), cny).multiplyAndDivide(5_000, Long.MAX_VALUE).minorUnits,
        )
    }

    @Test
    fun `scaled division still rejects a result that cannot fit`() {
        assertFailsWith<ArithmeticException> {
            Money(Long.MAX_VALUE, cny).multiplyAndDivide(2, 1)
        }
    }
}
