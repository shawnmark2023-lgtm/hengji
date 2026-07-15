package com.hengji.app.importflow

import com.hengji.connectors.CandidateStatus
import com.hengji.connectors.ImportCommitResult
import com.hengji.connectors.ImportPreview
import com.hengji.connectors.ImportRollbackResult

enum class ImportWizardStep(val label: String) {
    Source("选择来源"),
    Mapping("字段映射"),
    Preview("预览去重"),
    Confirm("确认导入"),
    Result("完成"),
}

data class ImportFlowError(
    val title: String,
    val message: String,
    val recoverable: Boolean = true,
)

data class ImportWizardState(
    val step: ImportWizardStep = ImportWizardStep.Source,
    val source: ImportSource? = null,
    val document: ImportDocumentSummary? = null,
    val mapping: ImportMappingDraft = ImportMappingDraft(),
    val preview: ImportPreview? = null,
    val acceptedFingerprints: Set<String> = emptySet(),
    val commitResult: ImportCommitResult? = null,
    val rollbackResult: ImportRollbackResult? = null,
    val error: ImportFlowError? = null,
    val requestSequence: Long = 0,
    val activeRequestId: Long? = null,
) {
    val isBusy: Boolean get() = activeRequestId != null
    val canPreview: Boolean get() = document != null && mapping.isComplete && !isBusy
    val canConfirm: Boolean get() = preview != null && acceptedFingerprints.isNotEmpty() && !isBusy
    val canCommit: Boolean get() = step == ImportWizardStep.Confirm && canConfirm

    val selectedCount: Int get() = acceptedFingerprints.size
    val readyCount: Int get() = preview?.readyCount ?: 0
    val duplicateCount: Int get() = preview?.duplicateCount ?: 0
    val invalidCount: Int get() = preview?.invalidCount ?: 0
}

sealed interface ImportFlowEvent {
    data class SourceChosen(val source: ImportSource) : ImportFlowEvent

    data class SourceLoaded(
        val requestId: Long,
        val document: ImportDocumentSummary?,
    ) : ImportFlowEvent

    data class MappingChanged(
        val target: ImportTargetField,
        val sourceField: String?,
    ) : ImportFlowEvent

    data object PreviewRequested : ImportFlowEvent

    data class PreviewLoaded(
        val requestId: Long,
        val preview: ImportPreview,
    ) : ImportFlowEvent

    data class CandidateToggled(
        val fingerprint: String,
        val accepted: Boolean,
    ) : ImportFlowEvent

    data object ConfirmationRequested : ImportFlowEvent
    data object CommitRequested : ImportFlowEvent

    data class CommitCompleted(
        val requestId: Long,
        val result: ImportCommitResult,
    ) : ImportFlowEvent

    data object RollbackRequested : ImportFlowEvent

    data class RollbackCompleted(
        val requestId: Long,
        val result: ImportRollbackResult,
    ) : ImportFlowEvent

    data class OperationFailed(
        val requestId: Long,
        val title: String,
        val safeMessage: String,
        val recoverable: Boolean = true,
    ) : ImportFlowEvent

    data object ErrorDismissed : ImportFlowEvent
    data object BackRequested : ImportFlowEvent
    data object ResetRequested : ImportFlowEvent
}

sealed interface ImportFlowCommand {
    val requestId: Long

    data class LoadSource(
        override val requestId: Long,
        val source: ImportSource,
    ) : ImportFlowCommand

    data class BuildPreview(
        override val requestId: Long,
        val document: ImportDocumentSummary,
        val mapping: com.hengji.connectors.ImportFieldMapping,
    ) : ImportFlowCommand

    data class Commit(
        override val requestId: Long,
        val selection: ImportCommitSelection,
    ) : ImportFlowCommand

    data class Rollback(
        override val requestId: Long,
        val batchId: String,
    ) : ImportFlowCommand
}

data class ImportFlowTransition(
    val state: ImportWizardState,
    val command: ImportFlowCommand? = null,
)

