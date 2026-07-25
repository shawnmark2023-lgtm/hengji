package com.hengji.data

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MarketQuote
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.UsageEvent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Durable storage for an authenticated encrypted ledger envelope.
 *
 * Implementations must make [compareAndSwap] durable before returning and must never expose a
 * partially written replacement. The expected value is compared byte-for-byte so concurrent
 * processes cannot silently overwrite each other's ledger revision.
 */
interface ProtectedLedgerStore {
    suspend fun readEnvelope(): String?

    suspend fun compareAndSwap(
        expectedEnvelope: String?,
        replacementEnvelope: String,
    ): Boolean
}

/**
 * A plaintext source that is kept behind the migration startup gate.
 *
 * [retireAfterVerifiedMigration] is called only after the encrypted target was read back,
 * authenticated, and found equal to [snapshotForMigration]. A failure must leave the source
 * recoverable so the migration can be retried on the next launch.
 */
interface PlaintextLedgerMigrationSource {
    /**
     * Returns null only when a previous launch already made the plaintext database unavailable
     * and this launch only needs to finish deleting its retirement artifacts.
     */
    suspend fun snapshotForMigration(): LedgerSnapshot?

    suspend fun retireAfterVerifiedMigration()
}

enum class ProtectedLedgerOpenOutcome {
    OPENED_EXISTING,
    CREATED_EMPTY,
    MIGRATED_PLAINTEXT,
    COMPLETED_INTERRUPTED_INITIALIZATION,
    COMPLETED_INTERRUPTED_MIGRATION,
}

data class ProtectedLedgerOpenResult(
    val repository: ProtectedLedgerRepository,
    val outcome: ProtectedLedgerOpenOutcome,
)

class ConcurrentLedgerWriteException :
    StorageProtectionException("The encrypted ledger changed in another process; retry the operation")

class PlaintextLedgerMigrationConflictException :
    StorageProtectionException(
        "Encrypted and plaintext ledgers differ; the plaintext source was retained for manual recovery",
    )

/**
 * Copy-on-write encrypted ledger repository.
 *
 * A mutation is published to memory only after authenticated encryption and an atomic storage
 * compare-and-swap succeed. Reads refresh from storage, making external process changes visible.
 */
