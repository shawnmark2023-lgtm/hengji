package com.hengji.data

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MarketQuote
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.UsageEvent
import kotlinx.serialization.Serializable

data class LedgerSnapshot(
    val revision: Long,
    val transactions: List<Transaction>,
    val assets: List<Asset>,
    val maintenanceCosts: List<MaintenanceCost>,
    val usageEvents: List<UsageEvent>,
    val marketQuotes: List<MarketQuote>,
    val insightPreferences: InsightPreferenceRecord = InsightPreferenceRecord(),
    val importBatches: List<ImportBatchRecord> = emptyList(),
)

data class InsightPreferenceRecord(
    val mutedTypes: Set<String> = emptySet(),
    val ignoredDeduplicationKeys: Set<String> = emptySet(),
    val updatedAtEpochMillis: Long = 0,
    val adoptedDeduplicationKeys: Set<String> = emptySet(),
    val snoozedUntilEpochMillisByKey: Map<String, Long> = emptyMap(),
    val feedbackTypeByKey: Map<String, String> = emptyMap(),
    val personalAiEnabled: Boolean = true,
    val onboardingCompletedAtEpochMillis: Long? = null,
    val personalAnalysisHistory: List<PersonalAnalysisRecord> = emptyList(),
) {
    init {
        require(mutedTypes.none { it.isBlank() }) { "Muted insight types cannot be blank" }
        require(ignoredDeduplicationKeys.none { it.isBlank() }) { "Ignored insight keys cannot be blank" }
        require(updatedAtEpochMillis >= 0) { "Preference update time cannot be negative" }
        require(adoptedDeduplicationKeys.none { it.isBlank() }) { "Adopted insight keys cannot be blank" }
        require(snoozedUntilEpochMillisByKey.keys.none { it.isBlank() }) { "Snoozed insight keys cannot be blank" }
        require(snoozedUntilEpochMillisByKey.values.none { it < 0 }) { "Snooze expiry cannot be negative" }
        require(feedbackTypeByKey.keys.none { it.isBlank() }) { "Feedback insight keys cannot be blank" }
        require(feedbackTypeByKey.values.none { it.isBlank() }) { "Feedback insight types cannot be blank" }
        require(onboardingCompletedAtEpochMillis == null || onboardingCompletedAtEpochMillis >= 0) {
            "Onboarding completion time cannot be negative"
        }
        require(personalAnalysisHistory.size <= 12) { "At most twelve personal analyses are retained" }
        require(personalAnalysisHistory.zipWithNext().all { (left, right) ->
            left.createdAtEpochMillis <= right.createdAtEpochMillis
        }) { "Personal analyses must be stored in chronological order" }
        require(adoptedDeduplicationKeys.intersect(ignoredDeduplicationKeys).isEmpty()) {
            "An insight cannot be both adopted and ignored"
        }
        require(adoptedDeduplicationKeys.intersect(snoozedUntilEpochMillisByKey.keys).isEmpty()) {
            "An insight cannot be both adopted and snoozed"
        }
        require(ignoredDeduplicationKeys.intersect(snoozedUntilEpochMillisByKey.keys).isEmpty()) {
            "An insight cannot be both ignored and snoozed"
        }
        require(
            feedbackTypeByKey.keys.all {
                it in adoptedDeduplicationKeys ||
                    it in ignoredDeduplicationKeys ||
                    it in snoozedUntilEpochMillisByKey
            },
        ) {
            "Feedback type metadata must reference a persisted feedback action"
        }
    }
}

/**
 * A bounded, local-only memory of model analyses. The protected ledger owns this record; it is
 * never treated as a financial fact and can be cleared independently of transaction history.
 */
@Serializable
data class PersonalAnalysisRecord(
    val createdAtEpochMillis: Long,
    val localDeduplicationKey: String,
    val headline: String,
    val summary: String,
    val actionLabel: String,
    val evidenceCodes: List<String>,
) {
    init {
        require(createdAtEpochMillis >= 0)
        require(localDeduplicationKey.isNotBlank() && localDeduplicationKey.length <= 200)
        require(headline.length in 1..80)
        require(summary.length in 1..500)
        require(actionLabel.length in 1..80)
        require(evidenceCodes.size in 1..8)
        require(evidenceCodes.all { it.matches(Regex("[a-z0-9._-]{1,80}")) })
    }
}

enum class ImportBatchState {
    COMMITTED,
    ROLLED_BACK,
}

data class ImportBatchItemRecord(
    val transactionId: String,
    val fingerprint: String,
) {
    init {
        require(transactionId.isNotBlank())
        require(fingerprint.isNotBlank())
    }
}

