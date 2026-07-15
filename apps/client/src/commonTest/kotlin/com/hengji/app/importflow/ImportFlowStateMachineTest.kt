package com.hengji.app.importflow

import com.hengji.connectors.CandidateStatus
import com.hengji.connectors.ExternalTransaction
import com.hengji.connectors.ImportCandidate
import com.hengji.connectors.ImportCommitResult
import com.hengji.connectors.ImportErrorCode
import com.hengji.connectors.ImportIssue
import com.hengji.connectors.ImportPreview
import com.hengji.connectors.ImportRollbackResult
import com.hengji.connectors.TransactionDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportFlowStateMachineTest {
    @Test
    fun sourceSelectionEmitsCommandAndIgnoresStaleCompletion() {
        val machine = ImportFlowStateMachine()

        val command = machine.dispatch(ImportFlowEvent.SourceChosen(ImportSource.CsvSandboxSample))
        val load = assertIs<ImportFlowCommand.LoadSource>(command)
        assertTrue(machine.state.isBusy)
        assertEquals(ImportWizardStep.Source, machine.state.step)

        machine.dispatch(ImportFlowEvent.SourceLoaded(load.requestId + 1, sampleDocument()))
        assertTrue(machine.state.isBusy, "stale async results must not replace the active request")

        machine.dispatch(ImportFlowEvent.SourceLoaded(load.requestId, sampleDocument()))
        assertFalse(machine.state.isBusy)
        assertEquals(ImportWizardStep.Mapping, machine.state.step)
        assertEquals("date", machine.state.mapping.occurredAt)
        assertEquals("amount", machine.state.mapping.amount)
        assertEquals("merchant", machine.state.mapping.merchant)
    }

    @Test
    fun previewRequiresDateAndAmountMappings() {
        val machine = ImportFlowStateMachine()
        val sourceCommand = assertIs<ImportFlowCommand.LoadSource>(
            machine.dispatch(ImportFlowEvent.SourceChosen(ImportSource.UserFile(ImportDocumentFormat.Csv))),
        )
        val document = sampleDocument(fields = listOf("when", "value", "who"))
        machine.dispatch(ImportFlowEvent.SourceLoaded(sourceCommand.requestId, document))

        assertFalse(machine.state.mapping.isComplete)
        assertNull(machine.dispatch(ImportFlowEvent.PreviewRequested))

        machine.dispatch(ImportFlowEvent.MappingChanged(ImportTargetField.OccurredAt, "when"))
        machine.dispatch(ImportFlowEvent.MappingChanged(ImportTargetField.Amount, "value"))
        val preview = assertIs<ImportFlowCommand.BuildPreview>(
            machine.dispatch(ImportFlowEvent.PreviewRequested),
        )

        assertEquals(document.documentId, preview.document.documentId)
        assertEquals("when", preview.mapping.occurredAt)
        assertEquals("value", preview.mapping.amount)
    }

    @Test
    fun previewSelectsOnlyReadyRowsAndKeepsDuplicatesAndErrorsExcluded() {
        val preview = mixedPreview()
        val initial = ImportWizardState(
            step = ImportWizardStep.Mapping,
            source = ImportSource.CsvSandboxSample,
            document = sampleDocument(),
            mapping = ImportMappingDraft(occurredAt = "date", amount = "amount"),
            requestSequence = 4,
            activeRequestId = 4,
        )
        val machine = ImportFlowStateMachine(initial)

        machine.dispatch(ImportFlowEvent.PreviewLoaded(4, preview))

        assertEquals(ImportWizardStep.Preview, machine.state.step)
        assertEquals(1, machine.state.readyCount)
        assertEquals(1, machine.state.duplicateCount)
        assertEquals(1, machine.state.invalidCount)
        assertEquals(setOf("hj1_ready"), machine.state.acceptedFingerprints)

        machine.dispatch(ImportFlowEvent.CandidateToggled("hj1_duplicate", accepted = true))
        assertEquals(setOf("hj1_ready"), machine.state.acceptedFingerprints)

        machine.dispatch(ImportFlowEvent.CandidateToggled("hj1_ready", accepted = false))
        assertTrue(machine.state.acceptedFingerprints.isEmpty())
        assertNull(machine.dispatch(ImportFlowEvent.ConfirmationRequested))
        assertEquals(ImportWizardStep.Preview, machine.state.step)
    }

    @Test
    fun commitAndRollbackRemainBatchScoped() {
        val preview = mixedPreview()
        val machine = ImportFlowStateMachine(
            ImportWizardState(
                step = ImportWizardStep.Preview,
                source = ImportSource.CsvSandboxSample,
                document = sampleDocument(),
                mapping = ImportMappingDraft(occurredAt = "date", amount = "amount"),
                preview = preview,
                acceptedFingerprints = setOf("hj1_ready"),
            ),
        )

        machine.dispatch(ImportFlowEvent.ConfirmationRequested)
        assertEquals(ImportWizardStep.Confirm, machine.state.step)

        val commit = assertIs<ImportFlowCommand.Commit>(machine.dispatch(ImportFlowEvent.CommitRequested))
        assertEquals(setOf("hj1_ready"), commit.selection.acceptedFingerprints)
        machine.dispatch(
            ImportFlowEvent.CommitCompleted(
                commit.requestId,
                ImportCommitResult("batch_123456", listOf("hj1_ready"), "2026-07-15T12:00:00Z"),
            ),
        )
        assertEquals(ImportWizardStep.Result, machine.state.step)

        val rollback = assertIs<ImportFlowCommand.Rollback>(machine.dispatch(ImportFlowEvent.RollbackRequested))
        assertEquals("batch_123456", rollback.batchId)
        machine.dispatch(
            ImportFlowEvent.RollbackCompleted(
                rollback.requestId,
                ImportRollbackResult(
                    batchId = "batch_123456",
                    removedFingerprints = listOf("hj1_ready"),
                    rolledBackAt = "2026-07-15T12:05:00Z",
                    alreadyRolledBack = false,
                ),
            ),
        )

        assertEquals(listOf("hj1_ready"), machine.state.rollbackResult?.removedFingerprints)
        assertNull(machine.dispatch(ImportFlowEvent.RollbackRequested), "the same UI state cannot roll back twice")
    }

    @Test
    fun failureMessageIsAppliedOnlyToMatchingRequest() {
        val machine = ImportFlowStateMachine()
        val command = assertIs<ImportFlowCommand.LoadSource>(
            machine.dispatch(ImportFlowEvent.SourceChosen(ImportSource.JsonSandboxSample)),
        )

        machine.dispatch(ImportFlowEvent.OperationFailed(command.requestId + 1, "错误", "stale"))
        assertNull(machine.state.error)
        assertTrue(machine.state.isBusy)

        machine.dispatch(ImportFlowEvent.OperationFailed(command.requestId, "无法解析", "JSON 结构不受支持"))
        assertFalse(machine.state.isBusy)
        assertEquals("无法解析", machine.state.error?.title)
        assertEquals("JSON 结构不受支持", machine.state.error?.message)
    }

    @Test
    fun documentSummaryRejectsMoreThanFiveUiSampleRows() {
        val result = runCatching {
            sampleDocument().copy(sampleRows = List(6) { mapOf("date" to "2026-07-15") })
        }
        assertTrue(result.isFailure)
    }

    private fun sampleDocument(
        fields: List<String> = listOf("date", "amount", "merchant", "category"),
    ) = ImportDocumentSummary(
        documentId = "doc_123",
        displayName = "示例账单.csv",
        format = ImportDocumentFormat.Csv,
        byteCount = 1_024,
        fields = fields,
        sampleRows = listOf(fields.associateWith { "•••" }),
        isSandbox = true,
    )

    private fun mixedPreview(): ImportPreview = ImportPreview(
        sourceConnectorId = "csv-local",
        candidates = listOf(
            ImportCandidate(2, transaction("hj1_ready"), CandidateStatus.READY),
            ImportCandidate(3, transaction("hj1_duplicate"), CandidateStatus.DUPLICATE),
            ImportCandidate(
                sourceRowNumber = 4,
                transaction = null,
                status = CandidateStatus.INVALID,
                issues = listOf(ImportIssue(ImportErrorCode.INVALID_AMOUNT, "invalid", 4, "amount")),
            ),
        ),
        fileIssues = emptyList(),
    )

    private fun transaction(fingerprint: String) = ExternalTransaction(
        occurredAt = "2026-07-15",
        amountMinor = 1_280,
        currency = "CNY",
        direction = TransactionDirection.EXPENSE,
        merchant = "示例商户",
        category = "餐饮",
        note = null,
        externalId = null,
        sourceConnectorId = "csv-local",
        fingerprint = fingerprint,
    )
}
