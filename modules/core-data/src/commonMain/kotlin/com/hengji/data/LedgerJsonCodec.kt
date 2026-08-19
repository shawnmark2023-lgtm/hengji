package com.hengji.data

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

const val LEDGER_EXPORT_SCHEMA_VERSION: Int = 6

object LedgerJsonCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    fun export(snapshot: LedgerSnapshot): String = json.encodeToString(LedgerExportDto.from(snapshot))

    fun restore(payload: String): LedgerSnapshot {
        require(payload.encodeToByteArray().size <= 25 * 1024 * 1024) { "Ledger restore exceeds 25 MiB" }
        val parsed = json.parseToJsonElement(payload)
        val migrated = migrateToCurrent(parsed)
        return json.decodeFromJsonElement<LedgerExportDto>(migrated)
            .toDomain()
            .also(::validateLedgerSnapshot)
    }

    internal fun migrateToCurrent(element: JsonElement): JsonObject {
        val root = element as? JsonObject ?: throw IllegalArgumentException("Ledger export root must be an object")
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 0
        require(version <= LEDGER_EXPORT_SCHEMA_VERSION) { "Ledger export schema $version is newer than supported" }
        var migrated = root
        var migratedVersion = version
        while (migratedVersion < LEDGER_EXPORT_SCHEMA_VERSION) {
            migrated = when (migratedVersion) {
                0 -> migrateVersionZeroToOne(migrated)
                1 -> migrateVersionOneToTwo(migrated)
                2 -> migrateVersionTwoToThree(migrated)
                3 -> migrateVersionThreeToFour(migrated)
                4 -> migrateVersionFourToFive(migrated)
                5 -> migrateVersionFiveToSix(migrated)
                else -> error("Unsupported ledger schema")
            }
            migratedVersion += 1
        }
        return migrated
    }

    private fun migrateVersionZeroToOne(root: JsonObject) = JsonObject(root + mapOf(
        "schemaVersion" to JsonPrimitive(1),
        "revision" to (root["revision"] ?: JsonPrimitive(0)),
        "transactions" to (root["transactions"] ?: JsonArray(emptyList())),
        "assets" to (root["assets"] ?: JsonArray(emptyList())),
        "maintenanceCosts" to (root["maintenanceCosts"] ?: JsonArray(emptyList())),
        "usageEvents" to (root["usageEvents"] ?: JsonArray(emptyList())),
        "marketQuotes" to (root["marketQuotes"] ?: JsonArray(emptyList())),
        "importBatches" to (root["importBatches"] ?: JsonArray(emptyList())),
    ))

    private fun migrateVersionOneToTwo(root: JsonObject): JsonObject {
        val legacyPreferences = root["insightPreferences"] as? JsonObject ?: JsonObject(emptyMap())
        val migratedPreferences = JsonObject(legacyPreferences + mapOf(
            "adoptedDeduplicationKeys" to
                (legacyPreferences["adoptedDeduplicationKeys"] ?: JsonArray(emptyList())),
            "snoozedUntilEpochMillisByKey" to
                (legacyPreferences["snoozedUntilEpochMillisByKey"] ?: JsonObject(emptyMap())),
        ))
        return JsonObject(root + mapOf(
            "schemaVersion" to JsonPrimitive(2),
            "insightPreferences" to migratedPreferences,
        ))
    }

    private fun migrateVersionTwoToThree(root: JsonObject): JsonObject {
        val migratedAssets = when (val assets = root["assets"]) {
            null -> JsonArray(emptyList())
            is JsonArray -> JsonArray(
                assets.map { asset ->
                    if (asset is JsonObject) {
                        JsonObject(
                            asset + mapOf(
                                "saleTargetMinorUnits" to
                                    (asset["saleTargetMinorUnits"] ?: JsonNull),
                            ),
                        )
                    } else {
                        asset
                    }
                },
            )
            else -> assets
        }
        return JsonObject(root + mapOf(
            "schemaVersion" to JsonPrimitive(3),
            "assets" to migratedAssets,
        ))
    }

    private fun migrateVersionThreeToFour(root: JsonObject): JsonObject {
        val legacyPreferences = root["insightPreferences"] as? JsonObject ?: JsonObject(emptyMap())
        val migratedPreferences = JsonObject(
            legacyPreferences + mapOf(
                "feedbackTypeByKey" to
                    (legacyPreferences["feedbackTypeByKey"] ?: JsonObject(emptyMap())),
            ),
        )
        return JsonObject(
            root + mapOf(
                "schemaVersion" to JsonPrimitive(4),
                "insightPreferences" to migratedPreferences,
            ),
        )
    }

    private fun migrateVersionFourToFive(root: JsonObject): JsonObject {
        val legacyPreferences = root["insightPreferences"] as? JsonObject ?: JsonObject(emptyMap())
        val migratedPreferences = JsonObject(
            legacyPreferences + mapOf(
                "personalAiEnabled" to
                    (legacyPreferences["personalAiEnabled"] ?: JsonPrimitive(true)),
                "onboardingCompletedAtEpochMillis" to
                    (legacyPreferences["onboardingCompletedAtEpochMillis"] ?: JsonNull),
                "personalAnalysisHistory" to
                    (legacyPreferences["personalAnalysisHistory"] ?: JsonArray(emptyList())),
            ),
        )
        return JsonObject(
            root + mapOf(
                "schemaVersion" to JsonPrimitive(5),
                "insightPreferences" to migratedPreferences,
            ),
        )
    }

    private fun migrateVersionFiveToSix(root: JsonObject): JsonObject {
        val legacyPreferences = root["insightPreferences"] as? JsonObject ?: JsonObject(emptyMap())
        val migratedPreferences = JsonObject(
            legacyPreferences + mapOf(
                "monthlyBudgetMinor" to (legacyPreferences["monthlyBudgetMinor"] ?: JsonNull),
            ),
        )
        return JsonObject(
            root + mapOf(
                "schemaVersion" to JsonPrimitive(6),
                "insightPreferences" to migratedPreferences,
            ),
        )
    }
}