object ImportFlowReducer {
    fun reduce(
        state: ImportWizardState,
        event: ImportFlowEvent,
    ): ImportFlowTransition = when (event) {
        is ImportFlowEvent.SourceChosen -> startRequest(state) { requestId ->
            ImportFlowTransition(
                state = state.copy(
                    step = ImportWizardStep.Source,
                    source = event.source,
                    document = null,
                    mapping = ImportMappingDraft(),
                    preview = null,
                    acceptedFingerprints = emptySet(),
                    commitResult = null,
                    rollbackResult = null,
                    error = null,
                    requestSequence = requestId,
                    activeRequestId = requestId,
                ),
                command = ImportFlowCommand.LoadSource(requestId, event.source),
            )
        }

        is ImportFlowEvent.SourceLoaded -> {
            if (!state.accepts(event.requestId)) unchanged(state) else if (event.document == null) {
                unchanged(state.copy(activeRequestId = null))
            } else {
                val suggestedMapping = suggestMapping(event.document.fields)
                unchanged(
                    state.copy(
                        step = ImportWizardStep.Mapping,
                        document = event.document,
                        mapping = suggestedMapping,
                        activeRequestId = null,
                        error = null,
                    ),
                )
            }
        }

        is ImportFlowEvent.MappingChanged -> {
            if (state.isBusy || state.step != ImportWizardStep.Mapping) unchanged(state) else {
                val field = event.sourceField
                if (field != null && field !in (state.document?.fields ?: emptyList())) {
                    unchanged(state)
                } else {
                    unchanged(
                        state.copy(
                            mapping = state.mapping.map(event.target, field),
                            preview = null,
                            acceptedFingerprints = emptySet(),
                            error = null,
                        ),
                    )
                }
            }
        }

        ImportFlowEvent.PreviewRequested -> {
            val document = state.document
            val mapping = state.mapping.toConnectorMapping()
            if (!state.canPreview || document == null || mapping == null) unchanged(state) else {
                startRequest(state) { requestId ->
                    ImportFlowTransition(
                        state = state.copy(
                            requestSequence = requestId,
                            activeRequestId = requestId,
                            error = null,
                        ),
                        command = ImportFlowCommand.BuildPreview(requestId, document, mapping),
                    )
                }
            }
        }

        is ImportFlowEvent.PreviewLoaded -> {
            if (!state.accepts(event.requestId)) unchanged(state) else {
                val accepted = event.preview.candidates
                    .asSequence()
                    .filter { it.status == CandidateStatus.READY }
                    .mapNotNull { it.transaction?.fingerprint }
                    .toSet()
                unchanged(
                    state.copy(
                        step = ImportWizardStep.Preview,
                        preview = event.preview,
                        acceptedFingerprints = accepted,
                        activeRequestId = null,
                        error = null,
                    ),
                )
            }
        }

        is ImportFlowEvent.CandidateToggled -> {
            if (state.isBusy || state.step != ImportWizardStep.Preview) unchanged(state) else {
                val candidate = state.preview?.candidates?.firstOrNull {
                    it.transaction?.fingerprint == event.fingerprint
                }
                if (candidate?.status != CandidateStatus.READY) unchanged(state) else {
                    val next = if (event.accepted) {
                        state.acceptedFingerprints + event.fingerprint
                    } else {
                        state.acceptedFingerprints - event.fingerprint
                    }
                    unchanged(state.copy(acceptedFingerprints = next))
                }
            }
        }

        ImportFlowEvent.ConfirmationRequested -> {
            if (state.step == ImportWizardStep.Preview && state.canConfirm) {
                unchanged(state.copy(step = ImportWizardStep.Confirm, error = null))
            } else {
                unchanged(state)
            }
        }

        ImportFlowEvent.CommitRequested -> {
            val document = state.document
            val preview = state.preview
            if (!state.canCommit || document == null || preview == null) unchanged(state) else {
                startRequest(state) { requestId ->
                    ImportFlowTransition(
                        state = state.copy(
                            requestSequence = requestId,
                            activeRequestId = requestId,
                            error = null,
                        ),
                        command = ImportFlowCommand.Commit(
                            requestId,
                            ImportCommitSelection(document, preview, state.acceptedFingerprints),
                        ),
                    )
                }
            }
        }

        is ImportFlowEvent.CommitCompleted -> {
            if (!state.accepts(event.requestId)) unchanged(state) else {
                unchanged(
                    state.copy(
                        step = ImportWizardStep.Result,
                        commitResult = event.result,
                        rollbackResult = null,
                        activeRequestId = null,
                        error = null,
                    ),
                )
            }
        }

        ImportFlowEvent.RollbackRequested -> {
            val batchId = state.commitResult?.batchId
            if (state.step != ImportWizardStep.Result || state.isBusy || batchId == null || state.rollbackResult != null) {
                unchanged(state)
            } else {
                startRequest(state) { requestId ->
                    ImportFlowTransition(
                        state = state.copy(
                            requestSequence = requestId,
                            activeRequestId = requestId,
                            error = null,
                        ),
                        command = ImportFlowCommand.Rollback(requestId, batchId),
                    )
                }
            }
        }

        is ImportFlowEvent.RollbackCompleted -> {
            if (!state.accepts(event.requestId)) unchanged(state) else {
                unchanged(
                    state.copy(
                        rollbackResult = event.result,
                        activeRequestId = null,
                        error = null,
                    ),
                )
            }
        }

        is ImportFlowEvent.OperationFailed -> {
            if (!state.accepts(event.requestId)) unchanged(state) else {
                unchanged(
                    state.copy(
                        activeRequestId = null,
                        error = ImportFlowError(event.title, event.safeMessage, event.recoverable),
                    ),
                )
            }
        }

        ImportFlowEvent.ErrorDismissed -> unchanged(state.copy(error = null))
        ImportFlowEvent.BackRequested -> back(state)
        ImportFlowEvent.ResetRequested -> unchanged(ImportWizardState(requestSequence = state.requestSequence))
    }

