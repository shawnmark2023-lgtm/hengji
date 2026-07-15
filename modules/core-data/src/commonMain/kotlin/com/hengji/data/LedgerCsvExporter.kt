package com.hengji.data

/** Spreadsheet-safe transaction export. Amounts stay in integer minor units for exact round-tripping. */
object LedgerCsvExporter {
    private val headers = listOf(
        "id",
        "kind",
        "minorUnits",
        "currency",
        "bookedOn",
        "categoryId",
        "merchant",
        "source",
        "note",
        "assetId",
        "importFingerprint",
        "deletedAtEpochMillis",
    )

    fun export(snapshot: LedgerSnapshot): String = buildString {
        appendLine(headers.joinToString(","))
        snapshot.transactions.forEach { transaction ->
            appendLine(
                listOf(
                    transaction.id.value,
                    transaction.kind.name,
                    transaction.amount.minorUnits.toString(),
                    transaction.amount.currency.value,
                    transaction.bookedOn.toString(),
                    transaction.categoryId.value,
                    transaction.merchant?.displayName.orEmpty(),
                    transaction.source.name,
                    transaction.note.orEmpty(),
                    transaction.assetId?.value.orEmpty(),
                    transaction.importFingerprint.orEmpty(),
                    transaction.deletedAtEpochMillis?.toString().orEmpty(),
                ).joinToString(",", transform = ::safeCell),
            )
        }
    }

    private fun safeCell(raw: String): String {
        val formulaSafe = if (raw.dropWhile(Char::isWhitespace).firstOrNull() in FORMULA_PREFIXES) "'$raw" else raw
        return "\"${formulaSafe.replace("\"", "\"\"")}\""
    }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')
}
