package com.hengji.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "ledger_metadata", primaryKeys = ["singletonId"])
data class LedgerMetadataEntity(
    val singletonId: Int = 1,
    val revision: Long,
)

@Entity(
    tableName = "transactions",
    primaryKeys = ["id"],
    indices = [Index(value = ["importFingerprint"], unique = true), Index(value = ["assetId"])],
)
data class TransactionEntity(
    val id: String,
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    val bookedOn: String,
    val categoryId: String,
    val merchantDisplayName: String?,
    val merchantNormalizedName: String?,
    val source: String,
    val note: String?,
    val assetId: String?,
    val originalTransactionId: String?,
    val importFingerprint: String?,
    val deletedAtEpochMillis: Long?,
)

@Entity(tableName = "assets", primaryKeys = ["id"], indices = [Index(value = ["categoryId"])])
data class AssetEntity(
    val id: String,
    val name: String,
    val categoryId: String,
    val purchaseMinor: Long,
    val currency: String,
    val purchasedOn: String,
    val status: String,
    val targetUseDays: Int?,
    val warrantyEndsOn: String?,
    val estimatedMinor: Long?,
    val saleTargetMinor: Long?,
)

@Entity(tableName = "maintenance_costs", primaryKeys = ["id"], indices = [Index(value = ["assetId"])])
data class MaintenanceCostEntity(
    val id: String,
    val assetId: String,
    val amountMinor: Long,
    val currency: String,
    val occurredOn: String,
    val description: String?,
)

@Entity(tableName = "usage_events", primaryKeys = ["id"], indices = [Index(value = ["assetId", "occurredOn"])])
data class UsageEventEntity(
    val id: String,
    val assetId: String,
    val occurredOn: String,
    val quantity: Long,
    val note: String?,
)

@Entity(tableName = "market_quotes", primaryKeys = ["id"], indices = [Index(value = ["assetId", "collectedOn"])])
data class MarketQuoteEntity(
    val id: String,
    val assetId: String,
    val providerId: String,
    val provenance: String,
    val specification: String,
    val condition: String,
    val priceMinor: Long,
    val shippingMinor: Long,
    val currency: String,
    val collectedOn: String,
    val sourceUrl: String?,
    val confidenceBasisPoints: Int,
    val isLive: Boolean,
)

@Entity(tableName = "insight_preferences", primaryKeys = ["singletonId"])
data class InsightPreferencesEntity(
    val singletonId: Int = 1,
    val mutedTypesJson: String,
    val ignoredDeduplicationKeysJson: String,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "[]")
    val adoptedDeduplicationKeysJson: String = "[]",
    @ColumnInfo(defaultValue = "{}")
    val snoozedUntilEpochMillisByKeyJson: String = "{}",
    @ColumnInfo(defaultValue = "{}")
    val feedbackTypeByKeyJson: String = "{}",
)

@Entity(tableName = "import_batches", primaryKeys = ["batchId"], indices = [Index(value = ["state"])])
data class ImportBatchEntity(
    val batchId: String,
    val sourceConnectorId: String,
    val sourceDigest: String,
    val state: String,
    val createdAtEpochMillis: Long,
    val committedAtEpochMillis: Long,
    val rolledBackAtEpochMillis: Long?,
)

@Entity(
    tableName = "import_batch_items",
    primaryKeys = ["batchId", "transactionId"],
    indices = [Index(value = ["fingerprint"]), Index(value = ["transactionId"])],
)
data class ImportBatchItemEntity(
    val batchId: String,
    val transactionId: String,
    val fingerprint: String,
)
