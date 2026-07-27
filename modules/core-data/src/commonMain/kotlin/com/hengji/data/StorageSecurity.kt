package com.hengji.data

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val AES_GCM_NONCE_BYTES = 12
private const val AES_GCM_TAG_BYTES = 16
internal const val AES_256_KEY_BYTES = 32
private const val LEGACY_PROTECTED_LEDGER_FORMAT_VERSION = 1
private const val PROTECTED_LEDGER_FORMAT_VERSION = 2
private const val MAX_PROTECTED_LEDGER_BYTES = 36 * 1024 * 1024

open class StorageProtectionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Platform implementation should source key material from Keychain, Keystore, or Credential Locker. */
interface DatabaseKeyProvider {
    suspend fun loadKey(alias: String): DatabaseKeyMaterial?
}

interface ProvisioningDatabaseKeyProvider : DatabaseKeyProvider {
    suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial
}

class DatabaseKeyMaterial(bytes: ByteArray) {
    private val material = bytes.copyOf()
    private var destroyed = false

    init {
        require(bytes.size == AES_256_KEY_BYTES) { "Database key must contain exactly 256 bits" }
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

internal fun requireValidDatabaseKeyAlias(alias: String) {
    require(DATABASE_KEY_ALIAS.matches(alias)) {
        "Database key alias must be 1-64 lowercase ASCII letters, digits, dots, underscores, or hyphens"
    }
}

private val DATABASE_KEY_ALIAS = Regex("[a-z0-9][a-z0-9._-]{0,63}")

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
    suspend fun encrypt(plaintext: ByteArray, associatedData: ByteArray, key: ByteArray): CipherPayload
    suspend fun decrypt(payload: CipherPayload, associatedData: ByteArray, key: ByteArray): ByteArray
}

/**
 * Portable AES-256-GCM backed by platform cryptography providers (JCA on JVM/Android and CryptoKit on Apple).
 * The provider emits [nonce | ciphertext | tag]; the protocol stores the nonce separately for explicit validation.
 */
class Aes256GcmPayloadCipher(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
) : PayloadCipher {
    override val algorithmId: String = ALGORITHM_ID
    override val providesAuthenticatedEncryption: Boolean = true

    override suspend fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
        key: ByteArray,
    ): CipherPayload {
        requireKey(key)
        val cipher = provider.get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
            .cipher()
        val combined = cipher.encrypt(plaintext, associatedData)
        try {
            check(combined.size >= AES_GCM_NONCE_BYTES + AES_GCM_TAG_BYTES) {
                "AES-GCM provider returned a truncated payload"
            }
            return CipherPayload(
                algorithmId = algorithmId,
                nonce = combined.copyOfRange(0, AES_GCM_NONCE_BYTES),
                ciphertext = combined.copyOfRange(AES_GCM_NONCE_BYTES, combined.size),
            )
        } finally {
            combined.fill(0)
        }
    }

    override suspend fun decrypt(
        payload: CipherPayload,
        associatedData: ByteArray,
        key: ByteArray,
    ): ByteArray {
        requireKey(key)
        require(payload.algorithmId == algorithmId) { "Unsupported encrypted payload algorithm" }
        require(payload.nonce.size == AES_GCM_NONCE_BYTES) { "AES-GCM nonce must contain 96 bits" }
        require(payload.ciphertext.size >= AES_GCM_TAG_BYTES) { "AES-GCM ciphertext is truncated" }
        val combined = payload.nonce + payload.ciphertext
        return try {
            provider.get(AES.GCM)
                .keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, key)
                .cipher()
                .decrypt(combined, associatedData)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw StorageProtectionException("Encrypted ledger authentication failed", error)
        } finally {
            combined.fill(0)
        }
    }

    private fun requireKey(key: ByteArray) {
        require(key.size == AES_256_KEY_BYTES) { "AES-256-GCM requires exactly 256 bits of key material" }
    }

    companion object {
        const val ALGORITHM_ID: String = "AES-256-GCM-HENGJI-1"
    }
}

@Serializable
private data class ProtectedLedgerEnvelope(
    val formatVersion: Int,
    val algorithmId: String,
    val nonceBase64: String,
    val ciphertextBase64: String,
    val keyAlias: String? = null,
)

object ProtectedLedgerEnvelopeCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(payload: CipherPayload, keyAlias: String? = null): String {
        require(payload.algorithmId == Aes256GcmPayloadCipher.ALGORITHM_ID) {
            "Unsupported encrypted payload algorithm"
        }
        require(payload.nonce.size == AES_GCM_NONCE_BYTES) { "AES-GCM nonce must contain 96 bits" }
        require(payload.ciphertext.size >= AES_GCM_TAG_BYTES) { "AES-GCM ciphertext is truncated" }
        keyAlias?.let(::requireValidDatabaseKeyAlias)
        return json.encodeToString(
            ProtectedLedgerEnvelope(
                formatVersion = if (keyAlias == null) {
                    LEGACY_PROTECTED_LEDGER_FORMAT_VERSION
                } else {
                    PROTECTED_LEDGER_FORMAT_VERSION
                },
                algorithmId = payload.algorithmId,
                nonceBase64 = Base64.encode(payload.nonce),
                ciphertextBase64 = Base64.encode(payload.ciphertext),
                keyAlias = keyAlias,
            ),
        ).also {
            require(it.encodeToByteArray().size <= MAX_PROTECTED_LEDGER_BYTES) {
                "Encrypted ledger exceeds the protected payload limit"
            }
        }
    }

    fun decode(envelope: String): CipherPayload {
        require(envelope.encodeToByteArray().size <= MAX_PROTECTED_LEDGER_BYTES) {
            "Encrypted ledger exceeds the protected payload limit"
        }
        val decoded = json.decodeFromString<ProtectedLedgerEnvelope>(envelope)
        require(
            decoded.formatVersion == LEGACY_PROTECTED_LEDGER_FORMAT_VERSION ||
                decoded.formatVersion == PROTECTED_LEDGER_FORMAT_VERSION,
        ) {
            "Unsupported encrypted ledger format version"
        }
        require(
            (decoded.formatVersion == LEGACY_PROTECTED_LEDGER_FORMAT_VERSION && decoded.keyAlias == null) ||
                (decoded.formatVersion == PROTECTED_LEDGER_FORMAT_VERSION && decoded.keyAlias != null),
        ) { "Encrypted ledger key metadata does not match its format version" }
        decoded.keyAlias?.let(::requireValidDatabaseKeyAlias)
        require(decoded.algorithmId == Aes256GcmPayloadCipher.ALGORITHM_ID) {
            "Unsupported encrypted payload algorithm"
        }
        require(decoded.nonceBase64.length <= 32) { "Encrypted ledger nonce is malformed" }
        require(decoded.ciphertextBase64.length <= MAX_PROTECTED_LEDGER_BYTES) {
            "Encrypted ledger ciphertext exceeds the protected payload limit"
        }
        val nonce = Base64.decode(decoded.nonceBase64)
        val ciphertext = Base64.decode(decoded.ciphertextBase64)
        require(nonce.size == AES_GCM_NONCE_BYTES) { "AES-GCM nonce must contain 96 bits" }
        require(ciphertext.size >= AES_GCM_TAG_BYTES) { "AES-GCM ciphertext is truncated" }
        return CipherPayload(decoded.algorithmId, nonce, ciphertext)
    }

    fun activeKeyAlias(envelope: String, legacyFallbackAlias: String): String {
        requireValidDatabaseKeyAlias(legacyFallbackAlias)
        require(envelope.encodeToByteArray().size <= MAX_PROTECTED_LEDGER_BYTES)
        val decoded = json.decodeFromString<ProtectedLedgerEnvelope>(envelope)
        require(
            decoded.formatVersion == LEGACY_PROTECTED_LEDGER_FORMAT_VERSION ||
                decoded.formatVersion == PROTECTED_LEDGER_FORMAT_VERSION,
        ) { "Unsupported encrypted ledger format version" }
        return decoded.keyAlias?.also(::requireValidDatabaseKeyAlias) ?: legacyFallbackAlias
    }
}

/**
 * Encryption boundary for portable backups or an encrypted snapshot adapter.
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

    suspend fun exportEnvelope(snapshot: LedgerSnapshot): String =
        ProtectedLedgerEnvelopeCodec.encode(export(snapshot), keyAlias)

    suspend fun restoreEnvelope(envelope: String): LedgerSnapshot {
        require(ProtectedLedgerEnvelopeCodec.activeKeyAlias(envelope, keyAlias) == keyAlias) {
            "Encrypted ledger key alias does not match the configured key"
        }
        return restore(ProtectedLedgerEnvelopeCodec.decode(envelope))
    }

    private suspend fun <T> withKey(block: suspend (ByteArray) -> T): T {
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

    private fun associatedData(): ByteArray =
        (
            "hengji|protected-ledger|format=$LEGACY_PROTECTED_LEDGER_FORMAT_VERSION|" +
                "schema=$LEDGER_EXPORT_SCHEMA_VERSION|key=$keyAlias|algorithm=${cipher.algorithmId}"
        ).encodeToByteArray()
}
