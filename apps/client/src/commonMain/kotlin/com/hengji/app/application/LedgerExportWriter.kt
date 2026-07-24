package com.hengji.app.application

data class PreparedLedgerExport(
    val fileName: String,
    val bytes: ByteArray,
    val mediaType: String,
    val fileExtension: String,
)

object LedgerExportPolicy {
    const val MAX_EXPORT_BYTES: Int = 25 * 1024 * 1024

    fun prepare(
        suggestedFileName: String,
        utf8Content: String,
        mediaType: String,
    ): PreparedLedgerExport {
        val expectedExtension = when (mediaType.lowercase()) {
            "application/json" -> "json"
            "text/csv" -> "csv"
            else -> throw IllegalArgumentException("不支持的导出媒体类型")
        }
        val safeFileName = suggestedFileName
            .replace('\\', '/')
            .substringAfterLast('/')
            .trim()
        require(safeFileName.isNotEmpty() && safeFileName != "." && safeFileName != "..") {
            "导出文件名无效"
        }
        require(safeFileName.none { it < ' ' || it == '\u007f' }) { "导出文件名包含控制字符" }
        require(safeFileName.substringBeforeLast('.', "").isNotEmpty()) { "导出文件名缺少主体" }
        require(safeFileName.substringAfterLast('.', "").equals(expectedExtension, ignoreCase = true)) {
            "导出文件扩展名与媒体类型不一致"
        }
        val bytes = utf8Content.encodeToByteArray()
        require(bytes.isNotEmpty()) { "导出内容为空" }
        require(bytes.size <= MAX_EXPORT_BYTES) { "导出内容超过 25 MiB 安全上限" }
        return PreparedLedgerExport(
            fileName = safeFileName,
            bytes = bytes,
            mediaType = mediaType.lowercase(),
            fileExtension = expectedExtension,
        )
    }
}

fun interface LedgerExportWriter {
    /** Returns a user-facing location label, or null when the platform only supports an in-app preview. */
    suspend fun save(suggestedFileName: String, utf8Content: String, mediaType: String): String?
}

object PreviewOnlyLedgerExportWriter : LedgerExportWriter {
    override suspend fun save(suggestedFileName: String, utf8Content: String, mediaType: String): String? = null
}
