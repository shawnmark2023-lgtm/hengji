package com.hengji.data

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StorageSecurityTest {
    @Test
    fun rejectsCipherWithoutAuthenticatedEncryption() {
        assertFailsWith<IllegalArgumentException> {
            ProtectedLedgerPayloadCodec("ledger", MissingKeyProvider, UnauthenticatedCipher)
        }
    }

    @Test
    fun missingKeyFailsClosedWithoutPlaintextFallback() = runSuspend {
        val codec = ProtectedLedgerPayloadCodec("ledger", MissingKeyProvider, AuthenticatedTestCipher)
        assertFailsWith<StorageProtectionException> {
            runSuspend { codec.export(DemoLedger.snapshot()) }
        }
    }

    @Test
    fun aesGcmEnvelopeRoundTripsAndUsesFreshNonces() = runSuspend {
        val codec = ProtectedLedgerPayloadCodec("ledger-primary", FixedKeyProvider(), Aes256GcmPayloadCipher())
        val snapshot = DemoLedger.snapshot()

        val first = codec.export(snapshot)
        val second = codec.export(snapshot)

        assertNotEquals(first.nonce.toList(), second.nonce.toList())
        assertNotEquals(first.ciphertext.toList(), second.ciphertext.toList())
        assertEquals(snapshot, codec.restore(first))
        assertEquals(snapshot, codec.restoreEnvelope(codec.exportEnvelope(snapshot)))
    }

    @Test
    fun aesGcmRejectsTamperingWrongKeyAndWrongContext() = runSuspend {
        val cipher = Aes256GcmPayloadCipher()
        val primary = ProtectedLedgerPayloadCodec("ledger-primary", FixedKeyProvider(fill = 7), cipher)
        val payload = primary.export(DemoLedger.snapshot())
        val tamperedBytes = payload.ciphertext.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val tampered = CipherPayload(payload.algorithmId, payload.nonce, tamperedBytes)

        assertFailsWith<StorageProtectionException> { runSuspend { primary.restore(tampered) } }
        assertFailsWith<StorageProtectionException> {
            runSuspend {
                ProtectedLedgerPayloadCodec("ledger-primary", FixedKeyProvider(fill = 8), cipher).restore(payload)
            }
        }
        assertFailsWith<StorageProtectionException> {
            runSuspend {
                ProtectedLedgerPayloadCodec("ledger-secondary", FixedKeyProvider(fill = 7), cipher).restore(payload)
            }
        }
    }

    @Test
    fun protectedEnvelopeRejectsUnknownOrMalformedMetadata() {
        val valid = CipherPayload(
            algorithmId = Aes256GcmPayloadCipher.ALGORITHM_ID,
            nonce = ByteArray(12) { 1 },
            ciphertext = ByteArray(16) { 2 },
        )
        val encoded = ProtectedLedgerEnvelopeCodec.encode(valid)

        assertFailsWith<IllegalArgumentException> {
            ProtectedLedgerEnvelopeCodec.decode(encoded.replace("\"formatVersion\":1", "\"formatVersion\":2"))
        }
        assertFailsWith<IllegalArgumentException> {
            ProtectedLedgerEnvelopeCodec.decode(encoded.replace(Aes256GcmPayloadCipher.ALGORITHM_ID, "AES-CBC"))
        }
        assertFailsWith<IllegalArgumentException> {
            ProtectedLedgerEnvelopeCodec.decode(encoded.replace("\"nonceBase64\":\"", "\"nonceBase64\":\"%%%"))
        }
    }

    @Test
    fun keyMaterialIsDestroyedAfterEveryOperation() = runSuspend {
        val provider = ObservingKeyProvider()
        val codec = ProtectedLedgerPayloadCodec("ledger", provider, AuthenticatedTestCipher)
        codec.export(DemoLedger.snapshot())

        assertTrue(provider.material != null)
        assertFailsWith<IllegalStateException> { provider.material!!.copyBytes() }
    }
}

private object MissingKeyProvider : DatabaseKeyProvider {
    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? = null
}

private object UnauthenticatedCipher : PayloadCipher {
    override val algorithmId = "unsafe-test"
    override val providesAuthenticatedEncryption = false
    override suspend fun encrypt(plaintext: ByteArray, associatedData: ByteArray, key: ByteArray) =
        error("not called")
    override suspend fun decrypt(payload: CipherPayload, associatedData: ByteArray, key: ByteArray) =
        error("not called")
}

private object AuthenticatedTestCipher : PayloadCipher {
    override val algorithmId = "authenticated-test-boundary"
    override val providesAuthenticatedEncryption = true
    override suspend fun encrypt(plaintext: ByteArray, associatedData: ByteArray, key: ByteArray) =
        CipherPayload(algorithmId, byteArrayOf(1), plaintext.copyOf())
    override suspend fun decrypt(payload: CipherPayload, associatedData: ByteArray, key: ByteArray) =
        payload.ciphertext.copyOf()
}

private class FixedKeyProvider(
    private val fill: Byte = 7,
) : DatabaseKeyProvider {
    override suspend fun loadKey(alias: String) = DatabaseKeyMaterial(ByteArray(32) { fill })
}

private class ObservingKeyProvider : DatabaseKeyProvider {
    var material: DatabaseKeyMaterial? = null

    override suspend fun loadKey(alias: String): DatabaseKeyMaterial =
        DatabaseKeyMaterial(ByteArray(32) { 9 }).also { material = it }
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
