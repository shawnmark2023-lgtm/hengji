package com.hengji.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hengji.data.ProtectedLedgerOpenOutcome
import com.hengji.data.openAndroidProtectedLedger
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectedLedgerStartupInstrumentedTest {
    @Test
    fun createsEncryptedLedgerAndReopensItWithAndroidKeystore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resetProtectedLedgerTestState(context)

        val created = openAndroidProtectedLedger(context)
        assertEquals(ProtectedLedgerOpenOutcome.CREATED_EMPTY, created.outcome)

        val keyVault = File(context.noBackupFilesDir, "hengji-key-vault")
        val ledgerFile = File(context.noBackupFilesDir, "hengji-ledger/hengji.ledger.hjenc")
        assertTrue(keyVault.listFiles().orEmpty().any { it.extension == "keystore" })
        assertTrue(ledgerFile.isFile)
        assertTrue(ledgerFile.length() > 0)

        val reopened = openAndroidProtectedLedger(context)
        assertEquals(ProtectedLedgerOpenOutcome.OPENED_EXISTING, reopened.outcome)
        assertEquals(
            created.repository.snapshot(includeDeleted = true),
            reopened.repository.snapshot(includeDeleted = true),
        )
    }

    private fun resetProtectedLedgerTestState(context: Context) {
        listOf(
            File(context.noBackupFilesDir, "hengji-key-vault"),
            File(context.noBackupFilesDir, "hengji-ledger"),
        ).forEach { directory ->
            if (directory.exists()) {
                assertTrue("Could not reset ${directory.name}", directory.deleteRecursively())
            }
        }

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.aliases().toList()
            .filter { it.startsWith("com.hengji.database-key-wrap.v1.hengji-ledger-primary") }
            .forEach(keyStore::deleteEntry)
    }
}
