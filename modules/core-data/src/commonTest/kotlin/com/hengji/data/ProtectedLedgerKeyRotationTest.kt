package com.hengji.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest

class ProtectedLedgerKeyRotationTest {
    @Test
    fun `rotation embeds new generation and normal startup discovers it`() = runTest {
        val store = RotationMemoryStore()
        val keys = RotationKeyProvider()
        val opened = ProtectedLedgerRepository.open(store, "ledger-primary", keys)
        opened.repository.clear()
        val before = requireNotNull(store.readEnvelope())

        val rotated = ProtectedLedgerKeyRotation.rotate(
            store,
            "ledger-primary",
            "ledger-primary-g2",
            keys,
        )

        assertEquals("ledger-primary", rotated.previousKeyAlias)
        assertEquals("ledger-primary-g2", rotated.activeKeyAlias)
        assertEquals(1, rotated.revision)
        assertNotEquals(before, store.readEnvelope())
        val restarted = ProtectedLedgerRepository.open(store, "ledger-primary", keys)
        assertEquals(1, restarted.repository.snapshot().revision)
    }

    @Test
    fun `failed rotation leaves old authenticated envelope readable`() = runTest {
        val store = RotationMemoryStore()
        val keys = RotationKeyProvider()
        ProtectedLedgerRepository.open(store, "ledger-primary", keys)
        val before = requireNotNull(store.readEnvelope())
        store.rejectNextWrite = true

        assertFailsWith<ConcurrentLedgerWriteException> {
            ProtectedLedgerKeyRotation.rotate(
                store,
                "ledger-primary",
                "ledger-primary-g2",
                keys,
            )
        }

        assertEquals(before, store.readEnvelope())
        assertEquals(0, ProtectedLedgerRepository.open(store, "ledger-primary", keys).repository.snapshot().revision)
    }
}

private class RotationMemoryStore : ProtectedLedgerStore {
    private var envelope: String? = null
    var rejectNextWrite: Boolean = false

    override suspend fun readEnvelope(): String? = envelope

    override suspend fun compareAndSwap(expectedEnvelope: String?, replacementEnvelope: String): Boolean {
        if (rejectNextWrite) {
            rejectNextWrite = false
            return false
        }
        if (envelope != expectedEnvelope) return false
        envelope = replacementEnvelope
        return true
    }
}

private class RotationKeyProvider : ProvisioningDatabaseKeyProvider {
    private val keys = mutableMapOf<String, ByteArray>()

    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? =
        keys[alias]?.let(::DatabaseKeyMaterial)

    override suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial {
        val value = keys.getOrPut(alias) {
            ByteArray(AES_256_KEY_BYTES) { index -> ((alias.length * 31 + index) and 0xFF).toByte() }
        }
        return DatabaseKeyMaterial(value)
    }
}
