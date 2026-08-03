package com.hengji.app.application

import com.hengji.app.importflow.ImportCommitSelection
import com.hengji.app.importflow.ImportDocumentFormat
import com.hengji.app.importflow.ImportSource
import com.hengji.app.importflow.LocalCaptureMode
import com.hengji.connectors.CandidateStatus
import com.hengji.connectors.ImportFieldMapping
import com.hengji.connectors.LocalDocumentKind
import com.hengji.connectors.ReviewedDocumentBatch
import com.hengji.connectors.ReviewedDocumentBatchCandidate
import com.hengji.data.InMemoryLedgerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.test.runTest

class LocalImportFlowPortTest {
    @Test
    fun capturedCandidatesAreEncodedWithoutRawOcrAndWithStableEscapedRows() {
        val batch = ReviewedDocumentBatch(
            sourceKind = LocalDocumentKind.IMAGE,
            candidates = listOf(
                reviewedCandidate("咖啡, \"店\"", 1_850),
                reviewedCandidate("咖啡, \"店\"", 1_850),
            ),
            skippedAmountCount = 1,
        )

        val first = CapturedDocumentCsvEncoder.encode(batch, LocalCaptureMode.LongScreenshot)
        val second = CapturedDocumentCsvEncoder.encode(batch, LocalCaptureMode.LongScreenshot)

        assertEquals(first, second)
        assertEquals(ImportDocumentFormat.Csv, first.format)
        assertTrue(first.displayName.contains("2笔"))
        assertTrue(first.displayName.contains("跳过1项"))
        assertTrue(first.content.contains("\"咖啡, \"\"店\"\"\""))
        assertTrue(first.content.contains("本机 OCR 候选；原图与 OCR 原文未保存"))
        assertFalse(first.content.contains("OCR 原始秘密"))
        val orderIds = first.content.lineSequence().drop(1).filter(String::isNotBlank)
            .map { it.substringAfter("expense,CNY,").substringBefore(',') }
            .toList()
        assertEquals(2, orderIds.distinct().size)
    }

    @Test
    fun longScreenshotUsesReviewedAtomicImportPipeline() = runTest {
        val repository = InMemoryLedgerRepository()
        var observedMode: LocalCaptureMode? = null
        val capturePicker = object : UserLocalCapturePicker {
            override val isAvailable = true

            override suspend fun pick(mode: LocalCaptureMode): PickedImportDocument {
                observedMode = mode
                return CapturedDocumentCsvEncoder.encode(
                    ReviewedDocumentBatch(
                        sourceKind = LocalDocumentKind.IMAGE,
                        candidates = listOf(
                            reviewedCandidate("早餐店", 1_850),
                            reviewedCandidate("地铁", 300, category = "交通"),
                        ),
                        skippedAmountCount = 0,
                    ),
                    mode,
                )
            }
        }
        val port = LocalImportFlowPort(
            ledger = PreviewLedgerGateway(repository),
            capturePicker = capturePicker,
        )
        val document = requireNotNull(
            port.openSource(ImportSource.LocalCapture(LocalCaptureMode.LongScreenshot)),
        )
        val preview = port.preview(document, reviewedCaptureMapping())

        assertEquals(LocalCaptureMode.LongScreenshot, observedMode)
        assertEquals("local-ocr-long-screenshot", preview.sourceConnectorId)
        assertEquals(2, preview.readyCount)
        val accepted = preview.candidates.mapNotNullTo(mutableSetOf()) { it.transaction?.fingerprint }
        val commit = port.commitAtomically(ImportCommitSelection(document, preview, accepted))
        assertEquals(2, repository.snapshot().transactions.size)

        val rollback = port.rollbackBatch(commit.batchId)
        assertEquals(2, rollback.removedFingerprints.size)
        assertTrue(repository.snapshot().transactions.isEmpty())
    }

    @Test
    fun userFileImportRequestsTransactionImportPolicy() = runTest {
        var observedFormat: ImportDocumentFormat? = null
        var observedPurpose: UserDocumentPurpose? = null
        val picker = UserImportDocumentPicker { format, purpose ->
            observedFormat = format
            observedPurpose = purpose
            PickedImportDocument(
                displayName = "transactions.csv",
                content = "date,amount\n2026-07-01,12.34",
                format = format,
            )
        }
        val port = LocalImportFlowPort(
            ledger = PreviewLedgerGateway(InMemoryLedgerRepository()),
            picker = picker,
        )

        port.openSource(ImportSource.UserFile(ImportDocumentFormat.Csv))

        assertEquals(ImportDocumentFormat.Csv, observedFormat)
        assertEquals(UserDocumentPurpose.TransactionImport, observedPurpose)
    }