    private fun startRequest(
        state: ImportWizardState,
        block: (Long) -> ImportFlowTransition,
    ): ImportFlowTransition {
        if (state.isBusy) return unchanged(state)
        return block(state.requestSequence + 1)
    }

    private fun back(state: ImportWizardState): ImportFlowTransition {
        if (state.isBusy) return unchanged(state)
        return when (state.step) {
            ImportWizardStep.Source -> unchanged(state)
            ImportWizardStep.Mapping -> unchanged(
                state.copy(
                    step = ImportWizardStep.Source,
                    document = null,
                    mapping = ImportMappingDraft(),
                    error = null,
                ),
            )
            ImportWizardStep.Preview -> unchanged(
                state.copy(
                    step = ImportWizardStep.Mapping,
                    preview = null,
                    acceptedFingerprints = emptySet(),
                    error = null,
                ),
            )
            ImportWizardStep.Confirm -> unchanged(state.copy(step = ImportWizardStep.Preview, error = null))
            ImportWizardStep.Result -> unchanged(state)
        }
    }

    private fun suggestMapping(fields: List<String>): ImportMappingDraft {
        fun match(vararg aliases: String): String? = fields.firstOrNull { field ->
            aliases.any { alias -> field.equals(alias, ignoreCase = true) }
        }
        return ImportMappingDraft(
            occurredAt = match("occurredAt", "date", "time", "交易时间", "日期"),
            amount = match("amount", "金额", "交易金额"),
            merchant = match("merchant", "商户", "交易对方", "商品"),
            category = match("category", "分类", "品类"),
            direction = match("direction", "type", "收支类型", "交易类型"),
            currency = match("currency", "币种"),
            note = match("note", "备注"),
            externalId = match("externalId", "orderId", "订单号", "交易号"),
        )
    }

    private fun ImportWizardState.accepts(requestId: Long): Boolean = activeRequestId == requestId

    private fun unchanged(state: ImportWizardState) = ImportFlowTransition(state)
}

class ImportFlowStateMachine(initialState: ImportWizardState = ImportWizardState()) {
    var state: ImportWizardState = initialState
        private set

    fun dispatch(event: ImportFlowEvent): ImportFlowCommand? {
        val transition = ImportFlowReducer.reduce(state, event)
        state = transition.state
        return transition.command
    }
}
