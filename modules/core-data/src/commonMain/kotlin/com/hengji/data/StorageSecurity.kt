package com.hengji.data

class StorageProtectionException(message: String) : IllegalStateException(message)

/** Platform implementation should source key material from Keychain, Keystore, or Credential Locker. */
interface DatabaseKeyProvider {
    suspend fun loadKey(alias: String): DatabaseKeyMaterial?
}

class DatabaseKeyMaterial(bytes: ByteArray) {
    private val material = bytes.copyOf()
    private var destroyed = false

    init {
        require(bytes.size >= 32) { "Database key must contain at least 256 bits" }
    }

    fun copyBytes(): ByteArray {
        check(!destroyed) { "Database key material was destroyed" }
        return material.copyOf()
    }

    fun destroy() {
        material.fill(0)
        destroyed = true
    }
}

data class CipherPayload(
    val algorithmId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    init {
        require(algorithmId.isNotBlank())
        require(nonce.isNotEmpty() && ciphertext.isNotEmpty())
    }
}

interface PayloadCipher {
    val algorithmId: String
    val providesAuthenticatedEncryption: Boolean
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray, key: ByteArray): CipherPayload
    fun decrypt(payload: CipherPayload, associatedData: ByteArray, key: ByteArray): ByteArray
}

/**
 * Encryption boundary for portable backups or a future encrypted-file adapter. No cipher implementation is bundled.
 * Missing keys and unauthenticated algorithms fail closed; plaintext fallback is intentionally absent.
 */
class ProtectedLedgerPayloadCodec(
    private val keyAlias: String,
    private val keyProvider: DatabaseKeyProvider,
    private val cipher: PayloadCipher,
) {
    init {
        require(keyAlias.isNotBlank())
        require(cipher.algorithmId.isNotBlank())
        require(cipher.providesAuthenticatedEncryption) { "Payload cipher must provide authenticated encryption" }
    }

    suspend fun export(snapshot: LedgerSnapshot): CipherPayload = withKey { key ->
        val plaintext = LedgerJsonCodec.export(snapshot).encodeToByteArray()
        try {
            cipher.encrypt(
                plaintext = plaintext,
                associatedData = associatedData(),
                key = key,
            ).also { payload ->
                require(payload.algorithmId == cipher.algorithmId) { "Cipher returned an unexpected algorithm id" }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun restore(payload: CipherPayload): LedgerSnapshot {
        require(payload.algorithmId == cipher.algorithmId) { "Encrypted payload algorithm does not match configured cipher" }
        return withKey { key ->
            val plaintext = cipher.decrypt(payload, associatedData(), key)
            try {
                LedgerJsonCodec.restore(plaintext.decodeToString(throwOnInvalidSequence = true))
            } finally {
                plaintext.fill(0)
            }
        }
    }

    private suspend fun <T> withKey(block: (ByteArray) -> T): T {
        val material = keyProvider.loadKey(keyAlias)
            ?: throw StorageProtectionException("Database key '$keyAlias' is unavailable; plaintext fallback is forbidden")
        val key = material.copyBytes()
        return try {
            block(key)
        } finally {
            key.fill(0)
            material.destroy()
        }
    }

    private fun associatedData(): ByteArray = "hengji-ledger-export-v$LEDGER_EXPORT_SCHEMA_VERSION".encodeToByteArray()
}