class ProtectedLedgerRepository private constructor(
    private val store: ProtectedLedgerStore,
    private val codec: ProtectedLedgerPayloadCodec,
    initialEnvelope: String,
    initialSnapshot: LedgerSnapshot,
) : PersistentLedgerRepository {
    private val mutex = Mutex()
    private var currentEnvelope: String = initialEnvelope
    private var currentSnapshot: LedgerSnapshot = initialSnapshot

    override suspend fun snapshot(includeDeleted: Boolean): LedgerSnapshot = mutex.withLock {
        refreshLocked()
        currentSnapshot.visible(includeDeleted)
    }

    override suspend fun upsertTransaction(transaction: Transaction): UpsertTransactionResult =
        mutate { current ->
            val duplicate = transaction.importFingerprint?.let { fingerprint ->
                current.transactions.firstOrNull {
                    it.id != transaction.id && it.importFingerprint == fingerprint
                }
            }
            if (duplicate != null) {
                current to UpsertTransactionResult.DUPLICATE_IMPORT_SKIPPED
            } else {
                val existed = current.transactions.any { it.id == transaction.id }
                val updated = current.transactions
                    .filterNot { it.id == transaction.id }
                    .plus(transaction)
                current.copy(
                    revision = checkedNextRevision(current.revision),
                    transactions = updated,
                ) to if (existed) UpsertTransactionResult.UPDATED else UpsertTransactionResult.INSERTED
            }
        }

    override suspend fun softDeleteTransaction(
        id: TransactionId,
        deletedAtEpochMillis: Long,
    ): Boolean {
        require(deletedAtEpochMillis >= 0) { "Deletion time cannot be negative" }
        return mutate { current ->
            val existing = current.transactions.firstOrNull { it.id == id }
            val hasActiveRefund = current.transactions.any {
                !it.isDeleted && it.originalTransactionId == id
            }
            if (existing == null || existing.isDeleted || hasActiveRefund) {
                current to false
            } else {
                current.copy(
                    revision = checkedNextRevision(current.revision),
                    transactions = current.transactions.map {
                        if (it.id == id) it.copy(deletedAtEpochMillis = deletedAtEpochMillis) else it
                    },
                ) to true
            }
        }
    }

    override suspend fun restoreTransaction(
        id: TransactionId,
        expectedDeletedAtEpochMillis: Long,
    ): Boolean {
        require(expectedDeletedAtEpochMillis >= 0) { "Expected deletion time cannot be negative" }
        return mutate { current ->
            val existing = current.transactions.firstOrNull { it.id == id }
            val fingerprintConflict = existing?.importFingerprint?.let { fingerprint ->
                current.transactions.any {
                    it.id != id && !it.isDeleted && it.importFingerprint == fingerprint
                }
            } ?: false
            val originalUnavailable = existing?.originalTransactionId?.let { originalId ->
                current.transactions.none { it.id == originalId && !it.isDeleted }
            } ?: false
            if (
                existing?.deletedAtEpochMillis != expectedDeletedAtEpochMillis ||
                fingerprintConflict ||
                originalUnavailable
            ) {
                current to false
            } else {
                current.copy(
                    revision = checkedNextRevision(current.revision),
                    transactions = current.transactions.map {
                        if (it.id == id) it.copy(deletedAtEpochMillis = null) else it
                    },
                ) to true
            }
        }
    }

    override suspend fun upsertAsset(asset: Asset) {
        mutate<Unit> { current ->
            require(current.marketQuotes.filter { it.assetId == asset.id }.all {
                it.price.currency == asset.purchasePrice.currency
            }) { "Existing quotes must use the asset purchase currency" }
            current.copy(
                revision = checkedNextRevision(current.revision),
                assets = current.assets.filterNot { it.id == asset.id } + asset,
            ) to Unit
        }
    }

    override suspend fun findAsset(id: AssetId): Asset? = mutex.withLock {
        refreshLocked()
        currentSnapshot.assets.firstOrNull { it.id == id }
    }

    override suspend fun addMaintenanceCost(cost: MaintenanceCost) {
        mutate<Unit> { current ->
            require(current.assets.any { it.id == cost.assetId }) {
                "Cannot add maintenance cost for an unknown asset"
            }
            current.copy(
                revision = checkedNextRevision(current.revision),
                maintenanceCosts = current.maintenanceCosts.filterNot { it.id == cost.id } + cost,
            ) to Unit
        }
    }

    override suspend fun addUsageEvent(event: UsageEvent) {
        mutate<Unit> { current ->
            require(current.assets.any { it.id == event.assetId }) { "Cannot add usage for an unknown asset" }
            current.copy(
                revision = checkedNextRevision(current.revision),
                usageEvents = current.usageEvents.filterNot { it.id == event.id } + event,
            ) to Unit
        }
    }

    override suspend fun addMarketQuote(quote: MarketQuote) {
        mutate<Unit> { current ->
            val asset = requireNotNull(current.assets.firstOrNull { it.id == quote.assetId }) {
                "Cannot add a quote for an unknown asset"
            }
            require(quote.price.currency == asset.purchasePrice.currency) {
                "Quote must use the asset purchase currency"
            }
            current.copy(
                revision = checkedNextRevision(current.revision),
                marketQuotes = current.marketQuotes.filterNot { it.id == quote.id } + quote,
            ) to Unit
        }
    }

    override suspend fun saveInsightPreferences(preferences: InsightPreferenceRecord) {
        mutate<Unit> { current ->
            current.copy(
                revision = checkedNextRevision(current.revision),
                insightPreferences = preferences,
            ) to Unit
        }
    }

    override suspend fun commitImportBatch(request: CommitImportBatchRequest): CommitImportBatchResult =
        mutate { current ->
            val existing = current.importBatches.firstOrNull { it.batchId == request.batchId }
            if (existing != null) {
                require(
                    existing.sourceConnectorId == request.sourceConnectorId &&
                        existing.sourceDigest == request.sourceDigest &&
                        existing.state == ImportBatchState.COMMITTED,
                ) { "Import batch id conflicts with a different or rolled-back batch" }
                current to CommitImportBatchResult(
                    status = ImportBatchCommitStatus.ALREADY_COMMITTED,
                    insertedTransactionIds = emptyList(),
                    skippedFingerprints = existing.items.map { it.fingerprint },
                    revision = current.revision,
                )
            } else {
                val existingFingerprints = current.transactions
                    .mapNotNullTo(mutableSetOf()) { it.importFingerprint }
                val inserted = request.transactions.filter { it.importFingerprint !in existingFingerprints }
                val skipped = request.transactions
                    .map { requireNotNull(it.importFingerprint) }
                    .filter { it in existingFingerprints }
                val nextRevision = checkedNextRevision(current.revision)
                val batch = ImportBatchRecord(
                    batchId = request.batchId,
                    sourceConnectorId = request.sourceConnectorId,
                    sourceDigest = request.sourceDigest,
                    state = ImportBatchState.COMMITTED,
                    createdAtEpochMillis = request.createdAtEpochMillis,
                    committedAtEpochMillis = request.committedAtEpochMillis,
                    items = inserted.map {
                        ImportBatchItemRecord(it.id.value, requireNotNull(it.importFingerprint))
                    },
                )
                current.copy(
                    revision = nextRevision,
                    transactions = current.transactions + inserted,
                    importBatches = current.importBatches + batch,
                ) to CommitImportBatchResult(
                    status = ImportBatchCommitStatus.COMMITTED,
                    insertedTransactionIds = inserted.map { it.id.value },
                    skippedFingerprints = skipped,
                    revision = nextRevision,
                )
            }
        }

    override suspend fun rollbackImportBatch(
        batchId: String,
        rolledBackAtEpochMillis: Long,
    ): RollbackImportBatchResult {
        require(batchId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Invalid import batch id" }
        require(rolledBackAtEpochMillis >= 0) { "Rollback time cannot be negative" }
        return mutate { current ->
            val batch = requireNotNull(current.importBatches.firstOrNull { it.batchId == batchId }) {
                "Unknown import batch id"
            }
            if (batch.state == ImportBatchState.ROLLED_BACK) {
                current to RollbackImportBatchResult(true, emptyList(), current.revision)
            } else {
                require(rolledBackAtEpochMillis >= batch.committedAtEpochMillis) {
                    "Rollback cannot precede commit"
                }
                val removedIds = batch.items.map { it.transactionId }
                val removedIdSet = removedIds.toSet()
                require(
                    current.transactions.none {
                        !it.isDeleted &&
                            it.id.value !in removedIdSet &&
                            it.originalTransactionId?.value in removedIdSet
                    },
                ) { "Import rollback would orphan an active refund" }
                val nextRevision = checkedNextRevision(current.revision)
                current.copy(
                    revision = nextRevision,
                    transactions = current.transactions.filterNot { it.id.value in removedIds },
                    importBatches = current.importBatches.map {
                        if (it.batchId == batchId) {
                            it.copy(
                                state = ImportBatchState.ROLLED_BACK,
                                rolledBackAtEpochMillis = rolledBackAtEpochMillis,
                            )
                        } else {
                            it
                        }
                    },
                ) to RollbackImportBatchResult(false, removedIds, nextRevision)
            }
        }
    }

    override suspend fun replaceWith(snapshot: LedgerSnapshot) {
        validateLedgerSnapshot(snapshot)
        mutate<Unit> { current ->
            snapshot.copy(
                revision = checkedNextRevision(maxOf(current.revision, snapshot.revision)),
            ) to Unit
        }
    }

    override suspend fun clear() {
        mutate<Unit> { current ->
            LedgerSnapshot(
                revision = checkedNextRevision(current.revision),
                transactions = emptyList(),
                assets = emptyList(),
                maintenanceCosts = emptyList(),
                usageEvents = emptyList(),
                marketQuotes = emptyList(),
            ) to Unit
        }
    }

    private suspend fun <T> mutate(
        transform: (LedgerSnapshot) -> Pair<LedgerSnapshot, T>,
    ): T = mutex.withLock {
        refreshLocked()
        val (replacement, result) = transform(currentSnapshot)
        if (replacement == currentSnapshot) return@withLock result
        validateLedgerSnapshot(replacement)
        val replacementEnvelope = codec.exportEnvelope(replacement)
        val committed = withContext(NonCancellable) {
            store.compareAndSwap(currentEnvelope, replacementEnvelope).also { didCommit ->
                if (didCommit) {
                    currentEnvelope = replacementEnvelope
                    currentSnapshot = replacement
                }
            }
        }
        if (!committed) {
            refreshLocked()
            throw ConcurrentLedgerWriteException()
        }
        result
    }

    private suspend fun refreshLocked() {
        val stored = store.readEnvelope()
            ?: throw StorageProtectionException("Encrypted ledger disappeared; plaintext fallback is forbidden")
        if (stored != currentEnvelope) {
            val restored = codec.restoreEnvelope(stored)
            validateLedgerSnapshot(restored)
            currentEnvelope = stored
            currentSnapshot = restored
        }
    }

    companion object {
        suspend fun open(
            store: ProtectedLedgerStore,
            keyAlias: String,
            keyProvider: ProvisioningDatabaseKeyProvider,
            initializationJournal: ProtectedLedgerInitializationJournal =
                KeyBackedProtectedLedgerInitializationJournal(keyAlias, keyProvider),
            cipher: PayloadCipher = Aes256GcmPayloadCipher(),
            plaintextSource: PlaintextLedgerMigrationSource? = null,
        ): ProtectedLedgerOpenResult {
            requireValidDatabaseKeyAlias(keyAlias)
            val codec = ProtectedLedgerPayloadCodec(keyAlias, keyProvider, cipher)
            val existingEnvelope = store.readEnvelope()
            val initializationState = initializationJournal.readState()

            if (existingEnvelope != null) {
                val sourceSnapshot = plaintextSource?.snapshotForMigration()
                val restored = codec.restoreEnvelope(existingEnvelope)
                validateLedgerSnapshot(restored)
                if (plaintextSource != null) {
                    if (sourceSnapshot != null && restored != sourceSnapshot) {
                        throw PlaintextLedgerMigrationConflictException()
                    }
                    plaintextSource.retireAfterVerifiedMigration()
                }
                markInitializationReady(initializationJournal, initializationState)
                return ProtectedLedgerOpenResult(
                    repository = ProtectedLedgerRepository(store, codec, existingEnvelope, restored),
                    outcome = when {
                        plaintextSource != null ->
                            ProtectedLedgerOpenOutcome.COMPLETED_INTERRUPTED_MIGRATION

                        initializationState == ProtectedLedgerInitializationState.INITIALIZING_FRESH ||
                            initializationState == ProtectedLedgerInitializationState.INITIALIZING_MIGRATION ->
                            ProtectedLedgerOpenOutcome.COMPLETED_INTERRUPTED_INITIALIZATION

                        else -> ProtectedLedgerOpenOutcome.OPENED_EXISTING
                    },
                )
            }

            if (initializationState == ProtectedLedgerInitializationState.READY) {
                throw StorageProtectionException(
                    "A protected ledger was initialized but its encrypted envelope is missing; " +
                        "automatic replacement is forbidden",
                )
            }

            val sourceSnapshot = plaintextSource?.snapshotForMigration()
            if (plaintextSource != null && sourceSnapshot == null) {
                throw StorageProtectionException(
                    "Plaintext retirement artifacts exist without an encrypted ledger; manual recovery is required",
                )
            }
            val requiredInitializationState = if (sourceSnapshot == null) {
                ProtectedLedgerInitializationState.INITIALIZING_FRESH
            } else {
                ProtectedLedgerInitializationState.INITIALIZING_MIGRATION
            }
            if (initializationState != null && initializationState != requiredInitializationState) {
                throw StorageProtectionException(
                    "Protected ledger initialization mode conflicts with the available migration source",
                )
            }
            if (initializationState == null && plaintextSource == null) {
                val orphanedKey = keyProvider.loadKey(keyAlias)
                if (orphanedKey != null) {
                    orphanedKey.destroy()
                    throw StorageProtectionException(
                        "A protected ledger key exists but the encrypted ledger is missing; " +
                            "automatic replacement is forbidden",
                    )
                }
            }
            ensureInitializationStarted(
                journal = initializationJournal,
                observedState = initializationState,
                requiredState = requiredInitializationState,
            )
            val provisioned = keyProvider.loadOrCreateKey(keyAlias)
            provisioned.destroy()
            val initial = sourceSnapshot ?: emptyLedgerSnapshot()
            validateLedgerSnapshot(initial)
            val candidateEnvelope = codec.exportEnvelope(initial)
            store.compareAndSwap(null, candidateEnvelope)

            val committedEnvelope = store.readEnvelope()
                ?: throw StorageProtectionException("Encrypted ledger initialization did not commit")
            val committedSnapshot = codec.restoreEnvelope(committedEnvelope)
            validateLedgerSnapshot(committedSnapshot)
            if (sourceSnapshot != null) {
                if (committedSnapshot != sourceSnapshot) throw PlaintextLedgerMigrationConflictException()
                plaintextSource.retireAfterVerifiedMigration()
            }
            markInitializationReady(initializationJournal, requiredInitializationState)
            return ProtectedLedgerOpenResult(
                repository = ProtectedLedgerRepository(store, codec, committedEnvelope, committedSnapshot),
                outcome = if (sourceSnapshot == null) {
                    ProtectedLedgerOpenOutcome.CREATED_EMPTY
                } else {
                    ProtectedLedgerOpenOutcome.MIGRATED_PLAINTEXT
                },
            )
        }
    }
}

private suspend fun ensureInitializationStarted(
    journal: ProtectedLedgerInitializationJournal,
    observedState: ProtectedLedgerInitializationState?,
    requiredState: ProtectedLedgerInitializationState,
) {
    if (observedState == requiredState) return
    check(observedState == null)
    if (journal.compareAndSwap(null, requiredState)) return
    val racedState = journal.readState()
    if (racedState != requiredState) {
        throw StorageProtectionException(
            "Protected ledger initialization raced with an incompatible state transition",
        )
    }
}

private suspend fun markInitializationReady(
    journal: ProtectedLedgerInitializationJournal,
    observedState: ProtectedLedgerInitializationState?,
) {
    if (observedState == ProtectedLedgerInitializationState.READY) return
    if (journal.compareAndSwap(observedState, ProtectedLedgerInitializationState.READY)) return
    if (journal.readState() != ProtectedLedgerInitializationState.READY) {
        throw StorageProtectionException("Protected ledger initialization could not be marked ready")
    }
}

private fun LedgerSnapshot.visible(includeDeleted: Boolean): LedgerSnapshot =
    if (includeDeleted) this else copy(transactions = transactions.filterNot { it.isDeleted })

private fun emptyLedgerSnapshot() = LedgerSnapshot(
    revision = 0,
    transactions = emptyList(),
    assets = emptyList(),
    maintenanceCosts = emptyList(),
    usageEvents = emptyList(),
    marketQuotes = emptyList(),
)

internal fun validateLedgerSnapshot(snapshot: LedgerSnapshot) {
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
    val assetsById = snapshot.assets.associateBy { it.id }
    require(snapshot.marketQuotes.all { quote ->
        quote.price.currency == assetsById.getValue(quote.assetId).purchasePrice.currency
    }) { "Quote must use the asset purchase currency" }
    require(snapshot.importBatches.distinctBy { it.batchId }.size == snapshot.importBatches.size) {
        "Duplicate import batch ids"
    }
    snapshot.importBatches.filter { it.state == ImportBatchState.COMMITTED }.forEach { batch ->
        require(batch.items.all { it.transactionId in transactionIds }) {
            "Committed import batch references an unknown transaction"
        }
    }
}

private fun checkedNextRevision(value: Long): Long {
    if (value == Long.MAX_VALUE) throw ArithmeticException("Ledger revision overflow")
    return value + 1
}
