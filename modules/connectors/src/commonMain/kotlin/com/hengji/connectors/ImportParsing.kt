package com.hengji.connectors

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ImportFormatException(
    val issue: ImportIssue,
) : IllegalArgumentException(issue.message)

object StableTransactionFingerprint {
    fun create(
        occurredAt: String,
        amountMinor: Long,
        currency: String,
        direction: TransactionDirection,
        merchant: String?,
        externalId: String?,
        sourceConnectorId: String,
    ): String {
        val canonical = listOf(
            sourceConnectorId.trim().lowercase(),
            occurredAt.trim(),
            amountMinor.toString(),
            currency.trim().uppercase(),
            direction.name,
            normalizeText(merchant),
            normalizeText(externalId),
        ).joinToString("\u001f")
        val first = fnv1a64(canonical, 0xcbf29ce484222325UL)
        val second = fnv1a64(canonical, 0x84222325cbf29ce4UL)
        return "hj1_${first.toString(16).padStart(16, '0')}${second.toString(16).padStart(16, '0')}"
    }

    private fun normalizeText(value: String?): String =
        value.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")

    private fun fnv1a64(value: String, seed: ULong): ULong {
        var hash = seed
        value.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x100000001b3UL
        }
        return hash
    }
}

object MinorUnitsParser {
    private val scales = mapOf(
        "JPY" to 0, "KRW" to 0, "CLP" to 0, "VND" to 0,
        "BHD" to 3, "IQD" to 3, "JOD" to 3, "KWD" to 3, "LYD" to 3, "OMR" to 3, "TND" to 3,
        "CLF" to 4,
        "CNY" to 2, "USD" to 2, "EUR" to 2, "GBP" to 2,
    )

