package com.hengji.connectors

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionDirection {
    EXPENSE,
    INCOME,
    REFUND,
}

@Serializable
data class ExternalTransaction(
    val occurredAt: String,
    val amountMinor: Long,
    val currency: String,
    val direction: TransactionDirection,
    val merchant: String?,
    val category: String?,
    val note: String?,
    val externalId: String?,
    val sourceConnectorId: String,
    val fingerprint: String,
) {
    init {
        require(occurredAt.isNotBlank()) { "occurredAt is required" }
        require(amountMinor >= 0) { "amountMinor uses magnitude; direction carries the sign" }
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must be ISO-like uppercase code" }
        require(sourceConnectorId.isNotBlank()) { "sourceConnectorId is required" }
        require(fingerprint.startsWith("hj1_")) { "Unsupported fingerprint version" }
    }
}

@Serializable
enum class AmountEncoding {
    MAJOR_DECIMAL,
    MINOR_UNITS,
}

@Serializable
data class ImportFieldMapping(
    val occurredAt: String,
    val amount: String,
    val amountEncoding: AmountEncoding = AmountEncoding.MAJOR_DECIMAL,
    val currency: String? = null,
    val direction: String? = null,
    val merchant: String? = null,
    val category: String? = null,
    val note: String? = null,
    val externalId: String? = null,
    val defaultCurrency: String = "CNY",
    val defaultDirection: TransactionDirection = TransactionDirection.EXPENSE,
) {
    init {
        require(occurredAt.isNotBlank() && amount.isNotBlank()) { "Date and amount mappings are required" }
        require(defaultCurrency.matches(Regex("[A-Z]{3}"))) { "Invalid default currency" }
    }
}

@Serializable
data class ImportLimits(
    val maxBytes: Int = 5 * 1024 * 1024,
    val maxRows: Int = 10_000,
    val maxColumns: Int = 64,
    val maxCellCharacters: Int = 4_096,
) {
    init {
        require(maxBytes in 1..25 * 1024 * 1024)
        require(maxRows in 1..100_000)
        require(maxColumns in 1..256)
        require(maxCellCharacters in 1..65_536)
    }
}

@Serializable
enum class ImportErrorCode {
    FILE_TOO_LARGE,
    TOO_MANY_ROWS,
    TOO_MANY_COLUMNS,
    CELL_TOO_LARGE,
    MALFORMED_CSV,
    MALFORMED_JSON,
    UNSUPPORTED_JSON_SHAPE,
    DUPLICATE_HEADER,
    MISSING_REQUIRED_FIELD,
    INVALID_AMOUNT,
    INVALID_CURRENCY,
    INVALID_DIRECTION,
    INVALID_DATE,
    DANGEROUS_FORMULA,
}

@Serializable
data class ImportIssue(
    val code: ImportErrorCode,
    val message: String,
    val rowNumber: Int? = null,
    val field: String? = null,
)

@Serializable
enum class CandidateStatus {
    READY,
    DUPLICATE,
    INVALID,
}

@Serializable
data class ImportCandidate(
    val sourceRowNumber: Int,
    val transaction: ExternalTransaction?,
    val status: CandidateStatus,
    val issues: List<ImportIssue> = emptyList(),
)

@Serializable
data class ImportPreview(
    val sourceConnectorId: String,
    val candidates: List<ImportCandidate>,
    val fileIssues: List<ImportIssue>,
) {
    val readyCount: Int get() = candidates.count { it.status == CandidateStatus.READY }
    val duplicateCount: Int get() = candidates.count { it.status == CandidateStatus.DUPLICATE }
    val invalidCount: Int get() = candidates.count { it.status == CandidateStatus.INVALID }
}

@Serializable
data class ImportCommitRequest(
    val batchId: String,
    val sourceConnectorId: String,
    val accepted: List<ExternalTransaction>,
) {
    init {
        require(batchId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Invalid batch id" }
        require(accepted.isNotEmpty()) { "Cannot commit an empty import" }
        require(accepted.all { it.sourceConnectorId == sourceConnectorId }) { "Mixed connector batch" }
    }
}

@Serializable
data class ImportCommitResult(
    val batchId: String,
    val insertedFingerprints: List<String>,
    val committedAt: String,
)

@Serializable
data class ImportRollbackResult(
    val batchId: String,
    val removedFingerprints: List<String>,
    val rolledBackAt: String,
    val alreadyRolledBack: Boolean,
)

/**
 * Persistence adapter contract. Implementations MUST make each method atomic:
 * either the full batch changes the ledger, or no row changes it.
 */
interface ImportLedger {
    fun existingFingerprints(fingerprints: Set<String>): Set<String>
    fun commit(request: ImportCommitRequest, committedAt: String): ImportCommitResult
    fun rollbackBatch(batchId: String, rolledBackAt: String): ImportRollbackResult
}
