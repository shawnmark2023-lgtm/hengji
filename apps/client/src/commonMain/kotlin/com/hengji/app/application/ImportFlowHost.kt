package com.hengji.app.application

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.hengji.app.importflow.ImportFlowCommand
import com.hengji.app.importflow.ImportFlowEvent
import com.hengji.app.importflow.ImportFlowPort
import com.hengji.app.importflow.ImportFlowReducer
import com.hengji.app.importflow.ImportWizardState
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class ImportFlowHost(
    val state: ImportWizardState,
    val dispatch: (ImportFlowEvent) -> Unit,
)

@Composable
fun rememberImportFlowHost(
    port: ImportFlowPort,
    onLedgerChanged: () -> Unit,
): ImportFlowHost {
    val scope = rememberCoroutineScope()
    var state by remember(port) { mutableStateOf(ImportWizardState()) }

    fun dispatch(event: ImportFlowEvent) {
        val transition = ImportFlowReducer.reduce(state, event)
        state = transition.state
        val command = transition.command ?: return
        scope.launch {
            val completion = try {
                execute(port, command).also {
                    if (command is ImportFlowCommand.Commit || command is ImportFlowCommand.Rollback) {
                        onLedgerChanged()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ImportFlowEvent.OperationFailed(
                    requestId = command.requestId,
                    title = when (command) {
                        is ImportFlowCommand.LoadSource -> "无法打开来源"
                        is ImportFlowCommand.BuildPreview -> "无法生成预览"
                        is ImportFlowCommand.Commit -> "导入未写入"
                        is ImportFlowCommand.Rollback -> "撤销未完成"
                    },
                    safeMessage = when (error) {
                        is IllegalArgumentException, is IllegalStateException, is UnsupportedOperationException ->
                            error.message ?: "请求未完成，请检查数据后重试。"
                        else -> "请求未完成；原始账本未写入，请重试。"
                    },
                )
            }
            state = ImportFlowReducer.reduce(state, completion).state
        }
    }

    return ImportFlowHost(state = state, dispatch = ::dispatch)
}

private suspend fun execute(port: ImportFlowPort, command: ImportFlowCommand): ImportFlowEvent = when (command) {
    is ImportFlowCommand.LoadSource -> ImportFlowEvent.SourceLoaded(
        requestId = command.requestId,
        document = port.openSource(command.source),
    )
    is ImportFlowCommand.BuildPreview -> ImportFlowEvent.PreviewLoaded(
        requestId = command.requestId,
        preview = port.preview(command.document, command.mapping),
    )
    is ImportFlowCommand.Commit -> ImportFlowEvent.CommitCompleted(
        requestId = command.requestId,
        result = port.commitAtomically(command.selection),
    )
    is ImportFlowCommand.Rollback -> ImportFlowEvent.RollbackCompleted(
        requestId = command.requestId,
        result = port.rollbackBatch(command.batchId),
    )
}
