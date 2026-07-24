package com.hengji.app.application

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class LedgerExportPolicyTest {
    @Test
    fun preparesBoundedJsonWithSafeBasename() {
        val export = LedgerExportPolicy.prepare(
            suggestedFileName = "../private/hengji-ledger.JSON",
            utf8Content = """{"schemaVersion":2}""",
            mediaType = "APPLICATION/JSON",
        )

        assertEquals("hengji-ledger.JSON", export.fileName)
        assertEquals("json", export.fileExtension)
        assertEquals("application/json", export.mediaType)
        assertContentEquals("""{"schemaVersion":2}""".encodeToByteArray(), export.bytes)
    }

    @Test
    fun preparesCsvWithoutChangingUtf8Content() {
        val content = "merchant,amount\r\n咖啡,1800\r\n"
        val export = LedgerExportPolicy.prepare(
            suggestedFileName = "transactions.csv",
            utf8Content = content,
            mediaType = "text/csv",
        )

        assertContentEquals(content.encodeToByteArray(), export.bytes)
    }

    @Test
    fun rejectsUnknownMediaTypeAndMismatchedExtension() {
        assertFails {
            LedgerExportPolicy.prepare("ledger.txt", "data", "text/plain")
        }
        assertFails {
            LedgerExportPolicy.prepare("ledger.csv", "{}", "application/json")
        }
    }

    @Test
    fun rejectsEmptyAndOversizedExports() {
        assertFails {
            LedgerExportPolicy.prepare("ledger.json", "", "application/json")
        }
        assertFails {
            LedgerExportPolicy.prepare(
                "ledger.json",
                "x".repeat(LedgerExportPolicy.MAX_EXPORT_BYTES + 1),
                "application/json",
            )
        }
    }
}
