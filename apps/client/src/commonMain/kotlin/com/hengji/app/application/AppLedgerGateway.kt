package com.hengji.app.application

import com.hengji.data.CommitImportBatchRequest
import com.hengji.data.CommitImportBatchResult
import com.hengji.data.ImportBatchCommitStatus
import com.hengji.data.ImportBatchItemRecord
import com.hengji.data.ImportBatchRecord
import com.hengji.data.ImportBatchState
import com.hengji.data.InsightPreferenceRecord
import com.hengji.data.LedgerRepository
import com.hengji.data.LedgerSnapshot
import com.hengji.data.PersistentLedgerRepository
import com.hengji.data.RollbackImportBatchResult
import com.hengji.data.UpsertTransactionResult
import com.hengji.domain.Asset
import com.hengji.domain.MarketQuote
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.UsageEvent

/** Application-facing coroutine boundary. Compose never calls SQLite or blocking filesystem APIs directly. */
interface AppLedgerGateway {
    suspend fun snapshot(includeDeleted: Boolean = false): LedgerSnapshot
    suspend fun upsertTransaction(transaction: Transaction): UpsertTransactionResult
    suspend fun softDeleteTransaction(id: TransactionId, deletedAtEpochMillis: Long): Boolean
    suspend fun upsertAsset(asset: Asset)
    suspend fun addUsageEvent(event: UsageEvent)
    suspend fun addMarketQuote(quote: MarketQuote)
    suspend fun saveInsightPreferences(preferences: InsightPreferenceRecord)
    suspend fun commitImportBatch(request: CommitImportBatchRequest): CommitImportBatchResult
    suspend fun rollbackImportBatch(batchId: String, rolledBackAtEpochMillis: Long): RollbackImportBatchResult
    suspend fun replaceWith(snapshot: LedgerSnapshot)
    suspend fun clear()
}

class PreviewLedgerGateway(
    private val repository: LedgerRepository,
) : AppLedgerGateway {
    override suspend fun snapshot(includeDeleted: Boolean): LedgerSnapshot = repository.snapshot(includeDeleted)

    override suspend fun upsertTransaction(transaction: Transaction): UpsertTransactionResult =
        repository.upsertTransaction(transaction)

    override suspend fun softDeleteTransaction(id: TransactionId, deletedAtEpochMillis: Long): Boolean =
        repository.softDeleteTransaction(id, deletedAtEpochMillis)

    override suspend fun upsertAsset(asset: Asset) = repository.upsertAsset(asset)
    override suspend fun addUsageEvent(event: UsageEvent) = repository.addUsageEvent(event)
    override suspend fun addMarketQuote(quote: MarketQuote) = repository.addMarketQuote(quote)
    override suspend fun saveInsightPreferences(preferences: InsightPreferenceRecord) =
        repository.saveInsightPreferences(preferences)

    override suspend fun commitImportBatch(request: CommitImportBatchRequest): CommitImportBatchResult {
        val current = repository.snapshot(includeDeleted = true)
        val prior = current.importBatches.firstOrNull { it.batchId == request.batchId }
        if (prior != null) {
            require(prior.state == ImportBatchState.COMMITTED) { "A rolled-back batch id cannot be reused" }
            return CommitImportBatchResult(
                status = ImportBatchCommitStatus.ALREADY_COMMITTED,
                insertedTransactionIds = prior.items.map { it.transactionId },
                skippedFingerprints = emptyList(),
                revision = current.revision,
            )
        }

        val existingFingerprints = current.transactions.mapNotNullTo(mutableSetOf()) { it.importFingerprint }
        val accepted = request.transactions.filter { it.importFingerprint !in existingFingerprints }
        val skipped = request.transactions.mapNotNull { it.importFingerprint }.filter(existingFingerprints::contains)
        val batch = ImportBatchRecord(
            batchId = request.batchId,
            sourceConnectorId = request.sourceConnectorId,
            sourceDigest = request.sourceDigest,
            state = ImportBatchState.COMMITTED,
            createdAtEpochMillis = request.createdAtEpochMillis,
            committedAtEpochMillis = request.committedAtEpochMillis,
            items = accepted.map {
                ImportBatchItemRecord(it.id.value, requireNotNull(it.importFingerprint))
            },
        )
        repository.replaceWith(
            current.copy(
                transactions = current.transactions + accepted,
                importBatches = current.importBatches + batch,
            ),
        )
        return CommitImportBatchResult(
            status = ImportBatchCommitStatus.COMMITTED,
            insertedTransactionIds = accepted.map { it.id.value },
            skippedFingerprints = skipped,
            revision = repository.snapshot().revision,
        )
    }

    override suspend fun rollbackImportBatch(
        batchId: String,
        rolledBackAtEpochMillis: Long,
    ): RollbackImportBatchResult {
        val current = repository.snapshot(includeDeleted = true)
        val batch = requireNotNull(current.importBatches.firstOrNull { it.batchId == batchId }) { "Unknown batch id" }
        if (batch.state == ImportBatchState.ROLLED_BACK) {
            return RollbackImportBatchResult(true, emptyList(), current.revision)
        }
        val ids = batch.items.mapTo(mutableSetOf()) { it.transactionId }
        val rolledBack = batch.copy(
            state = ImportBatchState.ROLLED_BACK,
            rolledBackAtEpochMillis = rolledBackAtEpochMillis,
        )
        repository.replaceWith(
            current.copy(
                transactions = current.transactions.filterNot { it.id.value in ids },
                importBatches = current.importBatches.map { if (it.batchId == batchId) rolledBack else it },
            ),
        )
        return RollbackImportBatchResult(false, ids.toList(), repository.snapshot().revision)
    }

    override suspend fun replaceWith(snapshot: LedgerSnapshot) = repository.replaceWith(snapshot)
    override suspend fun clear() = repository.clear()
}

class PersistentAppLedgerGateway(
    private val repository: PersistentLedgerRepository,
) : AppLedgerGateway {
    override suspend fun snapshot(includeDeleted: Boolean): LedgerSnapshot = repository.snapshot(includeDeleted)
    override suspend fun upsertTransaction(transaction: Transaction): UpsertTransactionResult = repository.upsertTransaction(transaction)
    override suspend fun softDeleteTransaction(id: TransactionId, deletedAtEpochMillis: Long): Boolean =
        repository.softDeleteTransaction(id, deletedAtEpochMillis)
    override suspend fun upsertAsset(asset: Asset) = repository.upsertAsset(asset)
    override suspend fun addUsageEvent(event: UsageEvent) = repository.addUsageEvent(event)
    override suspend fun addMarketQuote(quote: MarketQuote) = repository.addMarketQuote(quote)
    override suspend fun saveInsightPreferences(preferences: InsightPreferenceRecord) =
        repository.saveInsightPreferences(preferences)
    override suspend fun commitImportBatch(request: CommitImportBatchRequest): CommitImportBatchResult =
        repository.commitImportBatch(request)
    override suspend fun rollbackImportBatch(batchId: String, rolledBackAtEpochMillis: Long): RollbackImportBatchResult =
        repository.rollbackImportBatch(batchId, rolledBackAtEpochMillis)
    override suspend fun replaceWith(snapshot: LedgerSnapshot) = repository.replaceWith(snapshot)
    override suspend fun clear() = repository.clear()
}
