package com.hengji.data

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}

private object MissingKeyProvider : DatabaseKeyProvider {
    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? = null
}

private object UnauthenticatedCipher : PayloadCipher {
    override val algorithmId = "unsafe-test"
    override val providesAuthenticatedEncryption = false
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray, key: ByteArray) = error("not called")
    override fun decrypt(payload: CipherPayload, associatedData: ByteArray, key: ByteArray) = error("not called")
}

private object AuthenticatedTestCipher : PayloadCipher {
    override val algorithmId = "authenticated-test-boundary"
    override val providesAuthenticatedEncryption = true
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray, key: ByteArray) =
        CipherPayload(algorithmId, byteArrayOf(1), plaintext.copyOf())
    override fun decrypt(payload: CipherPayload, associatedData: ByteArray, key: ByteArray) = payload.ciphertext.copyOf()
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
