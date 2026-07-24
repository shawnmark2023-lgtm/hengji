package com.hengji.app.application

import com.hengji.app.importflow.ImportDocumentFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class UserDocumentPolicyTest {
    @Test
    fun purposesExposeDifferentBoundedFileBudgets() {
        assertEquals(
            5 * 1024 * 1024,
            UserDocumentPolicy.maximumBytes(UserDocumentPurpose.TransactionImport),
        )
        assertEquals(
            25 * 1024 * 1024,
            UserDocumentPolicy.maximumBytes(UserDocumentPurpose.LedgerRestore),
        )
    }

    @Test
    fun transactionImportRejectsPayloadOverFiveMiB() {
        assertFails {
            UserDocumentPolicy.decode(
                displayName = "transactions.csv",
                bytes = ByteArray(UserDocumentPolicy.MAX_TRANSACTION_IMPORT_BYTES + 1),
                format = ImportDocumentFormat.Csv,
                purpose = UserDocumentPurpose.TransactionImport,
            )
        }
    }

    @Test
    fun ledgerRestoreAllowsPayloadAboveImportLimitWithinRestoreLimit() {
        val bytes = ByteArray(UserDocumentPolicy.MAX_TRANSACTION_IMPORT_BYTES + 1) { ' '.code.toByte() }

        val picked = UserDocumentPolicy.decode(
            displayName = "hengji-ledger.json",
            bytes = bytes,
            format = ImportDocumentFormat.Json,
            purpose = UserDocumentPurpose.LedgerRestore,
        )

        assertEquals(bytes.size, picked.content.length)
    }

    @Test
    fun decodeUsesBasenameAndRejectsMismatchedExtension() {
        val picked = UserDocumentPolicy.decode(
            displayName = "../private/ledger.JSON",
            bytes = "{}".encodeToByteArray(),
            format = ImportDocumentFormat.Json,
            purpose = UserDocumentPurpose.LedgerRestore,
        )
        assertEquals("ledger.JSON", picked.displayName)

        assertFails {
            UserDocumentPolicy.decode(
                displayName = "ledger.csv",
                bytes = "{}".encodeToByteArray(),
                format = ImportDocumentFormat.Json,
                purpose = UserDocumentPurpose.LedgerRestore,
            )
        }
    }

    @Test
    fun decodeRejectsMalformedUtf8() {
        assertFails {
            UserDocumentPolicy.decode(
                displayName = "ledger.json",
                bytes = byteArrayOf(0xC3.toByte(), 0x28),
                format = ImportDocumentFormat.Json,
                purpose = UserDocumentPurpose.LedgerRestore,
            )
        }
    }
}