@Serializable
private data class LedgerExportDto(
    val schemaVersion: Int = LEDGER_EXPORT_SCHEMA_VERSION,
    val revision: Long = 0,
    val transactions: List<TransactionDto> = emptyList(),
    val assets: List<AssetDto> = emptyList(),
    val maintenanceCosts: List<MaintenanceDto> = emptyList(),
    val usageEvents: List<UsageDto> = emptyList(),
    val marketQuotes: List<QuoteDto> = emptyList(),
    val insightPreferences: InsightPreferencesDto = InsightPreferencesDto(),
    val importBatches: List<ImportBatchDto> = emptyList(),
) {
    init {
        require(schemaVersion == LEDGER_EXPORT_SCHEMA_VERSION)
        require(revision >= 0)
    }

    fun toDomain() = LedgerSnapshot(
        revision = revision,
        transactions = transactions.map(TransactionDto::toDomain),
        assets = assets.map(AssetDto::toDomain),
        maintenanceCosts = maintenanceCosts.map(MaintenanceDto::toDomain),
        usageEvents = usageEvents.map(UsageDto::toDomain),
        marketQuotes = marketQuotes.map(QuoteDto::toDomain),
        insightPreferences = insightPreferences.toDomain(),
        importBatches = importBatches.map(ImportBatchDto::toDomain),
    )

    companion object {
        fun from(snapshot: LedgerSnapshot) = LedgerExportDto(
            revision = snapshot.revision,
            transactions = snapshot.transactions.map(TransactionDto::from),
            assets = snapshot.assets.map(AssetDto::from),
            maintenanceCosts = snapshot.maintenanceCosts.map(MaintenanceDto::from),
            usageEvents = snapshot.usageEvents.map(UsageDto::from),
            marketQuotes = snapshot.marketQuotes.map(QuoteDto::from),
            insightPreferences = InsightPreferencesDto.from(snapshot.insightPreferences),
            importBatches = snapshot.importBatches.map(ImportBatchDto::from),
        )
    }
}

