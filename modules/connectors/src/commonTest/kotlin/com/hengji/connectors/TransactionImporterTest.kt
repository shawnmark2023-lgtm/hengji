package com.hengji.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransactionImporterTest {
    private val mapping = ImportFieldMapping(
        occurredAt = "date",
        amount = "amount",
        merchant = "merchant",
        category = "category",
        externalId = "id",
    )

    @Test
    fun parsesQuotedCsvAndDetectsDuplicatesWithinFile() {
        val csv = "date,amount,merchant,category,id\n2026-07-01,12.34,\"Coffee, Inc\",餐饮,same\n2026-07-01,12.34,\"Coffee, Inc\",餐饮,same"
        val preview = TransactionImporter().previewCsv(csv, mapping, "csv-local")

        assertEquals(1, preview.readyCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(1_234, preview.candidates.first().transaction?.amountMinor)
        assertEquals("Coffee, Inc", preview.candidates.first().transaction?.merchant)
    }

    @Test
    fun rejectsFormulaLikeText() {
        val csv = "date,amount,merchant,category,id\n2026-07-01,12.34,=HYPERLINK(1),餐饮,x"
        val candidate = TransactionImporter().previewCsv(csv, mapping, "csv-local").candidates.single()

        assertEquals(CandidateStatus.INVALID, candidate.status)
        assertTrue(candidate.issues.any { it.code == ImportErrorCode.DANGEROUS_FORMULA })
    }

    @Test
    fun parsesJsonWrapperAndMinorUnitsWithoutFloatingPoint() {
        val json = """{"transactions":[{"occurredAt":"2026-07-01","amountMinor":1234,"currency":"CNY","merchant":"本地商户"}]}"""
        val jsonMapping = ImportFieldMapping(
            occurredAt = "occurredAt",
            amount = "amountMinor",
            amountEncoding = AmountEncoding.MINOR_UNITS,
            currency = "currency",
            merchant = "merchant",
        )

        val transaction = TransactionImporter().previewJson(json, jsonMapping, "json-local").candidates.single().transaction
        assertEquals(1_234, transaction?.amountMinor)
    }

    @Test
    fun rejectsOversizedInputBeforeParsing() {
        val importer = TransactionImporter(ImportLimits(maxBytes = 16))
        val error = assertFailsWith<ImportFormatException> {
            importer.previewCsv("date,amount\n2026-07-01,12.34", mapping, "csv-local")
        }
        assertEquals(ImportErrorCode.FILE_TOO_LARGE, error.issue.code)
    }

    @Test
    fun commitsAndRollsBackAnEntireBatch() {
        val preview = TransactionImporter().previewCsv(
            "date,amount,merchant,category,id\n2026-07-01,12.34,商户,餐饮,x",
            mapping,
            "csv-local",
        )
        val transaction = requireNotNull(preview.candidates.single().transaction)
        val ledger = InMemoryImportLedger()
        ledger.commit(ImportCommitRequest("batch_123456", "csv-local", listOf(transaction)), "2026-07-15T10:00:00Z")

        assertEquals(1, ledger.snapshot().size)
        val rollback = ledger.rollbackBatch("batch_123456", "2026-07-15T10:01:00Z")
        assertEquals(1, rollback.removedFingerprints.size)
        assertTrue(ledger.snapshot().isEmpty())
        assertTrue(ledger.rollbackBatch("batch_123456", "2026-07-15T10:02:00Z").alreadyRolledBack)
    }
}
