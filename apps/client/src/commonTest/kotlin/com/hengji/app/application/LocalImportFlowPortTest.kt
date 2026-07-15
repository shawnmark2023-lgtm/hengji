package com.hengji.app.application

import com.hengji.app.importflow.ImportCommitSelection
import com.hengji.app.importflow.ImportSource
import com.hengji.connectors.CandidateStatus
import com.hengji.connectors.ImportFieldMapping
import com.hengji.data.InMemoryLedgerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LocalImportFlowPortTest {
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
}
