package com.hengji.connectors

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class SandboxContractTest {
    @Test
    fun sandboxConnectorNeverClaimsProductionData() = runSuspend {
        SandboxConnectorCatalog.create().forEach { connector ->
            val page = connector.fetch(ConnectorFetchRequest())
            assertTrue(connector.descriptor.availability == ConnectorAvailability.SANDBOX)
            assertTrue(page.sourceDisclosure.contains("非实时"))
        }
    }

    @Test
    fun demoQuotesAreAlwaysNonLive() = runSuspend {
        val quotes = DemoQuoteProvider().query(QuoteQuery("演示手机"))
        assertTrue(quotes.isNotEmpty())
        assertTrue(quotes.all { it.provenance == QuoteProvenance.DEMO_NON_LIVE })
        assertTrue(quotes.all { !it.isLive && it.disclosure.contains("非实时") })
        assertFalse(quotes.any { it.sourceUrl != null })
    }
}

private fun runSuspend(block: suspend () -> Unit) {
    var result: Result<Unit>? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(resumeResult: Result<Unit>) {
            result = resumeResult
        }
    })
    requireNotNull(result).getOrThrow()
}
