package com.hengji.app.application

import com.hengji.app.importflow.ImportCommitSelection
import com.hengji.app.importflow.ImportDocumentFormat
import com.hengji.app.importflow.ImportDocumentSummary
import com.hengji.app.importflow.ImportFlowPort
import com.hengji.app.importflow.ImportSource
import com.hengji.connectors.CandidateStatus
import com.hengji.connectors.ImportCommitResult
import com.hengji.connectors.ImportFieldMapping
import com.hengji.connectors.ImportPreview
import com.hengji.connectors.ImportRollbackResult
import com.hengji.connectors.TransactionDirection
import com.hengji.connectors.TransactionImporter
import com.hengji.data.CommitImportBatchRequest
import com.hengji.domain.CategoryId
import com.hengji.domain.CurrencyCode
import com.hengji.domain.Merchant
import com.hengji.domain.Money
import com.hengji.domain.Transaction
import com.hengji.domain.TransactionId
import com.hengji.domain.TransactionKind
import com.hengji.domain.TransactionSource
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

data class PickedImportDocument(
    val displayName: String,
    val content: String,
    val format: ImportDocumentFormat,
)

fun interface UserImportDocumentPicker {
    suspend fun pick(format: ImportDocumentFormat): PickedImportDocument?
}

object UnavailableUserImportDocumentPicker : UserImportDocumentPicker {
    override suspend fun pick(format: ImportDocumentFormat): PickedImportDocument =
        throw UnsupportedOperationException("此平台的系统文件选择器尚未接入；可先体验明确标注的沙箱样例。")
}

