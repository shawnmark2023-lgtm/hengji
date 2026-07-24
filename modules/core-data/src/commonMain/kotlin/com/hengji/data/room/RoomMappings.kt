package com.hengji.data.room

import com.hengji.data.ImportBatchItemRecord
import com.hengji.data.ImportBatchRecord
import com.hengji.data.ImportBatchState
import com.hengji.data.InsightPreferenceRecord
import com.hengji.data.LedgerSnapshot
import com.hengji.domain.Asset
import com.hengji.domain.AssetId
import com.hengji.domain.AssetStatus
import com.hengji.domain.CategoryId
import com.hengji.domain.Confidence
import com.hengji.domain.CurrencyCode
import com.hengji.domain.ItemCondition
import com.hengji.domain.MaintenanceCost
import com.hengji.domain.MaintenanceCostId
import com.hengji.domain.MarketQuote
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.QuoteProvenance
import com.hengji.domain.QuoteProviderId
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import com.hengji.domain.UsageEvent
import com.hengji.domain.UsageEventId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val preferenceJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
}

internal fun Transaction.toRoomEntity() = TransactionEntity(
    id = id.value,
    kind = kind.name,
    amountMinor = amount.minorUnits,
    currency = amount.currency.value,
    bookedOn = bookedOn.toString(),
    categoryId = categoryId.value,
    merchantDisplayName = merchant?.displayName,
    merchantNormalizedName = merchant?.normalizedName,
    source = source.name,
    note = note,
    assetId = assetId?.value,
    originalTransactionId = originalTransactionId?.value,
    importFingerprint = importFingerprint,
    deletedAtEpochMillis = deletedAtEpochMillis,
)

internal fun TransactionEntity.toDomain() = Transaction(
    id = TransactionId(id),
    kind = TransactionKind.valueOf(kind),
    amount = Money(amountMinor, CurrencyCode(currency)),
    bookedOn = LocalDate.parse(bookedOn),
    categoryId = CategoryId(categoryId),
    merchant = merchantDisplayName?.let { Merchant(it, merchantNormalizedName ?: it.trim().lowercase()) },
    source = TransactionSource.valueOf(source),
    note = note,
    assetId = assetId?.let(::AssetId),
    originalTransactionId = originalTransactionId?.let(::TransactionId),
    importFingerprint = importFingerprint,
    deletedAtEpochMillis = deletedAtEpochMillis,
)

internal fun Asset.toRoomEntity() = AssetEntity(
    id = id.value,
    name = name,
    categoryId = categoryId.value,
    purchaseMinor = purchasePrice.minorUnits,
    currency = purchasePrice.currency.value,
    purchasedOn = purchasedOn.toString(),
    status = status.name,
    targetUseDays = targetUseDays,
    warrantyEndsOn = warrantyEndsOn?.toString(),
    estimatedMinor = currentEstimatedValue?.minorUnits,
)

internal fun AssetEntity.toDomain() = Asset(
    id = AssetId(id),
    name = name,
    categoryId = CategoryId(categoryId),
    purchasePrice = Money(purchaseMinor, CurrencyCode(currency)),
    purchasedOn = LocalDate.parse(purchasedOn),
    status = AssetStatus.valueOf(status),
    targetUseDays = targetUseDays,
    warrantyEndsOn = warrantyEndsOn?.let(LocalDate::parse),
    currentEstimatedValue = estimatedMinor?.let { Money(it, CurrencyCode(currency)) },
)

internal fun MaintenanceCost.toRoomEntity() = MaintenanceCostEntity(
    id = id.value,
    assetId = assetId.value,
    amountMinor = amount.minorUnits,
    currency = amount.currency.value,
    occurredOn = occurredOn.toString(),
    description = description,
)

internal fun MaintenanceCostEntity.toDomain() = MaintenanceCost(
    id = MaintenanceCostId(id),
    assetId = AssetId(assetId),
    amount = Money(amountMinor, CurrencyCode(currency)),
    occurredOn = LocalDate.parse(occurredOn),
    description = description,
)

internal fun UsageEvent.toRoomEntity() = UsageEventEntity(
    id = id.value,
    assetId = assetId.value,
    occurredOn = occurredOn.toString(),
    quantity = quantity,
    note = note,
)

internal fun UsageEventEntity.toDomain() = UsageEvent(
    id = UsageEventId(id),
    assetId = AssetId(assetId),
    occurredOn = LocalDate.parse(occurredOn),
    quantity = quantity,
    note = note,
)

internal fun MarketQuote.toRoomEntity() = MarketQuoteEntity(
    id = id,
    assetId = assetId.value,
    providerId = providerId.value,
    provenance = provenance.name,
    specification = specification,
    condition = condition.name,
    priceMinor = price.minorUnits,
    shippingMinor = shipping.minorUnits,
    currency = price.currency.value,
    collectedOn = collectedOn.toString(),
    sourceUrl = sourceUrl,
    confidenceBasisPoints = confidence.basisPoints,
    isLive = isLive,
)

