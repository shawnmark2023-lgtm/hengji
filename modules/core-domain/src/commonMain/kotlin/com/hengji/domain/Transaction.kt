package com.hengji.domain

import kotlinx.datetime.LocalDate

data class TransactionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Transaction id cannot be blank" }
    }
}

data class CategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "Category id cannot be blank" }
    }
}

data class Merchant(
    val displayName: String,
    val normalizedName: String = displayName.trim().lowercase(),
) {
    init {
        require(displayName.isNotBlank()) { "Merchant name cannot be blank" }
        require(normalizedName.isNotBlank()) { "Normalized merchant name cannot be blank" }
    }
}

enum class TransactionKind {
    EXPENSE,
    INCOME,
    REFUND,
}

enum class TransactionSource {
    MANUAL,
    FILE_IMPORT,
    SHARE_EXTENSION,
    OFFICIAL_CONNECTOR,
    SAMPLE,
}

/**
 * Amount is always an unsigned magnitude. [kind] supplies cash-flow/spending direction, avoiding ambiguous negatives.
 */
data class Transaction(
    val id: TransactionId,
    val kind: TransactionKind,
    val amount: Money,
    val bookedOn: LocalDate,
    val categoryId: CategoryId,
    val merchant: Merchant? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val note: String? = null,
    val assetId: AssetId? = null,
    val originalTransactionId: TransactionId? = null,
    val importFingerprint: String? = null,
    val deletedAtEpochMillis: Long? = null,
) {
    init {
        amount.requireNonNegative("Transaction amount")
        require(note == null || note.length <= 2_000) { "Transaction note exceeds 2,000 characters" }
        require(importFingerprint == null || importFingerprint.isNotBlank()) { "Import fingerprint cannot be blank" }
        require(kind == TransactionKind.REFUND || originalTransactionId == null) {
            "Only refunds may reference an original transaction"
        }
        require(originalTransactionId != id) { "A refund cannot reference itself" }
    }

    val isDeleted: Boolean
        get() = deletedAtEpochMillis != null

    /** Positive expense, negative refund, and zero income contribution to spend metrics. */
    fun spendingContribution(): Money = when (kind) {
        TransactionKind.EXPENSE -> amount
        TransactionKind.REFUND -> -amount
        TransactionKind.INCOME -> Money.zero(amount.currency)
    }

    /** Positive income/refund and negative expense. */
    fun cashFlowContribution(): Money = when (kind) {
        TransactionKind.EXPENSE -> -amount
        TransactionKind.INCOME, TransactionKind.REFUND -> amount
    }
}
