package com.hengji.domain

/** ISO-4217 style currency code. Unknown/private codes are allowed when they use three ASCII letters. */
data class CurrencyCode(val value: String) {
    init {
        require(value.length == 3 && value.all { it in 'A'..'Z' }) {
            "Currency code must contain exactly three uppercase ASCII letters"
        }
    }

    companion object {
        val CNY = CurrencyCode("CNY")
        val USD = CurrencyCode("USD")
        val EUR = CurrencyCode("EUR")
    }
}

enum class MoneyRounding {
    TOWARD_ZERO,
    HALF_AWAY_FROM_ZERO,
}

/**
 * A monetary amount stored only in the currency's minor unit.
 *
 * Negative values are valid for deltas and net costs. Entity constructors decide where amounts must be non-negative.
 */
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) : Comparable<Money> {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = ExactMath.add(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = ExactMath.subtract(minorUnits, other.minorUnits))
    }

    operator fun unaryMinus(): Money = copy(minorUnits = ExactMath.negate(minorUnits))

    operator fun times(multiplier: Long): Money = copy(minorUnits = ExactMath.multiply(minorUnits, multiplier))

    fun dividedBy(divisor: Long, rounding: MoneyRounding = MoneyRounding.HALF_AWAY_FROM_ZERO): Money =
        copy(minorUnits = ExactMath.divideRounded(minorUnits, divisor, rounding))

    fun multiplyAndDivide(
        multiplier: Long,
        divisor: Long,
        rounding: MoneyRounding = MoneyRounding.HALF_AWAY_FROM_ZERO,
    ): Money = copy(
        minorUnits = ExactMath.multiplyDivideRounded(
            value = minorUnits,
            multiplier = multiplier,
            divisor = divisor,
            rounding = rounding,
        ),
    )

    fun requireNonNegative(label: String = "Money"): Money {
        require(minorUnits >= 0) { "$label cannot be negative" }
        return this
    }

    fun isPositive(): Boolean = minorUnits > 0

    fun isZero(): Boolean = minorUnits == 0L

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: ${currency.value} and ${other.currency.value}"
        }
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0, currency)
    }
}

/** Checked integer operations shared by deterministic financial calculations. */
object ExactMath {
    fun add(left: Long, right: Long): Long {
        val result = left + right
        if (((left xor result) and (right xor result)) < 0) {
            throw ArithmeticException("Long addition overflow: $left + $right")
        }
        return result
    }

    fun subtract(left: Long, right: Long): Long {
        val result = left - right
        if (((left xor right) and (left xor result)) < 0) {
            throw ArithmeticException("Long subtraction overflow: $left - $right")
        }
        return result
    }

    fun negate(value: Long): Long {
        if (value == Long.MIN_VALUE) throw ArithmeticException("Long negation overflow")
        return -value
    }

    fun multiply(left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0L
        if ((left == Long.MIN_VALUE && right == -1L) || (right == Long.MIN_VALUE && left == -1L)) {
            throw ArithmeticException("Long multiplication overflow: $left * $right")
        }
        val result = left * right
        if (result / right != left) {
            throw ArithmeticException("Long multiplication overflow: $left * $right")
        }
        return result
    }

    fun divideRounded(
        value: Long,
        divisor: Long,
        rounding: MoneyRounding = MoneyRounding.HALF_AWAY_FROM_ZERO,
    ): Long {
        require(divisor > 0) { "Divisor must be positive" }
        val quotient = value / divisor
        val remainder = value % divisor
        if (rounding == MoneyRounding.TOWARD_ZERO || remainder == 0L) return quotient

        val remainderMagnitude = if (remainder < 0) -remainder else remainder
        val roundThreshold = divisor / 2 + divisor % 2
        if (remainderMagnitude < roundThreshold) return quotient
        return if (value > 0) add(quotient, 1) else subtract(quotient, 1)
    }

    /**
     * Computes value * multiplier / divisor without overflowing an intermediate product when the rounded result fits.
     * Multiplier and divisor are non-negative because all current callers scale by counts or basis points.
     */
    fun multiplyDivideRounded(
        value: Long,
        multiplier: Long,
        divisor: Long,
        rounding: MoneyRounding = MoneyRounding.HALF_AWAY_FROM_ZERO,
    ): Long {
        require(multiplier >= 0) { "Multiplier must be non-negative" }
        require(divisor > 0) { "Divisor must be positive" }
        if (value == 0L || multiplier == 0L) return 0L

        val whole = value / divisor
        val signedRemainder = value % divisor
        var result = multiply(whole, multiplier)
        val remainderMagnitude = if (signedRemainder < 0) -signedRemainder else signedRemainder
        val fractional = multiplyRemainder(remainderMagnitude, multiplier, divisor)

        result = if (value > 0) add(result, fractional.quotient) else subtract(result, fractional.quotient)
        if (rounding == MoneyRounding.HALF_AWAY_FROM_ZERO && fractional.remainder != 0L) {
            val threshold = divisor / 2 + divisor % 2
            if (fractional.remainder >= threshold) {
                result = if (value > 0) add(result, 1) else subtract(result, 1)
            }
        }
        return result
    }

    private data class DivMod(val quotient: Long, val remainder: Long)

    /** Multiplies a remainder by a non-negative value as a quotient/remainder pair using binary addition. */
    private fun multiplyRemainder(remainder: Long, multiplier: Long, divisor: Long): DivMod {
        var remainingMultiplier = multiplier
        var accumulated = DivMod(0, 0)
        var addend = DivMod(0, remainder)

        while (remainingMultiplier > 0) {
            if ((remainingMultiplier and 1L) == 1L) {
                accumulated = addDivMod(accumulated, addend, divisor)
            }
            remainingMultiplier = remainingMultiplier ushr 1
            if (remainingMultiplier > 0) {
                addend = addDivMod(addend, addend, divisor)
            }
        }
        return accumulated
    }

    private fun addDivMod(left: DivMod, right: DivMod, divisor: Long): DivMod {
        var quotient = add(left.quotient, right.quotient)
        val distanceToDivisor = divisor - right.remainder
        val remainder = if (left.remainder >= distanceToDivisor) {
            quotient = add(quotient, 1)
            left.remainder - distanceToDivisor
        } else {
            left.remainder + right.remainder
        }
        return DivMod(quotient, remainder)
    }
}
