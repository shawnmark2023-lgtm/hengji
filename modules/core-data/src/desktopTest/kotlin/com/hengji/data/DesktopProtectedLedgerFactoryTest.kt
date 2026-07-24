package com.hengji.data

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DesktopProtectedLedgerFactoryTest {
    @Test
    fun windowsFactoryPersistsWithDpapiAndNeverWritesPlaintextLedger() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        withDirectory { directory ->
            runTest {
                val first = openDesktopProtectedLedger(directory)
                first.repository.replaceWith(DemoLedger.snapshot())
                val expected = first.repository.snapshot(includeDeleted = true)

                val reopened = openDesktopProtectedLedger(directory)
                assertEquals(expected, reopened.repository.snapshot(includeDeleted = true))
                assertEquals(ProtectedLedgerOpenOutcome.OPENED_EXISTING, reopened.outcome)
                assertTrue(Files.isRegularFile(directory.resolve("hengji.ledger.hjenc")))
                assertTrue(Files.isRegularFile(directory.resolve("key-vault/hengji-ledger-primary.dpapi")))
                assertFalse(Files.exists(directory.resolve("hengji.db")))
                assertFalse(Files.readString(directory.resolve("hengji.ledger.hjenc")).contains("asset-headphones"))
            }
        }
    }

    private fun withDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("hengji-protected-factory-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
