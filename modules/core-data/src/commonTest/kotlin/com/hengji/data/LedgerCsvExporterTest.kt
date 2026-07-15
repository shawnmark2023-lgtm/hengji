package com.hengji.data

import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class LedgerCsvExporterTest {
    @Test
    fun preservesMinorUnitsAndNeutralizesSpreadsheetFormulaPrefixes() {
        val transaction = Transaction(
            id = TransactionId("csv-1"),
            kind = TransactionKind.EXPENSE,
            amount = Money(12_345, CurrencyCode.CNY),
            bookedOn = LocalDate(2026, 7, 15),
            categoryId = CategoryId("other"),
            merchant = Merchant("=HYPERLINK(\"https://invalid.example\")"),
        )
        val csv = LedgerCsvExporter.export(
            LedgerSnapshot(1, listOf(transaction), emptyList(), emptyList(), emptyList(), emptyList()),
        )

        assertTrue("\"12345\"" in csv)
        assertTrue("\"'=HYPERLINK(\"\"https://invalid.example\"\")\"" in csv)
    }
}
