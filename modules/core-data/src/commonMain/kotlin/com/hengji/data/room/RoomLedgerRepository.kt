package com.hengji.data.room

import com.hengji.data.CommitImportBatchRequest
import com.hengji.data.CommitImportBatchResult
import com.hengji.data.ImportBatchCommitStatus
import com.hengji.data.ImportBatchState
import com.hengji.data.InsightPreferenceRecord
import com.hengji.data.LedgerSnapshot
import com.hengji.data.PersistentLedgerRepository
import com.hengji.data.RollbackImportBatchResult
import com.hengji.data.UpsertTransactionResult
import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MarketQuote
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.UsageEvent

class RoomLedgerRepository(
    private val database: HengjiDatabase,
) : PersistentLedgerRepository {
    private val dao: LedgerDao = database.ledgerDao()

    override suspend fun snapshot(includeDeleted: Boolean): LedgerSnapshot =
        dao.snapshotRows().toDomainSnapshot(includeDeleted)

    override suspend fun upsertTransaction(transaction: Transaction): UpsertTransactionResult {
        val (code) = dao.atomicUpsertTransaction(transaction.toRoomEntity())
        return when (code) {
            0 -> UpsertTransactionResult.INSERTED
            1 -> UpsertTransactionResult.UPDATED
            2 -> UpsertTransactionResult.DUPLICATE_IMPORT_SKIPPED
            else -> error("Unknown Room upsert result")
        }
    }

    override suspend fun softDeleteTransaction(id: TransactionId, deletedAtEpochMillis: Long): Boolean {
        require(deletedAtEpochMillis >= 0) { "Deletion time cannot be negative" }
        return dao.atomicSoftDeleteTransaction(id.value, deletedAtEpochMillis).first
    }

    override suspend fun upsertAsset(asset: Asset) {
        dao.atomicUpsertAsset(asset.toRoomEntity())
    }

    override suspend fun findAsset(id: AssetId): Asset? = dao.asset(id.value)?.toDomain()

    override suspend fun addMaintenanceCost(cost: MaintenanceCost) {
        dao.atomicUpsertMaintenanceCost(cost.toRoomEntity())
    }

    override suspend fun addUsageEvent(event: UsageEvent) {
        dao.atomicUpsertUsageEvent(event.toRoomEntity())
    }

    override suspend fun addMarketQuote(quote: MarketQuote) {
        dao.atomicUpsertMarketQuote(quote.toRoomEntity())
    }

    override suspend fun saveInsightPreferences(preferences: InsightPreferenceRecord) {
        dao.atomicSaveInsightPreferences(preferences.toRoomEntity())
    }

    override suspend fun commitImportBatch(request: CommitImportBatchRequest): CommitImportBatchResult {
        val roomResult = dao.atomicCommitImport(
            batch = ImportBatchEntity(
                batchId = request.batchId,
                sourceConnectorId = request.sourceConnectorId,
                sourceDigest = request.sourceDigest,
                state = ImportBatchState.COMMITTED.name,
                createdAtEpochMillis = request.createdAtEpochMillis,
                committedAtEpochMillis = request.committedAtEpochMillis,
                rolledBackAtEpochMillis = null,
            ),
            requestedTransactions = request.transactions.map(Transaction::toRoomEntity),
        )
        return CommitImportBatchResult(
            status = if (roomResult.alreadyCommitted) {
                ImportBatchCommitStatus.ALREADY_COMMITTED
            } else {
                ImportBatchCommitStatus.COMMITTED
            },
            insertedTransactionIds = roomResult.inserted.map { it.id },
            skippedFingerprints = roomResult.skippedFingerprints,
            revision = roomResult.revision,
        )
    }

    override suspend fun rollbackImportBatch(
        batchId: String,
        rolledBackAtEpochMillis: Long,
    ): RollbackImportBatchResult {
        require(batchId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Invalid import batch id" }
        require(rolledBackAtEpochMillis >= 0) { "Rollback time cannot be negative" }
        val result = dao.atomicRollbackImport(batchId, rolledBackAtEpochMillis)
        return RollbackImportBatchResult(
            alreadyRolledBack = result.alreadyRolledBack,
            removedTransactionIds = result.removedTransactionIds,
            revision = result.revision,
        )
    }

    override suspend fun replaceWith(snapshot: LedgerSnapshot) {
        validateSnapshot(snapshot)
        dao.atomicReplace(snapshot.toRoomRows(checkedNext(snapshot.revision)))
    }

    override suspend fun clear() {
        dao.atomicClear()
    }

    fun close() {
        database.close()
    }
}

private fun validateSnapshot(snapshot: LedgerSnapshot) {
    require(snapshot.revision >= 0) { "Ledger revision cannot be negative" }
    val assetIds = snapshot.assets.mapTo(mutableSetOf()) { it.id }
    val transactionIds = snapshot.transactions.mapTo(mutableSetOf()) { it.id.value }
    require(assetIds.size == snapshot.assets.size) { "Duplicate asset ids" }
    require(transactionIds.size == snapshot.transactions.size) { "Duplicate transaction ids" }
    require(snapshot.transactions.mapNotNull { it.importFingerprint }.distinct().size ==
        snapshot.transactions.mapNotNull { it.importFingerprint }.size
    ) { "Duplicate import fingerprints" }
    require(snapshot.maintenanceCosts.all { it.assetId in assetIds }) { "Maintenance references an unknown asset" }
    require(snapshot.usageEvents.all { it.assetId in assetIds }) { "Usage references an unknown asset" }
    require(snapshot.marketQuotes.all { it.assetId in assetIds }) { "Quote references an unknown asset" }
    snapshot.importBatches.forEach { batch ->
        if (batch.state == ImportBatchState.COMMITTED) {
            require(batch.items.all { it.transactionId in transactionIds }) { "Import batch references an unknown transaction" }
        }
    }
}

private fun checkedNext(value: Long): Long {
    if (value == Long.MAX_VALUE) throw ArithmeticException("Ledger revision overflow")
    return value + 1
}