    fun parse(raw: String, currency: String, encoding: AmountEncoding): Long? {
        val value = raw.trim().replace(",", "")
        if (encoding == AmountEncoding.MINOR_UNITS) {
            return value.toLongOrNull()?.let { if (it == Long.MIN_VALUE) null else kotlin.math.abs(it) }
        }
        val match = Regex("^([+-]?)(\\d+)(?:\\.(\\d+))?$").matchEntire(value) ?: return null
        val scale = scales[currency] ?: 2
        val fraction = match.groupValues[3]
        if (fraction.length > scale) return null
        val factor = powerOfTen(scale) ?: return null
        val whole = match.groupValues[2].toLongOrNull() ?: return null
        val fractionMinor = fraction.padEnd(scale, '0').ifEmpty { "0" }.toLongOrNull() ?: return null
        return try {
            MathCompat.addExact(MathCompat.multiplyExact(whole, factor), fractionMinor)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun powerOfTen(scale: Int): Long? {
        var result = 1L
        repeat(scale) {
            if (result > Long.MAX_VALUE / 10) return null
            result *= 10
        }
        return result
    }
}

private object MathCompat {
    fun multiplyExact(left: Long, right: Long): Long {
        if (left == 0L || right == 0L) return 0L
        val result = left * right
        if (result / right != left) throw ArithmeticException("long overflow")
        return result
    }

    fun addExact(left: Long, right: Long): Long {
        val result = left + right
        if (((left xor result) and (right xor result)) < 0) throw ArithmeticException("long overflow")
        return result
    }
}

internal data class TabularDocument(
    val rows: List<Map<String, String>>,
)

internal object CsvDocumentParser {
    fun parse(content: String, limits: ImportLimits): TabularDocument {
        enforceSize(content, limits)
        val records = mutableListOf<MutableList<String>>()
        var record = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var afterQuote = false
        var index = 0

        fun finishCell() {
            if (cell.length > limits.maxCellCharacters) fail(ImportErrorCode.CELL_TOO_LARGE, "CSV cell exceeds limit")
            record += cell.toString()
            cell.clear()
            afterQuote = false
            if (record.size > limits.maxColumns) fail(ImportErrorCode.TOO_MANY_COLUMNS, "CSV has too many columns")
        }

        fun finishRecord() {
            finishCell()
            records += record
            record = mutableListOf()
            if (records.size > limits.maxRows + 1) fail(ImportErrorCode.TOO_MANY_ROWS, "CSV has too many rows")
        }

        while (index < content.length) {
            val char = content[index]
            if (quoted) {
                when {
                    char == '"' && index + 1 < content.length && content[index + 1] == '"' -> {
                        cell.append('"')
                        index++
                    }
                    char == '"' -> {
                        quoted = false
                        afterQuote = true
                    }
                    else -> cell.append(char)
                }
            } else {
                when (char) {
                    '"' -> if (cell.isEmpty() && !afterQuote) quoted = true else fail(ImportErrorCode.MALFORMED_CSV, "Unexpected quote")
                    ',' -> finishCell()
                    '\n' -> finishRecord()
                    '\r' -> {
                        finishRecord()
                        if (index + 1 < content.length && content[index + 1] == '\n') index++
                    }
                    else -> {
                        if (afterQuote && !char.isWhitespace()) fail(ImportErrorCode.MALFORMED_CSV, "Unexpected content after closing quote")
                        if (!afterQuote) cell.append(char)
                    }
                }
            }
            if (cell.length > limits.maxCellCharacters) fail(ImportErrorCode.CELL_TOO_LARGE, "CSV cell exceeds limit")
            index++
        }
        if (quoted) fail(ImportErrorCode.MALFORMED_CSV, "Unclosed quoted field")
        if (cell.isNotEmpty() || record.isNotEmpty()) finishRecord()
        if (records.isEmpty()) return TabularDocument(emptyList())

        val headers = records.first().mapIndexed { headerIndex, value ->
            value.removePrefix("\uFEFF").trim().ifEmpty { "column_${headerIndex + 1}" }
        }
        if (headers.toSet().size != headers.size) fail(ImportErrorCode.DUPLICATE_HEADER, "CSV headers must be unique")
        val rows = records.drop(1).filterNot { values -> values.all { it.isBlank() } }.map { values ->
            if (values.size != headers.size) fail(ImportErrorCode.MALFORMED_CSV, "CSV row width does not match its header")
            headers.associateWith { header -> values.getOrElse(headers.indexOf(header)) { "" } }
        }
        return TabularDocument(rows)
    }
}

internal object JsonDocumentParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun parse(content: String, limits: ImportLimits): TabularDocument {
        enforceSize(content, limits)
        val root = try {
            json.parseToJsonElement(content)
        } catch (_: IllegalArgumentException) {
            fail(ImportErrorCode.MALFORMED_JSON, "JSON is malformed")
        }
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["transactions"] as? JsonArray
                ?: fail(ImportErrorCode.UNSUPPORTED_JSON_SHAPE, "JSON must be an array or contain a transactions array")
            else -> fail(ImportErrorCode.UNSUPPORTED_JSON_SHAPE, "JSON root must be an object or array")
        }
        if (array.size > limits.maxRows) fail(ImportErrorCode.TOO_MANY_ROWS, "JSON has too many rows")
        val rows = array.mapIndexed { index, element ->
            val objectValue = element as? JsonObject
                ?: fail(ImportErrorCode.UNSUPPORTED_JSON_SHAPE, "JSON transaction at index $index must be an object")
            if (objectValue.size > limits.maxColumns) fail(ImportErrorCode.TOO_MANY_COLUMNS, "JSON object has too many fields")
            objectValue.mapValues { (_, value) -> primitiveContent(value, limits) }
        }
        return TabularDocument(rows)
    }

    private fun primitiveContent(value: JsonElement, limits: ImportLimits): String {
        val result = when (value) {
            JsonNull -> ""
            is JsonPrimitive -> value.content
            else -> fail(ImportErrorCode.UNSUPPORTED_JSON_SHAPE, "Nested JSON values are not accepted in transaction fields")
        }
        if (result.length > limits.maxCellCharacters) fail(ImportErrorCode.CELL_TOO_LARGE, "JSON value exceeds limit")
        return result
    }
}

private fun enforceSize(content: String, limits: ImportLimits) {
    if (content.encodeToByteArray().size > limits.maxBytes) {
        fail(ImportErrorCode.FILE_TOO_LARGE, "Import exceeds ${limits.maxBytes} bytes")
    }
}

private fun fail(code: ImportErrorCode, message: String): Nothing =
    throw ImportFormatException(ImportIssue(code = code, message = message))

