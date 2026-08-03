package com.hengji.app.importflow

import com.hengji.connectors.AmountEncoding
import com.hengji.connectors.CandidateStatus
import com.hengji.connectors.ImportCommitResult
import com.hengji.connectors.ImportFieldMapping
import com.hengji.connectors.ImportPreview
import com.hengji.connectors.ImportRollbackResult

enum class ImportDocumentFormat {
    Csv,
    Json,
}

enum class LocalCaptureMode {
    LongScreenshot,
    ImageOrPdf,
    SharedDocument,
}

sealed interface ImportSource {
    val format: ImportDocumentFormat
    val isSandbox: Boolean

    data object CsvSandboxSample : ImportSource {
        override val format = ImportDocumentFormat.Csv
        override val isSandbox = true
    }

    data object JsonSandboxSample : ImportSource {
        override val format = ImportDocumentFormat.Json
        override val isSandbox = true
    }

    data class UserFile(override val format: ImportDocumentFormat) : ImportSource {
        override val isSandbox = false
    }

    data class LocalCapture(val mode: LocalCaptureMode) : ImportSource {
        override val format = ImportDocumentFormat.Csv
        override val isSandbox = false
    }
}

/**
 * A display-safe handle. The adapter keeps raw file contents outside Compose state and resolves
 * [documentId] only when previewing or committing.
 */
data class ImportDocumentSummary(
    val documentId: String,
    val displayName: String,
    val format: ImportDocumentFormat,
    val byteCount: Long,
    val fields: List<String>,
    val sampleRows: List<Map<String, String>>,
    val isSandbox: Boolean,
) {
    init {
        require(documentId.isNotBlank())
        require(displayName.isNotBlank())
        require(byteCount >= 0)
        require(fields.isNotEmpty())
        require(fields.distinct().size == fields.size)
        require(sampleRows.size <= 5) { "Only a small redacted sample belongs in UI state" }
        require(sampleRows.all { row -> row.keys.all(fields::contains) })
    }
}

enum class ImportTargetField(
    val label: String,
    val required: Boolean,
) {
    OccurredAt("交易时间", true),
    Amount("金额", true),
    Merchant("商户", false),
    Category("分类", false),
    Direction("收支方向", false),
    Currency("币种", false),
    Note("备注", false),
    ExternalId("平台订单号", false),
}

data class ImportMappingDraft(
    val occurredAt: String? = null,
    val amount: String? = null,
    val merchant: String? = null,
    val category: String? = null,
    val direction: String? = null,
    val currency: String? = null,
    val note: String? = null,
    val externalId: String? = null,
    val amountEncoding: AmountEncoding = AmountEncoding.MAJOR_DECIMAL,
    val defaultCurrency: String = "CNY",
) {
    val isComplete: Boolean
        get() = !occurredAt.isNullOrBlank() && !amount.isNullOrBlank()

    fun sourceFor(target: ImportTargetField): String? = when (target) {
        ImportTargetField.OccurredAt -> occurredAt
        ImportTargetField.Amount -> amount
        ImportTargetField.Merchant -> merchant
        ImportTargetField.Category -> category
        ImportTargetField.Direction -> direction
        ImportTargetField.Currency -> currency
        ImportTargetField.Note -> note
        ImportTargetField.ExternalId -> externalId
    }

    fun map(target: ImportTargetField, sourceField: String?): ImportMappingDraft {
        val normalized = sourceField?.trim()?.ifEmpty { null }
        return when (target) {
            ImportTargetField.OccurredAt -> copy(occurredAt = normalized)
            ImportTargetField.Amount -> copy(amount = normalized)
            ImportTargetField.Merchant -> copy(merchant = normalized)
            ImportTargetField.Category -> copy(category = normalized)
            ImportTargetField.Direction -> copy(direction = normalized)
            ImportTargetField.Currency -> copy(currency = normalized)
            ImportTargetField.Note -> copy(note = normalized)
            ImportTargetField.ExternalId -> copy(externalId = normalized)
        }
    }

    fun toConnectorMapping(): ImportFieldMapping? {
        val dateField = occurredAt ?: return null
        val amountField = amount ?: return null
        return ImportFieldMapping(
            occurredAt = dateField,
            amount = amountField,
            amountEncoding = amountEncoding,
            currency = currency,
            direction = direction,
            merchant = merchant,
            category = category,
            note = note,
            externalId = externalId,
            defaultCurrency = defaultCurrency,
        )
    }
}

data class ImportCommitSelection(
    val document: ImportDocumentSummary,
    val preview: ImportPreview,
    val acceptedFingerprints: Set<String>,
) {
    init {
        require(acceptedFingerprints.isNotEmpty())
        val readyFingerprints = preview.candidates
            .filter { it.status == CandidateStatus.READY }
            .mapNotNull { it.transaction?.fingerprint }
            .toSet()
        require(acceptedFingerprints.all(readyFingerprints::contains))
    }
}

/**
 * Platform/application boundary. Implementations may use the connectors parser and ImportLedger,
 * while the UI never reads files, performs SQL, or stores raw document contents.
 */
interface ImportFlowPort {
    suspend fun openSource(source: ImportSource): ImportDocumentSummary?

    suspend fun preview(
        document: ImportDocumentSummary,
        mapping: ImportFieldMapping,
    ): ImportPreview

    /** Must insert all accepted rows or no rows. */
    suspend fun commitAtomically(selection: ImportCommitSelection): ImportCommitResult

    suspend fun rollbackBatch(batchId: String): ImportRollbackResult
}