/** Local-only import adapter. Raw document contents remain in this short-lived object and never enter Compose state. */
class LocalImportFlowPort(
    private val ledger: AppLedgerGateway,
    private val picker: UserImportDocumentPicker = UnavailableUserImportDocumentPicker,
    private val importer: TransactionImporter = TransactionImporter(),
) : ImportFlowPort {
    private val rawDocuments = mutableMapOf<String, String>()

    override suspend fun openSource(source: ImportSource): ImportDocumentSummary? {
        val picked = when (source) {
            ImportSource.CsvSandboxSample -> PickedImportDocument(
                displayName = "衡记 CSV 沙箱样例.csv",
                content = CSV_SANDBOX,
                format = ImportDocumentFormat.Csv,
            )
            ImportSource.JsonSandboxSample -> PickedImportDocument(
                displayName = "衡记 JSON 沙箱样例.json",
                content = JSON_SANDBOX,
                format = ImportDocumentFormat.Json,
            )
            is ImportSource.UserFile -> picker.pick(source.format) ?: return null
        }
        val documentId = documentId(picked.content, picked.format)
        rawDocuments[documentId] = picked.content
        return inspect(
            documentId = documentId,
            picked = picked,
            isSandbox = source.isSandbox,
        )
    }

    override suspend fun preview(
        document: ImportDocumentSummary,
        mapping: ImportFieldMapping,
    ): ImportPreview {
        val content = requireNotNull(rawDocuments[document.documentId]) { "导入内容已过期，请重新选择来源" }
        val sourceId = sourceConnectorId(document)
        val initial = when (document.format) {
            ImportDocumentFormat.Csv -> importer.previewCsv(content, mapping, sourceId)
            ImportDocumentFormat.Json -> importer.previewJson(content, mapping, sourceId)
        }
        val candidateFingerprints = initial.candidates.mapNotNullTo(mutableSetOf()) { it.transaction?.fingerprint }
        val existing = ledger.snapshot(includeDeleted = false).transactions
            .mapNotNullTo(mutableSetOf()) { it.importFingerprint }
            .intersect(candidateFingerprints)
        return when (document.format) {
            ImportDocumentFormat.Csv -> importer.previewCsv(content, mapping, sourceId, existing)
            ImportDocumentFormat.Json -> importer.previewJson(content, mapping, sourceId, existing)
        }
    }

    override suspend fun commitAtomically(selection: ImportCommitSelection): ImportCommitResult {
        val accepted = selection.preview.candidates
            .filter { it.status == CandidateStatus.READY }
            .mapNotNull { it.transaction }
            .filter { it.fingerprint in selection.acceptedFingerprints }
        require(accepted.isNotEmpty()) { "没有可提交的记录" }
        val now = Clock.System.now()
        val batchId = "batch_${now.toEpochMilliseconds()}_${accepted.first().fingerprint.takeLast(8)}"
        val domainTransactions = accepted.map { external ->
            Transaction(
                id = TransactionId("import-${external.fingerprint.removePrefix("hj1_")}"),
                kind = when (external.direction) {
                    TransactionDirection.EXPENSE -> TransactionKind.EXPENSE
                    TransactionDirection.INCOME -> TransactionKind.INCOME
                    TransactionDirection.REFUND -> TransactionKind.REFUND
                },
                amount = Money(external.amountMinor, CurrencyCode(external.currency)),
                bookedOn = parseBookedDate(external.occurredAt),
                categoryId = CategoryId(normalizeCategory(external.category)),
                merchant = external.merchant?.takeIf { it.isNotBlank() }?.let(::Merchant),
                source = TransactionSource.FILE_IMPORT,
                note = external.note?.take(2_000),
                importFingerprint = external.fingerprint,
            )
        }
        val result = ledger.commitImportBatch(
            CommitImportBatchRequest(
                batchId = batchId,
                sourceConnectorId = selection.preview.sourceConnectorId,
                sourceDigest = "document:${selection.document.documentId}",
                createdAtEpochMillis = now.toEpochMilliseconds(),
                committedAtEpochMillis = now.toEpochMilliseconds(),
                transactions = domainTransactions,
            ),
        )
        val insertedIds = result.insertedTransactionIds.toSet()
        val insertedFingerprints = domainTransactions
            .filter { it.id.value in insertedIds }
            .mapNotNull { it.importFingerprint }
        rawDocuments.remove(selection.document.documentId)
        return ImportCommitResult(batchId, insertedFingerprints, now.toString())
    }

    override suspend fun rollbackBatch(batchId: String): ImportRollbackResult {
        val snapshot = ledger.snapshot(includeDeleted = true)
        val fingerprints = snapshot.importBatches
            .firstOrNull { it.batchId == batchId }
            ?.items
            ?.map { it.fingerprint }
            .orEmpty()
        val now = Clock.System.now()
        val result = ledger.rollbackImportBatch(batchId, now.toEpochMilliseconds())
        return ImportRollbackResult(
            batchId = batchId,
            removedFingerprints = if (result.alreadyRolledBack) emptyList() else fingerprints,
            rolledBackAt = now.toString(),
            alreadyRolledBack = result.alreadyRolledBack,
        )
    }

    private fun inspect(
        documentId: String,
        picked: PickedImportDocument,
        isSandbox: Boolean,
    ): ImportDocumentSummary {
        val (fields, rows) = when (picked.format) {
            ImportDocumentFormat.Csv -> inspectCsv(picked.content)
            ImportDocumentFormat.Json -> inspectJson(picked.content)
        }
        return ImportDocumentSummary(
            documentId = documentId,
            displayName = picked.displayName,
            format = picked.format,
            byteCount = picked.content.encodeToByteArray().size.toLong(),
            fields = fields,
            sampleRows = rows.take(5),
            isSandbox = isSandbox,
        )
    }

    private fun inspectCsv(content: String): Pair<List<String>, List<Map<String, String>>> {
        val lines = content.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.take(6).toList()
        require(lines.isNotEmpty()) { "CSV 文件为空" }
        val fields = parseSimpleCsvLine(lines.first()).map(String::trim)
        require(fields.none { it.isBlank() } && fields.distinct().size == fields.size) { "CSV 表头无效或重复" }
        val rows = lines.drop(1).map { line ->
            val cells = parseSimpleCsvLine(line)
            fields.mapIndexed { index, field -> field to cells.getOrElse(index) { "" } }.toMap()
        }
        return fields to rows
    }

    private fun inspectJson(content: String): Pair<List<String>, List<Map<String, String>>> {
        val root = Json.parseToJsonElement(content)
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["transactions"]?.jsonArray ?: error("JSON 必须包含 transactions 数组")
            else -> error("JSON 根节点必须是数组或对象")
        }
        val objects = array.take(5).map { it.jsonObject }
        val fields = (array.firstOrNull() as? JsonObject)?.keys?.toList().orEmpty()
        require(fields.isNotEmpty()) { "JSON 没有可映射字段" }
        val rows = objects.map { obj ->
            fields.associateWith { field -> (obj[field] as? JsonPrimitive)?.content.orEmpty() }
        }
        return fields to rows
    }

    private fun parseSimpleCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += cell.toString()
                    cell.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        require(!quoted) { "CSV 引号未闭合" }
        cells += cell.toString()
        return cells
    }

    private fun parseBookedDate(value: String): LocalDate =
        LocalDate.parse(value.take(10))

    private fun normalizeCategory(value: String?): String = when (value?.trim()?.lowercase()) {
        "餐饮", "food", "dining" -> "dining"
        "交通", "transport" -> "transport"
        "居家", "家居", "home" -> "home"
        "数码", "digital" -> "digital"
        else -> "other"
    }

    private fun sourceConnectorId(document: ImportDocumentSummary): String = when {
        document.isSandbox && document.format == ImportDocumentFormat.Csv -> "sandbox-csv"
        document.isSandbox -> "sandbox-json"
        document.format == ImportDocumentFormat.Csv -> "local-file-csv"
        else -> "local-file-json"
    }

    private fun documentId(content: String, format: ImportDocumentFormat): String {
        var hash = 0xcbf29ce484222325UL
        content.encodeToByteArray().forEach { byte ->
            hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL
        }
        return "${format.name.lowercase()}-${hash.toString(16).padStart(16, '0')}"
    }

    private companion object {
        val CSV_SANDBOX = """
            date,amount,merchant,category,direction,currency,orderId,note
            2026-07-02,36.80,虚构早餐店,餐饮,expense,CNY,demo-csv-001,沙箱样例
            2026-07-03,128.00,虚构出行,交通,expense,CNY,demo-csv-002,沙箱样例
            2026-07-04,19.90,虚构咖啡店,餐饮,expense,CNY,demo-csv-003,沙箱样例
        """.trimIndent()

        val JSON_SANDBOX = """
            {"transactions":[
              {"date":"2026-07-05","amount":"68.00","merchant":"虚构生活店","category":"居家","direction":"expense","currency":"CNY","orderId":"demo-json-001"},
              {"date":"2026-07-06","amount":"22.50","merchant":"虚构交通卡","category":"交通","direction":"expense","currency":"CNY","orderId":"demo-json-002"},
              {"date":"2026-07-07","amount":"18.00","merchant":"虚构退款商户","category":"其他","direction":"refund","currency":"CNY","orderId":"demo-json-003"}
            ]}
        """.trimIndent()
    }
}