class TransactionImporter(
    private val limits: ImportLimits = ImportLimits(),
) {
    fun previewCsv(
        content: String,
        mapping: ImportFieldMapping,
        sourceConnectorId: String,
        existingFingerprints: Set<String> = emptySet(),
    ): ImportPreview = preview(CsvDocumentParser.parse(content, limits), mapping, sourceConnectorId, existingFingerprints)

    fun previewJson(
        content: String,
        mapping: ImportFieldMapping,
        sourceConnectorId: String,
        existingFingerprints: Set<String> = emptySet(),
    ): ImportPreview = preview(JsonDocumentParser.parse(content, limits), mapping, sourceConnectorId, existingFingerprints)

    private fun preview(
        document: TabularDocument,
        mapping: ImportFieldMapping,
        sourceConnectorId: String,
        existingFingerprints: Set<String>,
    ): ImportPreview {
        val seen = existingFingerprints.toMutableSet()
        val candidates = document.rows.mapIndexed { index, row ->
            toCandidate(index + 2, row, mapping, sourceConnectorId, seen).also { candidate ->
                candidate.transaction?.let { seen += it.fingerprint }
            }
        }
        return ImportPreview(sourceConnectorId, candidates, emptyList())
    }

    private fun toCandidate(
        rowNumber: Int,
        row: Map<String, String>,
        mapping: ImportFieldMapping,
        sourceConnectorId: String,
        seen: Set<String>,
    ): ImportCandidate {
        val issues = mutableListOf<ImportIssue>()
        fun mapped(column: String?, required: Boolean = false): String? {
            if (column == null) return null
            val value = row[column]?.trim()
            if (required && value.isNullOrEmpty()) {
                issues += ImportIssue(ImportErrorCode.MISSING_REQUIRED_FIELD, "Required value is missing", rowNumber, column)
            }
            return value?.ifEmpty { null }
        }

        val occurredAt = mapped(mapping.occurredAt, required = true)
        if (occurredAt != null && !isSupportedDate(occurredAt)) {
            issues += ImportIssue(ImportErrorCode.INVALID_DATE, "Use YYYY-MM-DD or an ISO-8601 timestamp", rowNumber, mapping.occurredAt)
        }
        val currency = (mapped(mapping.currency) ?: mapping.defaultCurrency).uppercase()
        if (!currency.matches(Regex("[A-Z]{3}"))) {
            issues += ImportIssue(ImportErrorCode.INVALID_CURRENCY, "Currency must be a three-letter uppercase code", rowNumber, mapping.currency)
        }
        val rawAmount = mapped(mapping.amount, required = true)
        val amountMinor = rawAmount?.let { MinorUnitsParser.parse(it, currency, mapping.amountEncoding) }
        if (rawAmount != null && amountMinor == null) {
            issues += ImportIssue(ImportErrorCode.INVALID_AMOUNT, "Amount is invalid, too precise, or outside 64-bit range", rowNumber, mapping.amount)
        }
        val rawDirection = mapped(mapping.direction)
        val direction = rawDirection?.let(::parseDirection) ?: mapping.defaultDirection
        if (rawDirection != null && parseDirection(rawDirection) == null) {
            issues += ImportIssue(ImportErrorCode.INVALID_DIRECTION, "Direction must be expense, income, or refund", rowNumber, mapping.direction)
        }
        val merchant = mapped(mapping.merchant)
        val category = mapped(mapping.category)
        val note = mapped(mapping.note)
        val externalId = mapped(mapping.externalId)
        listOf(mapping.merchant to merchant, mapping.category to category, mapping.note to note).forEach { (field, value) ->
            if (value != null && isFormula(value)) {
                issues += ImportIssue(ImportErrorCode.DANGEROUS_FORMULA, "Formula-like text is rejected to prevent spreadsheet injection", rowNumber, field)
            }
        }

        if (issues.isNotEmpty() || occurredAt == null || amountMinor == null) {
            return ImportCandidate(rowNumber, null, CandidateStatus.INVALID, issues)
        }
        val fingerprint = StableTransactionFingerprint.create(
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            merchant = merchant,
            externalId = externalId,
            sourceConnectorId = sourceConnectorId,
        )
        val transaction = ExternalTransaction(
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            merchant = merchant,
            category = category,
            note = note,
            externalId = externalId,
            sourceConnectorId = sourceConnectorId,
            fingerprint = fingerprint,
        )
        return ImportCandidate(
            sourceRowNumber = rowNumber,
            transaction = transaction,
            status = if (fingerprint in seen) CandidateStatus.DUPLICATE else CandidateStatus.READY,
        )
    }

    private fun parseDirection(value: String): TransactionDirection? = when (value.trim().lowercase()) {
        "expense", "支出", "消费" -> TransactionDirection.EXPENSE
        "income", "收入" -> TransactionDirection.INCOME
        "refund", "退款" -> TransactionDirection.REFUND
        else -> null
    }

    private fun isSupportedDate(value: String): Boolean = try {
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) {
            LocalDate.parse(value)
        } else {
            Instant.parse(value)
        }
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun isFormula(value: String): Boolean =
        value.dropWhile { it == ' ' }.firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r')
}