    @Test
    fun sandboxImportCommitsToLedgerAndRollsBackAsOneBatch() = runTest {
        val repository = InMemoryLedgerRepository()
        val gateway = PreviewLedgerGateway(repository)
        val port = LocalImportFlowPort(gateway)
        val document = requireNotNull(port.openSource(ImportSource.CsvSandboxSample))
        val mapping = ImportFieldMapping(
            occurredAt = "date",
            amount = "amount",
            merchant = "merchant",
            category = "category",
            direction = "direction",
            currency = "currency",
            note = "note",
            externalId = "orderId",
        )
        val preview = port.preview(document, mapping)
        val accepted = preview.candidates
            .filter { it.status == CandidateStatus.READY }
            .mapNotNullTo(mutableSetOf()) { it.transaction?.fingerprint }

        assertEquals(3, preview.readyCount)
        val commit = port.commitAtomically(ImportCommitSelection(document, preview, accepted))
        assertEquals(3, repository.snapshot().transactions.size)
        assertEquals(1, repository.snapshot().importBatches.size)

        val rollback = port.rollbackBatch(commit.batchId)
        assertEquals(3, rollback.removedFingerprints.size)
        assertTrue(repository.snapshot().transactions.isEmpty())
        assertEquals(1, repository.snapshot().importBatches.size)
    }

    @Test
    fun committedFingerprintsAreMarkedDuplicateOnNextPreview() = runTest {
        val repository = InMemoryLedgerRepository()
        val port = LocalImportFlowPort(PreviewLedgerGateway(repository))
        val mapping = ImportFieldMapping(
            occurredAt = "date",
            amount = "amount",
            merchant = "merchant",
            category = "category",
            direction = "direction",
            currency = "currency",
            externalId = "orderId",
        )
        val firstDocument = requireNotNull(port.openSource(ImportSource.CsvSandboxSample))
        val firstPreview = port.preview(firstDocument, mapping)
        val accepted = firstPreview.candidates.mapNotNullTo(mutableSetOf()) { it.transaction?.fingerprint }
        port.commitAtomically(ImportCommitSelection(firstDocument, firstPreview, accepted))

        val secondDocument = requireNotNull(port.openSource(ImportSource.CsvSandboxSample))
        val secondPreview = port.preview(secondDocument, mapping)
        assertEquals(3, secondPreview.duplicateCount)
        assertEquals(0, secondPreview.readyCount)
    }

    @Test
    fun softDeletedImportIsDuplicateBeforeCommitAndOffersNoRowsToInsert() = runTest {
        val repository = InMemoryLedgerRepository()
        val gateway = PreviewLedgerGateway(repository)
        val port = LocalImportFlowPort(gateway)
        val mapping = ImportFieldMapping(
            occurredAt = "date",
            amount = "amount",
            merchant = "merchant",
            category = "category",
            direction = "direction",
            currency = "currency",
            externalId = "orderId",
        )
        val firstDocument = requireNotNull(port.openSource(ImportSource.CsvSandboxSample))
        val firstPreview = port.preview(firstDocument, mapping)
        val accepted = firstPreview.candidates.mapNotNullTo(mutableSetOf()) { it.transaction?.fingerprint }
        port.commitAtomically(ImportCommitSelection(firstDocument, firstPreview, accepted))
        val deletedId = repository.snapshot().transactions.first().id
        assertTrue(gateway.softDeleteTransaction(deletedId, deletedAtEpochMillis = 10))

        val secondDocument = requireNotNull(port.openSource(ImportSource.CsvSandboxSample))
        val secondPreview = port.preview(secondDocument, mapping)

        assertEquals(3, secondPreview.duplicateCount)
        assertEquals(0, secondPreview.readyCount)
        assertEquals(2, repository.snapshot().transactions.size)
        assertEquals(3, repository.snapshot(includeDeleted = true).transactions.size)
    }

    private fun reviewedCandidate(
        merchant: String,
        amountMinor: Long,
        category: String = "餐饮",
    ) = ReviewedDocumentBatchCandidate(
        merchant = merchant,
        amountMinor = amountMinor,
        bookedOn = LocalDate(2026, 8, 3),
        currency = "CNY",
        categoryHint = category,
        direction = "expense",
    )

    private fun reviewedCaptureMapping() = ImportFieldMapping(
        occurredAt = "date",
        amount = "amount",
        merchant = "merchant",
        category = "category",
        direction = "direction",
        currency = "currency",
        note = "note",
        externalId = "orderId",
    )
}