@Serializable
private data class TransactionDto(
    val id: String,
    val kind: String,
    val minorUnits: Long,
    val currency: String,
    val bookedOn: String,
    val categoryId: String,
    val merchant: String? = null,
    val merchantNormalized: String? = null,
    val source: String = TransactionSource.MANUAL.name,
    val note: String? = null,
    val assetId: String? = null,
    val originalTransactionId: String? = null,
    val importFingerprint: String? = null,
    val deletedAtEpochMillis: Long? = null,
) {
    fun toDomain() = Transaction(
        id = TransactionId(id),
        kind = TransactionKind.valueOf(kind),
        amount = Money(minorUnits, CurrencyCode(currency)),
        bookedOn = LocalDate.parse(bookedOn),
        categoryId = CategoryId(categoryId),
        merchant = merchant?.let { Merchant(it, merchantNormalized ?: it.trim().lowercase()) },
        source = TransactionSource.valueOf(source),
        note = note,
        assetId = assetId?.let(::AssetId),
        originalTransactionId = originalTransactionId?.let(::TransactionId),
        importFingerprint = importFingerprint,
        deletedAtEpochMillis = deletedAtEpochMillis,
    )

    companion object {
        fun from(value: Transaction) = TransactionDto(
            id = value.id.value,
            kind = value.kind.name,
            minorUnits = value.amount.minorUnits,
            currency = value.amount.currency.value,
            bookedOn = value.bookedOn.toString(),
            categoryId = value.categoryId.value,
            merchant = value.merchant?.displayName,
            merchantNormalized = value.merchant?.normalizedName,
            source = value.source.name,
            note = value.note,
            assetId = value.assetId?.value,
            originalTransactionId = value.originalTransactionId?.value,
            importFingerprint = value.importFingerprint,
            deletedAtEpochMillis = value.deletedAtEpochMillis,
        )
    }
}

@Serializable
private data class AssetDto(
    val id: String,
    val name: String,
    val categoryId: String,
    val purchaseMinorUnits: Long,
    val currency: String,
    val purchasedOn: String,
    val status: String,
    val targetUseDays: Int? = null,
    val warrantyEndsOn: String? = null,
    val estimatedMinorUnits: Long? = null,
    val saleTargetMinorUnits: Long? = null,
) {
    fun toDomain() = Asset(
        id = AssetId(id),
        name = name,
        categoryId = CategoryId(categoryId),
        purchasePrice = Money(purchaseMinorUnits, CurrencyCode(currency)),
        purchasedOn = LocalDate.parse(purchasedOn),
        status = AssetStatus.valueOf(status),
        targetUseDays = targetUseDays,
        warrantyEndsOn = warrantyEndsOn?.let(LocalDate::parse),
        currentEstimatedValue = estimatedMinorUnits?.let { Money(it, CurrencyCode(currency)) },
        saleTargetPrice = saleTargetMinorUnits?.let { Money(it, CurrencyCode(currency)) },
    )

    companion object {
        fun from(value: Asset) = AssetDto(
            id = value.id.value,
            name = value.name,
            categoryId = value.categoryId.value,
            purchaseMinorUnits = value.purchasePrice.minorUnits,
            currency = value.purchasePrice.currency.value,
            purchasedOn = value.purchasedOn.toString(),
            status = value.status.name,
            targetUseDays = value.targetUseDays,
            warrantyEndsOn = value.warrantyEndsOn?.toString(),
            estimatedMinorUnits = value.currentEstimatedValue?.minorUnits,
            saleTargetMinorUnits = value.saleTargetPrice?.minorUnits,
        )
    }
}

@Serializable
private data class MaintenanceDto(
    val id: String,
    val assetId: String,
    val minorUnits: Long,
    val currency: String,
    val occurredOn: String,
    val description: String? = null,
) {
    fun toDomain() = MaintenanceCost(
        MaintenanceCostId(id), AssetId(assetId), Money(minorUnits, CurrencyCode(currency)),
        LocalDate.parse(occurredOn), description,
    )
    companion object {
        fun from(value: MaintenanceCost) = MaintenanceDto(
            value.id.value, value.assetId.value, value.amount.minorUnits, value.amount.currency.value,
            value.occurredOn.toString(), value.description,
        )
    }
}

@Serializable
private data class UsageDto(
    val id: String,
    val assetId: String,
    val occurredOn: String,
    val quantity: Long = 1,
    val note: String? = null,
) {
    fun toDomain() = UsageEvent(UsageEventId(id), AssetId(assetId), LocalDate.parse(occurredOn), quantity, note)
    companion object {
        fun from(value: UsageEvent) = UsageDto(
            value.id.value, value.assetId.value, value.occurredOn.toString(), value.quantity, value.note,
        )
    }
}

