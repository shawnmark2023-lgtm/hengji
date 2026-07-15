package com.hengji.data

import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MarketQuote
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.UsageEvent

data class LedgerSnapshot(
    val revision: Long,
    val transactions: List<Transaction>,
    val assets: List<Asset>,
    val maintenanceCosts: List<MaintenanceCost>,
    val usageEvents: List<UsageEvent>,
    val marketQuotes: List<MarketQuote>,
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

    fun upsertAsset(asset: Asset)

    fun findAsset(id: AssetId): Asset?

    fun addMaintenanceCost(cost: MaintenanceCost)

    fun addUsageEvent(event: UsageEvent)

    fun addMarketQuote(quote: MarketQuote)

    fun replaceWith(snapshot: LedgerSnapshot)

    fun clear()
}
