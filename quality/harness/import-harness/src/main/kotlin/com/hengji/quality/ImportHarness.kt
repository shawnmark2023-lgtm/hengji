package com.hengji.quality

import com.hengji.connectors.CandidateStatus
import com.hengji.connectors.ImportErrorCode
import com.hengji.connectors.ImportFieldMapping
import com.hengji.connectors.ImportFormatException
import com.hengji.connectors.ImportLimits
import com.hengji.connectors.TransactionImporter

fun main() {
    val mapping = ImportFieldMapping(
        occurredAt = "date",
        amount = "amount",
        merchant = "merchant",
        externalId = "id",
    )
    val importer = TransactionImporter()
    val checks = linkedMapOf<String, Boolean>()

    checks["unclosed_quote"] = expectFormat(ImportErrorCode.MALFORMED_CSV) {
        importer.previewCsv("date,amount\n2026-07-01,\"12.34", mapping, "quality-csv")
    }
    checks["row_width_mismatch"] = expectFormat(ImportErrorCode.MALFORMED_CSV) {
        importer.previewCsv("date,amount\n2026-07-01,12.34,extra", mapping, "quality-csv")
    }
    checks["duplicate_header"] = expectFormat(ImportErrorCode.DUPLICATE_HEADER) {
        importer.previewCsv("date,amount,amount\n2026-07-01,12.34,12.34", mapping, "quality-csv")
    }
    checks["nested_json"] = expectFormat(ImportErrorCode.UNSUPPORTED_JSON_SHAPE) {
        importer.previewJson(
            """[{"date":"2026-07-01","amount":"12.34","merchant":{"name":"bad"}}]""",
            mapping,
            "quality-json",
        )
    }
    checks["too_many_rows"] = expectFormat(ImportErrorCode.TOO_MANY_ROWS) {
        TransactionImporter(ImportLimits(maxRows = 1)).previewCsv(
            "date,amount\n2026-07-01,1.00\n2026-07-02,2.00",
            mapping,
            "quality-csv",
        )
    }
    checks["file_too_large"] = expectFormat(ImportErrorCode.FILE_TOO_LARGE) {
        TransactionImporter(ImportLimits(maxBytes = 16)).previewCsv(
            "date,amount\n2026-07-01,12.34",
            mapping,
            "quality-csv",
        )
    }
    val emptyRequired = importer.previewCsv(
        "date,amount,merchant,id\n,12.34,merchant,empty-date",
        mapping,
        "quality-csv",
    ).candidates.single()
    checks["empty_required_field"] =
        emptyRequired.status == CandidateStatus.INVALID &&
            emptyRequired.issues.any { it.code == ImportErrorCode.MISSING_REQUIRED_FIELD }

    val validUnicode = importer.previewCsv(
        "\uFEFFdate,amount,merchant,id\n2026-07-01,12.34,咖啡店,unicode-1",
        mapping,
        "quality-csv",
    )
    checks["utf8_bom_and_unicode"] = validUnicode.readyCount == 1

    val passed = checks.count { it.value }
    val result = jsonObject(
        "gate" to "malformed-import",
        "status" to if (passed == checks.size) "passed" else "failed",
        "testCount" to checks.size,
        "passed" to passed,
        "failed" to checks.filterValues { !it }.keys.toList(),
        "cases" to checks,
        "limitation" to "Byte decoding belongs to the future platform file adapter; this harness covers the current String importer contract.",
    )
    println(result)
    check(passed == checks.size) { "malformed-import quality harness failed" }
}

private fun expectFormat(code: ImportErrorCode, block: () -> Unit): Boolean =
    try {
        block()
        false
    } catch (error: ImportFormatException) {
        error.issue.code == code
    }

private fun jsonObject(vararg entries: Pair<String, Any?>): String =
    entries.joinToString(prefix = "{", postfix = "}") { (key, value) -> "${key.json()}:${value.toJsonValue()}" }

private fun Any?.toJsonValue(): String = when (this) {
    null -> "null"
    is String -> json()
    is Boolean, is Number -> toString()
    is Map<*, *> -> entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "${key.toString().json()}:${value.toJsonValue()}"
    }
    is Iterable<*> -> joinToString(prefix = "[", postfix = "]") { it.toJsonValue() }
    else -> toString().json()
}

private fun String.json(): String = buildString {
    append('"')
    this@json.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        }
    }
    append('"')
}
