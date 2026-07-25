package com.hengji.domain

import kotlinx.datetime.LocalDate

data class AssetId(val value: String) {
    init {
        require(value.isNotBlank()) { "Asset id cannot be blank" }
    }
}

enum class AssetStatus {
    ACTIVE,
    STORED,
    SOLD,
    DISPOSED,
}

data class Asset(
    val id: AssetId,
    val name: String,
    val categoryId: CategoryId,
    val purchasePrice: Money,
    val purchasedOn: LocalDate,
    val status: AssetStatus = AssetStatus.ACTIVE,
    val targetUseDays: Int? = null,
    val warrantyEndsOn: LocalDate? = null,
    val currentEstimatedValue: Money? = null,
    val saleTargetPrice: Money? = null,
) {
    init {
        require(name.isNotBlank()) { "Asset name cannot be blank" }
        purchasePrice.requireNonNegative("Purchase price")
        require(targetUseDays == null || targetUseDays > 0) { "Target use days must be positive" }
        require(warrantyEndsOn == null || warrantyEndsOn >= purchasedOn) {
            "Warranty cannot end before purchase"
        }
        currentEstimatedValue?.let {
            it.requireNonNegative("Current estimated value")
            require(it.currency == purchasePrice.currency) { "Asset estimate must use purchase currency" }
        }
        saleTargetPrice?.let {
            require(it.minorUnits > 0) { "Sale target price must be positive" }
            require(it.currency == purchasePrice.currency) { "Sale target price must use purchase currency" }
        }
    }
}

data class MaintenanceCostId(val value: String) {
    init {
        require(value.isNotBlank()) { "Maintenance cost id cannot be blank" }
    }
}

data class MaintenanceCost(
    val id: MaintenanceCostId,
    val assetId: AssetId,
    val amount: Money,
    val occurredOn: LocalDate,
    val description: String? = null,
) {
    init {
        amount.requireNonNegative("Maintenance cost")
        require(description == null || description.length <= 500) { "Maintenance description is too long" }
    }
}

data class UsageEventId(val value: String) {
    init {
        require(value.isNotBlank()) { "Usage event id cannot be blank" }
    }
}

data class UsageEvent(
    val id: UsageEventId,
    val assetId: AssetId,
    val occurredOn: LocalDate,
    val quantity: Long = 1,
    val note: String? = null,
) {
    init {
        require(quantity > 0) { "Usage quantity must be positive" }
        require(note == null || note.length <= 500) { "Usage note is too long" }
    }
}
