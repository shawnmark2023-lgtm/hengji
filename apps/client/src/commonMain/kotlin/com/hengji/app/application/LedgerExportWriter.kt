package com.hengji.app.application

fun interface LedgerExportWriter {
    /** Returns a user-facing location label, or null when the platform only supports an in-app preview. */
    suspend fun save(suggestedFileName: String, utf8Content: String, mediaType: String): String?
}

object PreviewOnlyLedgerExportWriter : LedgerExportWriter {
    override suspend fun save(suggestedFileName: String, utf8Content: String, mediaType: String): String? = null
}
