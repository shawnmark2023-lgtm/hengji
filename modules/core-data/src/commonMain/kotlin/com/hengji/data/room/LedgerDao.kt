package com.hengji.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

data class RoomSnapshotRows(
    val revision: Long,
    val transactions: List<TransactionEntity>,
    val assets: List<AssetEntity>,
    val maintenanceCosts: List<MaintenanceCostEntity>,
    val usageEvents: List<UsageEventEntity>,
    val marketQuotes: List<MarketQuoteEntity>,
    val insightPreferences: InsightPreferencesEntity?,
    val importBatches: List<ImportBatchEntity>,
    val importItems: List<ImportBatchItemEntity>,
)

data class RoomCommitImportResult(
    val alreadyCommitted: Boolean,
    val inserted: List<TransactionEntity>,
    val skippedFingerprints: List<String>,
    val revision: Long,
)

data class RoomRollbackImportResult(
    val alreadyRolledBack: Boolean,
    val removedTransactionIds: List<String>,
    val revision: Long,
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_metadata WHERE singletonId = 1")
    suspend fun metadata(): LedgerMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initializeMetadata(metadata: LedgerMetadataEntity): Long

    @Query("UPDATE ledger_metadata SET revision = :revision WHERE singletonId = 1")
    suspend fun setRevision(revision: Long)

    @Query("SELECT * FROM transactions ORDER BY bookedOn, id")
    suspend fun transactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transaction(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE importFingerprint IN (:fingerprints)")
    suspend fun transactionsByFingerprints(fingerprints: List<String>): List<TransactionEntity>

    @Upsert
    suspend fun upsertTransaction(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(entities: List<TransactionEntity>)

    @Query("UPDATE transactions SET deletedAtEpochMillis = :deletedAt WHERE id = :id AND deletedAtEpochMillis IS NULL")
    suspend fun softDeleteTransaction(id: String, deletedAt: Long): Int

    @Query("UPDATE transactions SET deletedAtEpochMillis = NULL WHERE id = :id AND deletedAtEpochMillis = :expectedDeletedAt")
    suspend fun restoreTransaction(id: String, expectedDeletedAt: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE originalTransactionId = :originalId
          AND kind = 'REFUND'
          AND deletedAtEpochMillis IS NULL
        """,
    )
    suspend fun activeRefundCount(originalId: String): Int

    @Query(
        """
        SELECT * FROM transactions
        WHERE kind = 'REFUND'
          AND deletedAtEpochMillis IS NULL
          AND originalTransactionId IN (:originalIds)
        """,
    )
    suspend fun activeRefundsForOriginals(originalIds: List<String>): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactions(ids: List<String>): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT * FROM assets ORDER BY purchasedOn, id")
    suspend fun assets(): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun asset(id: String): AssetEntity?

    @Upsert
    suspend fun upsertAsset(entity: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(entities: List<AssetEntity>)

    @Query("DELETE FROM assets")
    suspend fun deleteAllAssets()

    @Query("SELECT * FROM maintenance_costs ORDER BY occurredOn, id")
    suspend fun maintenanceCosts(): List<MaintenanceCostEntity>

    @Upsert
    suspend fun upsertMaintenanceCost(entity: MaintenanceCostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceCosts(entities: List<MaintenanceCostEntity>)

    @Query("DELETE FROM maintenance_costs")
    suspend fun deleteAllMaintenanceCosts()

    @Query("SELECT * FROM usage_events ORDER BY occurredOn, id")
    suspend fun usageEvents(): List<UsageEventEntity>

    @Upsert
    suspend fun upsertUsageEvent(entity: UsageEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageEvents(entities: List<UsageEventEntity>)

    @Query("DELETE FROM usage_events")
    suspend fun deleteAllUsageEvents()

    @Query("SELECT * FROM market_quotes ORDER BY collectedOn, id")
    suspend fun marketQuotes(): List<MarketQuoteEntity>

    @Query("SELECT * FROM market_quotes WHERE assetId = :assetId")
    suspend fun marketQuotesForAsset(assetId: String): List<MarketQuoteEntity>

    @Upsert
    suspend fun upsertMarketQuote(entity: MarketQuoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarketQuotes(entities: List<MarketQuoteEntity>)

    @Query("DELETE FROM market_quotes")
    suspend fun deleteAllMarketQuotes()

    @Query("SELECT * FROM insight_preferences WHERE singletonId = 1")
    suspend fun insightPreferences(): InsightPreferencesEntity?

    @Upsert
    suspend fun upsertInsightPreferences(entity: InsightPreferencesEntity)

    @Query("DELETE FROM insight_preferences")
    suspend fun deleteInsightPreferences()

    @Query("SELECT * FROM import_batches ORDER BY createdAtEpochMillis, batchId")
    suspend fun importBatches(): List<ImportBatchEntity>

    @Query("SELECT * FROM import_batches WHERE batchId = :batchId")
    suspend fun importBatch(batchId: String): ImportBatchEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImportBatch(entity: ImportBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportBatches(entities: List<ImportBatchEntity>)

    @Query("UPDATE import_batches SET state = 'ROLLED_BACK', rolledBackAtEpochMillis = :rolledBackAt WHERE batchId = :batchId AND state = 'COMMITTED'")
    suspend fun markImportBatchRolledBack(batchId: String, rolledBackAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImportBatchItems(entities: List<ImportBatchItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreImportBatchItems(entities: List<ImportBatchItemEntity>)

    @Query("SELECT * FROM import_batch_items ORDER BY batchId, transactionId")
    suspend fun importBatchItems(): List<ImportBatchItemEntity>

    @Query("SELECT * FROM import_batch_items WHERE batchId = :batchId ORDER BY transactionId")
    suspend fun importBatchItems(batchId: String): List<ImportBatchItemEntity>

    @Query("DELETE FROM import_batch_items")
    suspend fun deleteAllImportBatchItems()

    @Query("DELETE FROM import_batches")
    suspend fun deleteAllImportBatches()

    @Transaction
    suspend fun ensureInitialized() {
        initializeMetadata(LedgerMetadataEntity(revision = 0))
    }

    @Transaction
    suspend fun snapshotRows(): RoomSnapshotRows {
        ensureInitialized()
        return RoomSnapshotRows(
            revision = requireNotNull(metadata()).revision,
            transactions = transactions(),
            assets = assets(),
            maintenanceCosts = maintenanceCosts(),
            usageEvents = usageEvents(),
            marketQuotes = marketQuotes(),
            insightPreferences = insightPreferences(),
            importBatches = importBatches(),
            importItems = importBatchItems(),
        )
    }

    suspend fun bumpRevision(): Long {
        ensureInitialized()
        val current = requireNotNull(metadata()).revision
        if (current == Long.MAX_VALUE) throw ArithmeticException("Ledger revision overflow")
        val next = current + 1
        setRevision(next)
        return next
    }

    @Transaction
    suspend fun atomicUpsertTransaction(entity: TransactionEntity): Pair<Int, Long> {
        ensureInitialized()
        val duplicate = entity.importFingerprint?.let { transactionsByFingerprints(listOf(it)).firstOrNull() }
        if (duplicate != null && duplicate.id != entity.id) {
            return 2 to requireNotNull(metadata()).revision
        }
        val existed = transaction(entity.id) != null
        upsertTransaction(entity)
        return (if (existed) 1 else 0) to bumpRevision()
    }

    @Transaction
    suspend fun atomicSoftDeleteTransaction(id: String, deletedAt: Long): Pair<Boolean, Long> {
        ensureInitialized()
        if (activeRefundCount(id) > 0) {
            return false to requireNotNull(metadata()).revision
        }
        val changed = softDeleteTransaction(id, deletedAt) == 1
        return changed to if (changed) bumpRevision() else requireNotNull(metadata()).revision
    }

    @Transaction
    suspend fun atomicRestoreTransaction(id: String, expectedDeletedAt: Long): Pair<Boolean, Long> {
        ensureInitialized()
        val existing = transaction(id)
        if (existing?.deletedAtEpochMillis != expectedDeletedAt) {
            return false to requireNotNull(metadata()).revision
        }
        val fingerprintConflict = existing.importFingerprint?.let { fingerprint ->
            transactionsByFingerprints(listOf(fingerprint)).any {
                it.id != id && it.deletedAtEpochMillis == null
            }
        } ?: false
        val originalUnavailable = existing.originalTransactionId?.let { originalId ->
            transaction(originalId)?.let { it.deletedAtEpochMillis != null } ?: true
        } ?: false
        if (fingerprintConflict || originalUnavailable) {
            return false to requireNotNull(metadata()).revision
        }
        val changed = restoreTransaction(id, expectedDeletedAt) == 1
        return changed to if (changed) bumpRevision() else requireNotNull(metadata()).revision
    }

    @Transaction
    suspend fun atomicUpsertAsset(entity: AssetEntity): Long {
        require(marketQuotesForAsset(entity.id).all { it.currency == entity.currency }) {
            "Existing quotes must use the asset purchase currency"
        }
        upsertAsset(entity)
        return bumpRevision()
    }

    @Transaction
    suspend fun atomicUpsertMaintenanceCost(entity: MaintenanceCostEntity): Long {
        requireNotNull(asset(entity.assetId)) { "Cannot add maintenance cost for an unknown asset" }
        upsertMaintenanceCost(entity)
        return bumpRevision()
    }

    @Transaction
    suspend fun atomicUpsertUsageEvent(entity: UsageEventEntity): Long {
        requireNotNull(asset(entity.assetId)) { "Cannot add usage for an unknown asset" }
        upsertUsageEvent(entity)
        return bumpRevision()
    }

    @Transaction
    suspend fun atomicUpsertMarketQuote(entity: MarketQuoteEntity): Long {
        val referencedAsset = requireNotNull(asset(entity.assetId)) { "Cannot add quote for an unknown asset" }
        require(entity.currency == referencedAsset.currency) {
            "Quote must use the asset purchase currency"
        }
        upsertMarketQuote(entity)
        return bumpRevision()
    }

    @Transaction
    suspend fun atomicSaveInsightPreferences(entity: InsightPreferencesEntity): Long {
        upsertInsightPreferences(entity)
        return bumpRevision()
    }

    @Transaction
    suspend fun atomicCommitImport(
        batch: ImportBatchEntity,
        requestedTransactions: List<TransactionEntity>,
    ): RoomCommitImportResult {
        ensureInitialized()
        val existingBatch = importBatch(batch.batchId)
        if (existingBatch != null) {
            val sameRequest = existingBatch.sourceConnectorId == batch.sourceConnectorId &&
                existingBatch.sourceDigest == batch.sourceDigest &&
                existingBatch.state == "COMMITTED"
            require(sameRequest) { "Import batch id conflicts with a different or rolled-back batch" }
            val items = importBatchItems(batch.batchId)
            return RoomCommitImportResult(
                alreadyCommitted = true,
                inserted = emptyList(),
                skippedFingerprints = items.map { it.fingerprint },
                revision = requireNotNull(metadata()).revision,
            )
        }
        val requestedFingerprints = requestedTransactions.map { requireNotNull(it.importFingerprint) }
        val existingFingerprints = transactionsByFingerprints(requestedFingerprints).mapNotNullTo(mutableSetOf()) { it.importFingerprint }
        val inserted = requestedTransactions.filter { it.importFingerprint !in existingFingerprints }
        insertImportBatch(batch)
        if (inserted.isNotEmpty()) {
            insertTransactions(inserted)
            insertImportBatchItems(inserted.map {
                ImportBatchItemEntity(batch.batchId, it.id, requireNotNull(it.importFingerprint))
            })
        }
        return RoomCommitImportResult(
            alreadyCommitted = false,
            inserted = inserted,
            skippedFingerprints = requestedFingerprints.filter { it in existingFingerprints },
            revision = bumpRevision(),
        )
    }

    @Transaction
    suspend fun atomicRollbackImport(batchId: String, rolledBackAt: Long): RoomRollbackImportResult {
        ensureInitialized()
        val batch = requireNotNull(importBatch(batchId)) { "Unknown import batch id" }
        if (batch.state == "ROLLED_BACK") {
            return RoomRollbackImportResult(true, emptyList(), requireNotNull(metadata()).revision)
        }
        require(batch.state == "COMMITTED") { "Import batch is not committed" }
        require(rolledBackAt >= batch.committedAtEpochMillis) { "Rollback cannot precede commit" }
        val transactionIds = importBatchItems(batchId).map { it.transactionId }
        val transactionIdSet = transactionIds.toSet()
        if (transactionIds.isNotEmpty()) {
            require(
                activeRefundsForOriginals(transactionIds).none { it.id !in transactionIdSet },
            ) { "Import rollback would orphan an active refund" }
        }
        if (transactionIds.isNotEmpty()) deleteTransactions(transactionIds)
        check(markImportBatchRolledBack(batchId, rolledBackAt) == 1)
        return RoomRollbackImportResult(false, transactionIds, bumpRevision())
    }

    @Transaction
    suspend fun atomicReplace(rows: RoomSnapshotRows) {
        ensureInitialized()
        val currentRevision = requireNotNull(metadata()).revision
        val baseRevision = maxOf(currentRevision, rows.revision)
        if (baseRevision == Long.MAX_VALUE) throw ArithmeticException("Ledger revision overflow")
        val replacementRevision = baseRevision + 1
        deleteAllImportBatchItems()
        deleteAllImportBatches()
        deleteAllMarketQuotes()
        deleteAllUsageEvents()
        deleteAllMaintenanceCosts()
        deleteAllTransactions()
        deleteAllAssets()
        deleteInsightPreferences()
        if (rows.assets.isNotEmpty()) insertAssets(rows.assets)
        if (rows.transactions.isNotEmpty()) insertTransactions(rows.transactions)
        if (rows.maintenanceCosts.isNotEmpty()) insertMaintenanceCosts(rows.maintenanceCosts)
        if (rows.usageEvents.isNotEmpty()) insertUsageEvents(rows.usageEvents)
        if (rows.marketQuotes.isNotEmpty()) insertMarketQuotes(rows.marketQuotes)
        rows.insightPreferences?.let { upsertInsightPreferences(it) }
        if (rows.importBatches.isNotEmpty()) insertImportBatches(rows.importBatches)
        if (rows.importItems.isNotEmpty()) restoreImportBatchItems(rows.importItems)
        setRevision(replacementRevision)
    }

    @Transaction
    suspend fun atomicClear(): Long {
        deleteAllImportBatchItems()
        deleteAllImportBatches()
        deleteAllMarketQuotes()
        deleteAllUsageEvents()
        deleteAllMaintenanceCosts()
        deleteAllTransactions()
        deleteAllAssets()
        deleteInsightPreferences()
        return bumpRevision()
    }
}
