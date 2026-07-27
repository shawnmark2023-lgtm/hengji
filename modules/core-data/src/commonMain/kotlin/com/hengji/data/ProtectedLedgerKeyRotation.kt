package com.hengji.data

data class ProtectedLedgerKeyRotationResult(
    val repository: ProtectedLedgerRepository,
    val previousKeyAlias: String,
    val activeKeyAlias: String,
    val revision: Long,
)

/**
 * Re-encrypts the authenticated snapshot under a new platform-protected data key.
 *
 * The new key is provisioned before a single envelope CAS. A crash before the CAS leaves the old
 * envelope readable; a crash after it leaves the new alias embedded in the authenticated envelope,
 * so normal startup discovers it without a mutable plaintext selector.
 */
object ProtectedLedgerKeyRotation {
    suspend fun rotate(
        store: ProtectedLedgerStore,
        baseKeyAlias: String,
        replacementKeyAlias: String,
        keyProvider: ProvisioningDatabaseKeyProvider,
        initializationJournal: ProtectedLedgerInitializationJournal =
            KeyBackedProtectedLedgerInitializationJournal(baseKeyAlias, keyProvider),
        cipher: PayloadCipher = Aes256GcmPayloadCipher(),
    ): ProtectedLedgerKeyRotationResult {
        requireValidDatabaseKeyAlias(baseKeyAlias)
        requireValidDatabaseKeyAlias(replacementKeyAlias)
        val currentEnvelope = store.readEnvelope()
            ?: throw StorageProtectionException("Cannot rotate a missing protected ledger")
        val currentAlias = ProtectedLedgerEnvelopeCodec.activeKeyAlias(currentEnvelope, baseKeyAlias)
        require(replacementKeyAlias != currentAlias) { "Replacement key alias must advance the active generation" }
        keyProvider.loadKey(replacementKeyAlias)?.let {
            it.destroy()
            throw StorageProtectionException("Replacement key alias already exists; refusing ambiguous rotation")
        }

        val currentCodec = ProtectedLedgerPayloadCodec(currentAlias, keyProvider, cipher)
        val snapshot = currentCodec.restoreEnvelope(currentEnvelope)
        validateLedgerSnapshot(snapshot)
        keyProvider.loadOrCreateKey(replacementKeyAlias).destroy()
        val replacementCodec = ProtectedLedgerPayloadCodec(replacementKeyAlias, keyProvider, cipher)
        val replacementEnvelope = replacementCodec.exportEnvelope(snapshot)
        if (!store.compareAndSwap(currentEnvelope, replacementEnvelope)) {
            throw ConcurrentLedgerWriteException()
        }
        val committed = store.readEnvelope()
            ?: throw StorageProtectionException("Rotated protected ledger disappeared after commit")
        val verified = replacementCodec.restoreEnvelope(committed)
        if (verified != snapshot) {
            throw StorageProtectionException("Rotated protected ledger did not verify against the source snapshot")
        }
        val reopened = ProtectedLedgerRepository.open(
            store = store,
            keyAlias = baseKeyAlias,
            keyProvider = keyProvider,
            initializationJournal = initializationJournal,
            cipher = cipher,
        )
        return ProtectedLedgerKeyRotationResult(
            repository = reopened.repository,
            previousKeyAlias = currentAlias,
            activeKeyAlias = replacementKeyAlias,
            revision = snapshot.revision,
        )
    }
}
