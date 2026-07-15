package com.hengji.domain

import kotlinx.datetime.LocalDate

data class DateRange(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
) {
    init {
        require(startInclusive <= endExclusive) { "Date range cannot end before it starts" }
    }

    val days: Int
        get() = daysBetween(startInclusive, endExclusive)

    operator fun contains(date: LocalDate): Boolean = date >= startInclusive && date < endExclusive
}

internal fun daysBetween(start: LocalDate, end: LocalDate): Int {
    val difference = ExactMath.subtract(end.toEpochDays(), start.toEpochDays())
    require(difference in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        "Date interval exceeds supported Int day range"
    }
    return difference.toInt()
}