@Serializable
private data class QuoteDto(
    val id: String,
    val assetId: String,
    val providerId: String,
    val provenance: String,
    val specification: String,
    val condition: String,
    val priceMinor: Long,
    val shippingMinor: Long = 0,
    val currency: String,
    val collectedOn: String,
    val sourceUrl: String? = null,
    val confidenceBasisPoints: Int,
    val isLive: Boolean = false,
) {
    fun toDomain() = MarketQuote(
        id, AssetId(assetId), QuoteProviderId(providerId), QuoteProvenance.valueOf(provenance), specification,
        ItemCondition.valueOf(condition), Money(priceMinor, CurrencyCode(currency)),
        Money(shippingMinor, CurrencyCode(currency)), LocalDate.parse(collectedOn), sourceUrl,
        Confidence(confidenceBasisPoints), isLive,
    )
    companion object {
        fun from(value: MarketQuote) = QuoteDto(
            value.id, value.assetId.value, value.providerId.value, value.provenance.name, value.specification,
            value.condition.name, value.price.minorUnits, value.shipping.minorUnits, value.price.currency.value,
            value.collectedOn.toString(), value.sourceUrl, value.confidence.basisPoints, value.isLive,
        )
    }
}

@Serializable
private data class InsightPreferencesDto(
    val mutedTypes: Set<String> = emptySet(),
    val ignoredDeduplicationKeys: Set<String> = emptySet(),
    val updatedAtEpochMillis: Long = 0,
    val adoptedDeduplicationKeys: Set<String> = emptySet(),
    val snoozedUntilEpochMillisByKey: Map<String, Long> = emptyMap(),
    val feedbackTypeByKey: Map<String, String> = emptyMap(),
    val personalAiEnabled: Boolean = true,
    val onboardingCompletedAtEpochMillis: Long? = null,
    val personalAnalysisHistory: List<PersonalAnalysisRecord> = emptyList(),
    val monthlyBudgetMinor: Long? = null,
) {
    fun toDomain() = InsightPreferenceRecord(
        mutedTypes = mutedTypes,
        ignoredDeduplicationKeys = ignoredDeduplicationKeys,
        updatedAtEpochMillis = updatedAtEpochMillis,
        adoptedDeduplicationKeys = adoptedDeduplicationKeys,
        snoozedUntilEpochMillisByKey = snoozedUntilEpochMillisByKey,
        feedbackTypeByKey = feedbackTypeByKey,
        personalAiEnabled = personalAiEnabled,
        onboardingCompletedAtEpochMillis = onboardingCompletedAtEpochMillis,
        personalAnalysisHistory = personalAnalysisHistory,
        monthlyBudgetMinor = monthlyBudgetMinor,
    )
    companion object {
        fun from(value: InsightPreferenceRecord) = InsightPreferencesDto(
            mutedTypes = value.mutedTypes,
            ignoredDeduplicationKeys = value.ignoredDeduplicationKeys,
            updatedAtEpochMillis = value.updatedAtEpochMillis,
            adoptedDeduplicationKeys = value.adoptedDeduplicationKeys,
            snoozedUntilEpochMillisByKey = value.snoozedUntilEpochMillisByKey,
            feedbackTypeByKey = value.feedbackTypeByKey,
            personalAiEnabled = value.personalAiEnabled,
            onboardingCompletedAtEpochMillis = value.onboardingCompletedAtEpochMillis,
            personalAnalysisHistory = value.personalAnalysisHistory,
            monthlyBudgetMinor = value.monthlyBudgetMinor,
        )
    }
}

@Serializable
private data class ImportBatchItemDto(val transactionId: String, val fingerprint: String) {
    fun toDomain() = ImportBatchItemRecord(transactionId, fingerprint)
    companion object {
        fun from(value: ImportBatchItemRecord) = ImportBatchItemDto(value.transactionId, value.fingerprint)
    }
}

@Serializable
private data class ImportBatchDto(
    val batchId: String,
    val sourceConnectorId: String,
    val sourceDigest: String,
    val state: String,
    val createdAtEpochMillis: Long,
    val committedAtEpochMillis: Long,
    val rolledBackAtEpochMillis: Long? = null,
    val items: List<ImportBatchItemDto> = emptyList(),
) {
    fun toDomain() = ImportBatchRecord(
        batchId, sourceConnectorId, sourceDigest, ImportBatchState.valueOf(state), createdAtEpochMillis,
        committedAtEpochMillis, rolledBackAtEpochMillis, items.map(ImportBatchItemDto::toDomain),
    )
    companion object {
        fun from(value: ImportBatchRecord) = ImportBatchDto(
            value.batchId, value.sourceConnectorId, value.sourceDigest, value.state.name,
            value.createdAtEpochMillis, value.committedAtEpochMillis, value.rolledBackAtEpochMillis,
            value.items.map(ImportBatchItemDto::from),
        )
    }
}