data class ImportBatchRecord(
    val batchId: String,
    val sourceConnectorId: String,
    val sourceDigest: String,
    val state: ImportBatchState,
    val createdAtEpochMillis: Long,
    val committedAtEpochMillis: Long,
    val rolledBackAtEpochMillis: Long? = null,
    val items: List<ImportBatchItemRecord> = emptyList(),
) {
    init {
        require(batchId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Invalid import batch id" }
        require(sourceConnectorId.isNotBlank() && sourceDigest.isNotBlank())
        require(createdAtEpochMillis >= 0 && committedAtEpochMillis >= createdAtEpochMillis)
        require(rolledBackAtEpochMillis == null || rolledBackAtEpochMillis >= committedAtEpochMillis)
        require((state == ImportBatchState.ROLLED_BACK) == (rolledBackAtEpochMillis != null))
        require(items.distinctBy { it.transactionId }.size == items.size)
        require(items.distinctBy { it.fingerprint }.size == items.size)
    }
}

data class CommitImportBatchRequest(
    val batchId: String,
    val sourceConnectorId: String,
    val sourceDigest: String,
    val createdAtEpochMillis: Long,
    val committedAtEpochMillis: Long,
    val transactions: List<Transaction>,
) {
    init {
        require(batchId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Invalid import batch id" }
        require(sourceConnectorId.isNotBlank() && sourceDigest.isNotBlank())
        require(createdAtEpochMillis >= 0 && committedAtEpochMillis >= createdAtEpochMillis)
        require(transactions.isNotEmpty())
        require(transactions.all { !it.importFingerprint.isNullOrBlank() })
        require(transactions.distinctBy { it.id }.size == transactions.size)
        require(transactions.distinctBy { it.importFingerprint }.size == transactions.size)
    }
}

enum class ImportBatchCommitStatus {
    COMMITTED,
    ALREADY_COMMITTED,
}

data class CommitImportBatchResult(
    val status: ImportBatchCommitStatus,
    val insertedTransactionIds: List<String>,
    val skippedFingerprints: List<String>,
    val revision: Long,
)

data class RollbackImportBatchResult(
    val alreadyRolledBack: Boolean,
    val removedTransactionIds: List<String>,
    val revision: Long,
)

enum class UpsertTransactionResult {
    INSERTED,
    UPDATED,
    DUPLICATE_IMPORT_SKIPPED,
}

/**
 * Local-first aggregate boundary. Implementations own atomicity and observation mechanics.
 * This synchronous contract keeps the domain portable; production SQLite adapters can wrap it in suspending use cases.
 */
interface LedgerRepository {
    fun snapshot(includeDeleted: Boolean = false): LedgerSnapshot

    fun upsertTransaction(transaction: Transaction): UpsertTransactionResult

    fun softDeleteTransaction(id: TransactionId, deletedAtEpochMillis: Long): Boolean

    fun restoreTransaction(id: TransactionId, expectedDeletedAtEpochMillis: Long): Boolean

    fun upsertAsset(asset: Asset)

    fun findAsset(id: AssetId): Asset?

    fun addMaintenanceCost(cost: MaintenanceCost)

    fun addUsageEvent(event: UsageEvent)

    fun addMarketQuote(quote: MarketQuote)

    fun saveInsightPreferences(preferences: InsightPreferenceRecord)

    fun replaceWith(snapshot: LedgerSnapshot)

    fun clear()
}

/**
 * Coroutine-first durable boundary used by Room KMP. Every method returns only after its SQLite transaction commits.
 * The synchronous [LedgerRepository] remains for previews and the current UI prototype.
 */
interface PersistentLedgerRepository {
    suspend fun snapshot(includeDeleted: Boolean = false): LedgerSnapshot
    suspend fun upsertTransaction(transaction: Transaction): UpsertTransactionResult
    suspend fun softDeleteTransaction(id: TransactionId, deletedAtEpochMillis: Long): Boolean
    suspend fun restoreTransaction(id: TransactionId, expectedDeletedAtEpochMillis: Long): Boolean
    suspend fun upsertAsset(asset: Asset)
    suspend fun findAsset(id: AssetId): Asset?
    suspend fun addMaintenanceCost(cost: MaintenanceCost)
    suspend fun addUsageEvent(event: UsageEvent)
    suspend fun addMarketQuote(quote: MarketQuote)
    suspend fun saveInsightPreferences(preferences: InsightPreferenceRecord)
    suspend fun commitImportBatch(request: CommitImportBatchRequest): CommitImportBatchResult
    suspend fun rollbackImportBatch(batchId: String, rolledBackAtEpochMillis: Long): RollbackImportBatchResult
    suspend fun replaceWith(snapshot: LedgerSnapshot)
    suspend fun clear()
}
