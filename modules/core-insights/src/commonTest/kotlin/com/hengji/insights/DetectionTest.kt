package com.hengji.insights

import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectionTest {
    private val cny = CurrencyCode.CNY
    private val merchant = Merchant("Video Plus", "video-plus")

    @Test
    fun `stable monthly charges form an explainable subscription candidate`() {
        val candidate = SubscriptionDetector.detect(
            listOf(
                tx("s1", 1_000, LocalDate(2026, 1, 1)),
                tx("s2", 1_020, LocalDate(2026, 1, 31)),
                tx("s3", 990, LocalDate(2026, 3, 2)),
                tx("s4", 1_000, LocalDate(2026, 4, 1)),
            ),
            cny,
        ).single()

        assertEquals(SubscriptionCadence.MONTHLY, candidate.cadence)
        assertEquals(1_000, candidate.typicalAmount.minorUnits)
        assertEquals(30, candidate.medianIntervalDays)
        assertEquals(LocalDate(2026, 5, 1), candidate.nextExpectedOn)
        assertTrue(candidate.confidence.basisPoints >= 8_000)
    }

    @Test
    fun `irregular charges are not called subscriptions`() {
        val candidates = SubscriptionDetector.detect(
            listOf(
                tx("s1", 1_000, LocalDate(2026, 1, 1)),
                tx("s2", 1_000, LocalDate(2026, 1, 4)),
                tx("s3", 1_000, LocalDate(2026, 2, 20)),
            ),
            cny,
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `nearby identical charges are grouped as a duplicate candidate`() {
        val duplicate = DuplicateChargeDetector.detect(
            listOf(
                tx("d1", 2_500, LocalDate(2026, 5, 1)),
                tx("d2", 2_500, LocalDate(2026, 5, 2)),
                tx("different", 2_600, LocalDate(2026, 5, 2)),
            ),
            cny,
        ).single()
        assertEquals(listOf(TransactionId("d1"), TransactionId("d2")), duplicate.transactionIds)
    }

    private fun tx(id: String, amount: Long, date: LocalDate) = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.EXPENSE,
        amount = Money(amount, cny),
        bookedOn = date,
        categoryId = CategoryId("subscription"),
        merchant = merchant,
    )
}
