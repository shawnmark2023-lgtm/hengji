package com.hengji.app.application

import com.hengji.domain.TransactionId

internal const val TRANSACTION_UNDO_WINDOW_MILLIS: Long = 8_000

internal data class PendingTransactionUndo(
    val transactionId: TransactionId,
    val deletedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(deletedAtEpochMillis >= 0)
        require(expiresAtEpochMillis >= 0)
    }

    fun isAvailableAt(nowEpochMillis: Long): Boolean =
        nowEpochMillis >= 0 && nowEpochMillis <= expiresAtEpochMillis
}

internal sealed interface TransactionDeletionResult {
    data class Deleted(val pendingUndo: PendingTransactionUndo) : TransactionDeletionResult
    data object Rejected : TransactionDeletionResult
}

/**
 * Keeps deletion and its compare-and-set restore token in the application layer.
 * The exact deletion timestamp prevents an old Undo action from reviving a record
 * that was deleted again after another edit or restore.
 */
internal class TransactionDeletionCoordinator(
    private val gateway: AppLedgerGateway,
) {
    private var lastDeletionToken = -1L

    suspend fun delete(
        transactionId: TransactionId,
        nowEpochMillis: Long,
    ): TransactionDeletionResult {
        require(nowEpochMillis >= 0)
        check(lastDeletionToken < Long.MAX_VALUE) { "Deletion token space exhausted" }
        val deletedAtEpochMillis = maxOf(nowEpochMillis, lastDeletionToken + 1)
        val deleted = gateway.softDeleteTransaction(transactionId, deletedAtEpochMillis)
        return if (deleted) {
            lastDeletionToken = deletedAtEpochMillis
            TransactionDeletionResult.Deleted(
                PendingTransactionUndo(
                    transactionId = transactionId,
                    deletedAtEpochMillis = deletedAtEpochMillis,
                    expiresAtEpochMillis = if (
                        nowEpochMillis > Long.MAX_VALUE - TRANSACTION_UNDO_WINDOW_MILLIS
                    ) {
                        Long.MAX_VALUE
                    } else {
                        nowEpochMillis + TRANSACTION_UNDO_WINDOW_MILLIS
                    },
                ),
            )
        } else {
            TransactionDeletionResult.Rejected
        }
    }

    suspend fun undo(
        pending: PendingTransactionUndo,
        nowEpochMillis: Long,
    ): Boolean {
        if (!pending.isAvailableAt(nowEpochMillis)) return false
        return gateway.restoreTransaction(
            id = pending.transactionId,
            expectedDeletedAtEpochMillis = pending.deletedAtEpochMillis,
        )
    }
}
