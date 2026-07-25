package com.hengji.app.application

import com.hengji.data.DemoLedger
import com.hengji.data.InMemoryLedgerRepository
import com.hengji.data.InsightPreferenceRecord
import com.hengji.data.LedgerSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoDataSeedPolicyTest {
    @Test
    fun pristineLedgerSeedsOnlyWhenEnabled() {
        val pristine = emptySnapshot(revision = 0)

        assertTrue(DemoDataSeedPolicy.shouldSeed(enabled = true, snapshot = pristine))
        assertFalse(DemoDataSeedPolicy.shouldSeed(enabled = false, snapshot = pristine))
    }

    @Test
    fun clearedLedgerCannotBeMistakenForPristineAfterRestart() = runTest {
        val gateway = PreviewLedgerGateway(InMemoryLedgerRepository(DemoLedger.snapshot()))
        val beforeClear = gateway.snapshot()

        gateway.clear()
        val reopened = gateway.snapshot()

        assertTrue(reopened.revision > beforeClear.revision)
        assertTrue(reopened.transactions.isEmpty())
        assertTrue(reopened.assets.isEmpty())
        assertTrue(reopened.maintenanceCosts.isEmpty())
        assertTrue(reopened.usageEvents.isEmpty())
        assertTrue(reopened.marketQuotes.isEmpty())
        assertEquals(InsightPreferenceRecord(), reopened.insightPreferences)
        assertTrue(reopened.importBatches.isEmpty())
        assertFalse(DemoDataSeedPolicy.shouldSeed(enabled = true, snapshot = reopened))
    }

    private fun emptySnapshot(revision: Long) = LedgerSnapshot(
        revision = revision,
        transactions = emptyList(),
        assets = emptyList(),
        maintenanceCosts = emptyList(),
        usageEvents = emptyList(),
        marketQuotes = emptyList(),
    )
}