internal fun MarketQuoteEntity.toDomain() = MarketQuote(
    id = id,
    assetId = AssetId(assetId),
    providerId = QuoteProviderId(providerId),
    provenance = QuoteProvenance.valueOf(provenance),
    specification = specification,
    condition = ItemCondition.valueOf(condition),
    price = Money(priceMinor, CurrencyCode(currency)),
    shipping = Money(shippingMinor, CurrencyCode(currency)),
    collectedOn = LocalDate.parse(collectedOn),
    sourceUrl = sourceUrl,
    confidence = Confidence(confidenceBasisPoints),
    isLive = isLive,
)

internal fun InsightPreferenceRecord.toRoomEntity() = InsightPreferencesEntity(
    mutedTypesJson = preferenceJson.encodeToString(mutedTypes.sorted()),
    ignoredDeduplicationKeysJson = preferenceJson.encodeToString(ignoredDeduplicationKeys.sorted()),
    updatedAtEpochMillis = updatedAtEpochMillis,
    adoptedDeduplicationKeysJson = preferenceJson.encodeToString(adoptedDeduplicationKeys.sorted()),
    snoozedUntilEpochMillisByKeyJson = preferenceJson.encodeToString<Map<String, Long>>(
        buildMap {
            snoozedUntilEpochMillisByKey.entries.sortedBy { it.key }.forEach { (key, value) ->
                put(key, value)
            }
        },
    ),
)

internal fun InsightPreferencesEntity.toDomain() = InsightPreferenceRecord(
    mutedTypes = preferenceJson.decodeFromString<List<String>>(mutedTypesJson).toSet(),
    ignoredDeduplicationKeys = preferenceJson.decodeFromString<List<String>>(ignoredDeduplicationKeysJson).toSet(),
    updatedAtEpochMillis = updatedAtEpochMillis,
    adoptedDeduplicationKeys =
        preferenceJson.decodeFromString<List<String>>(adoptedDeduplicationKeysJson).toSet(),
    snoozedUntilEpochMillisByKey =
        preferenceJson.decodeFromString<Map<String, Long>>(snoozedUntilEpochMillisByKeyJson),
)

internal fun ImportBatchRecord.toRoomEntity() = ImportBatchEntity(
    batchId = batchId,
    sourceConnectorId = sourceConnectorId,
    sourceDigest = sourceDigest,
    state = state.name,
    createdAtEpochMillis = createdAtEpochMillis,
    committedAtEpochMillis = committedAtEpochMillis,
    rolledBackAtEpochMillis = rolledBackAtEpochMillis,
)

internal fun RoomSnapshotRows.toDomainSnapshot(includeDeleted: Boolean): LedgerSnapshot {
    val itemsByBatch = importItems.groupBy { it.batchId }
    return LedgerSnapshot(
        revision = revision,
        transactions = transactions.map(TransactionEntity::toDomain).filter { includeDeleted || !it.isDeleted },
        assets = assets.map(AssetEntity::toDomain),
        maintenanceCosts = maintenanceCosts.map(MaintenanceCostEntity::toDomain),
        usageEvents = usageEvents.map(UsageEventEntity::toDomain),
        marketQuotes = marketQuotes.map(MarketQuoteEntity::toDomain),
        insightPreferences = insightPreferences?.toDomain() ?: InsightPreferenceRecord(),
        importBatches = importBatches.map { batch ->
            ImportBatchRecord(
                batchId = batch.batchId,
                sourceConnectorId = batch.sourceConnectorId,
                sourceDigest = batch.sourceDigest,
                state = ImportBatchState.valueOf(batch.state),
                createdAtEpochMillis = batch.createdAtEpochMillis,
                committedAtEpochMillis = batch.committedAtEpochMillis,
                rolledBackAtEpochMillis = batch.rolledBackAtEpochMillis,
                items = itemsByBatch[batch.batchId].orEmpty().map {
                    ImportBatchItemRecord(it.transactionId, it.fingerprint)
                },
            )
        },
    )
}

internal fun LedgerSnapshot.toRoomRows(revisionOverride: Long = revision): RoomSnapshotRows = RoomSnapshotRows(
    revision = revisionOverride,
    transactions = transactions.map(Transaction::toRoomEntity),
    assets = assets.map(Asset::toRoomEntity),
    maintenanceCosts = maintenanceCosts.map(MaintenanceCost::toRoomEntity),
    usageEvents = usageEvents.map(UsageEvent::toRoomEntity),
    marketQuotes = marketQuotes.map(MarketQuote::toRoomEntity),
    insightPreferences = insightPreferences.toRoomEntity(),
    importBatches = importBatches.map(ImportBatchRecord::toRoomEntity),
    importItems = importBatches.flatMap { batch ->
        batch.items.map { ImportBatchItemEntity(batch.batchId, it.transactionId, it.fingerprint) }
    },
)
