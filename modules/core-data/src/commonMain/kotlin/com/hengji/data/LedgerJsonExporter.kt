package com.hengji.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object LedgerJsonExporter {
    private val json = Json { prettyPrint = true }

    fun export(snapshot: LedgerSnapshot): String = json.encodeToString(
        buildJsonObject {
            put("schemaVersion", 1)
            put("revision", snapshot.revision)
            put("transactions", JsonArray(snapshot.transactions.map { transaction ->
                buildJsonObject {
                    put("id", transaction.id.value)
                    put("kind", transaction.kind.name)
                    put("minorUnits", transaction.amount.minorUnits)
                    put("currency", transaction.amount.currency.value)
                    put("bookedOn", transaction.bookedOn.toString())
                    put("categoryId", transaction.categoryId.value)
                    put("merchant", transaction.merchant?.displayName?.let(::JsonPrimitive) ?: JsonNull)
                    put("source", transaction.source.name)
                    put("assetId", transaction.assetId?.value?.let(::JsonPrimitive) ?: JsonNull)
                }
            }))
            put("assets", JsonArray(snapshot.assets.map { asset ->
                buildJsonObject {
                    put("id", asset.id.value)
                    put("name", asset.name)
                    put("categoryId", asset.categoryId.value)
                    put("purchaseMinorUnits", asset.purchasePrice.minorUnits)
                    put("currency", asset.purchasePrice.currency.value)
                    put("purchasedOn", asset.purchasedOn.toString())
                    put("status", asset.status.name)
                    put("estimatedMinorUnits", asset.currentEstimatedValue?.minorUnits?.let(::JsonPrimitive) ?: JsonNull)
                }
            }))
            put("maintenanceCount", snapshot.maintenanceCosts.size)
            put("usageEventCount", snapshot.usageEvents.size)
            put("marketQuoteCount", snapshot.marketQuotes.size)
        },
    )
}
