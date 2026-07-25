package com.hengji.data

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MarketQuote
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.UsageEvent

/**
 * Deterministic, thread-confined repository used by previews, tests, and the no-login prototype.
 * Production UI must call it from a single application scope until the SQLite adapter replaces it.
 */
class InMemoryLedgerRepository(
    initial: LedgerSnapshot = LedgerSnapshot(
        revision = 0,
        transactions = emptyList(),
        assets = emptyList(),
        maintenanceCosts = emptyList(),
        usageEvents = emptyList(),
        marketQuotes = emptyList(),
    ),
) : LedgerRepository {
    private val transactions = LinkedHashMap<TransactionId, Transaction>()
    private val assets = LinkedHashMap<AssetId, Asset>()
    private val maintenanceCosts = LinkedHashMap<String, MaintenanceCost>()
    private val usageEvents = LinkedHashMap<String, UsageEvent>()
    private val marketQuotes = LinkedHashMap<String, MarketQuote>()
    private var insightPreferences: InsightPreferenceRecord = initial.insightPreferences
    private val importBatches = LinkedHashMap<String, ImportBatchRecord>()
    private var revision: Long = initial.revision

    init {
        load(initial, preserveRevision = true)
    }

    override fun snapshot(includeDeleted: Boolean): LedgerSnapshot = LedgerSnapshot(
        revision = revision,
        transactions = transactions.values.filter { includeDeleted || !it.isDeleted },
        assets = assets.values.toList(),
        maintenanceCosts = maintenanceCosts.values.toList(),
        usageEvents = usageEvents.values.toList(),
        marketQuotes = marketQuotes.values.toList(),
        insightPreferences = insightPreferences,
        importBatches = importBatches.values.toList(),
    )

    override fun upsertTransaction(transaction: Transaction): UpsertTransactionResult {
        val duplicate = transaction.importFingerprint?.let { fingerprint ->
            transactions.values.firstOrNull {
                it.id != transaction.id && it.importFingerprint == fingerprint && !it.isDeleted
            }
        }
        if (duplicate != null) return UpsertTransactionResult.DUPLICATE_IMPORT_SKIPPED

        val result = if (transactions.containsKey(transaction.id)) {
            UpsertTransactionResult.UPDATED
        } else {
            UpsertTransactionResult.INSERTED
        }
        transactions[transaction.id] = transaction
        bumpRevision()
        return result
    }

    override fun softDeleteTransaction(id: TransactionId, deletedAtEpochMillis: Long): Boolean {
        require(deletedAtEpochMillis >= 0) { "Deletion time cannot be negative" }
        val current = transactions[id] ?: return false
        if (current.isDeleted) return false
        transactions[id] = current.copy(deletedAtEpochMillis = deletedAtEpochMillis)
        bumpRevision()
        return true
    }

    override fun upsertAsset(asset: Asset) {
        require(marketQuotes.values.filter { it.assetId == asset.id }.all {
            it.price.currency == asset.purchasePrice.currency
        }) { "Existing quotes must use the asset purchase currency" }
        assets[asset.id] = asset
        bumpRevision()
    }

    override fun findAsset(id: AssetId): Asset? = assets[id]

    override fun addMaintenanceCost(cost: MaintenanceCost) {
        require(assets.containsKey(cost.assetId)) { "Cannot add maintenance cost for an unknown asset" }
        maintenanceCosts[cost.id.value] = cost
        bumpRevision()
    }

    override fun addUsageEvent(event: UsageEvent) {
        require(assets.containsKey(event.assetId)) { "Cannot add usage for an unknown asset" }
        usageEvents[event.id.value] = event
        bumpRevision()
    }

    override fun addMarketQuote(quote: MarketQuote) {
        val asset = requireNotNull(assets[quote.assetId]) { "Cannot add a quote for an unknown asset" }
        require(quote.price.currency == asset.purchasePrice.currency) {
            "Quote must use the asset purchase currency"
        }
        marketQuotes[quote.id] = quote
        bumpRevision()
    }

    override fun saveInsightPreferences(preferences: InsightPreferenceRecord) {
        insightPreferences = preferences
        bumpRevision()
    }

    override fun replaceWith(snapshot: LedgerSnapshot) {
        load(snapshot, preserveRevision = false)
    }

    override fun clear() {
        transactions.clear()
        assets.clear()
        maintenanceCosts.clear()
        usageEvents.clear()
        marketQuotes.clear()
        insightPreferences = InsightPreferenceRecord()
        importBatches.clear()
        bumpRevision()
    }

    private fun load(snapshot: LedgerSnapshot, preserveRevision: Boolean) {
        validateReferentialIntegrity(snapshot)
        transactions.clear()
        assets.clear()
        maintenanceCosts.clear()
        usageEvents.clear()
        marketQuotes.clear()
        importBatches.clear()

        snapshot.transactions.forEach { transactions[it.id] = it }
        snapshot.assets.forEach { assets[it.id] = it }
        snapshot.maintenanceCosts.forEach { maintenanceCosts[it.id.value] = it }
        snapshot.usageEvents.forEach { usageEvents[it.id.value] = it }
        snapshot.marketQuotes.forEach { marketQuotes[it.id] = it }
        insightPreferences = snapshot.insightPreferences
        snapshot.importBatches.forEach { importBatches[it.batchId] = it }
        revision = if (preserveRevision) snapshot.revision else checkedIncrement(snapshot.revision)
    }

    private fun validateReferentialIntegrity(snapshot: LedgerSnapshot) {
        val assetIds = snapshot.assets.mapTo(mutableSetOf()) { it.id }
        require(snapshot.assets.distinctBy { it.id }.size == snapshot.assets.size) { "Duplicate asset ids" }
        require(snapshot.transactions.distinctBy { it.id }.size == snapshot.transactions.size) { "Duplicate transaction ids" }
        require(snapshot.transactions.mapNotNull { it.importFingerprint }.distinct().size ==
            snapshot.transactions.mapNotNull { it.importFingerprint }.size
        ) { "Duplicate active import fingerprints" }
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
        val transactionIds = snapshot.transactions.mapTo(mutableSetOf()) { it.id.value }
        snapshot.importBatches.filter { it.state == ImportBatchState.COMMITTED }.forEach { batch ->
            require(batch.items.all { it.transactionId in transactionIds }) {
                "Committed import batch references an unknown transaction"
            }
        }
    }

    private fun bumpRevision() {
        revision = checkedIncrement(revision)
    }

    private fun checkedIncrement(value: Long): Long {
        if (value == Long.MAX_VALUE) throw ArithmeticException("Ledger revision overflow")
        return value + 1
    }
}
