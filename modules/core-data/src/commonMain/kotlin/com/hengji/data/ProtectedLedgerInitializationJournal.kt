package com.hengji.data

/**
 * OS-protected durable state for the narrow interval between announcing an initialization and
 * publishing its first authenticated envelope.
 *
 * Fresh creation and plaintext migration are deliberately distinct. A migration marker must never
 * be interpreted as permission to create an unrelated empty ledger after its source disappears.
 */
enum class ProtectedLedgerInitializationState {
    INITIALIZING_FRESH,
    INITIALIZING_MIGRATION,
    READY,
}

interface ProtectedLedgerInitializationJournal {
    suspend fun readState(): ProtectedLedgerInitializationState?

    suspend fun compareAndSwap(
        expectedState: ProtectedLedgerInitializationState?,
        replacementState: ProtectedLedgerInitializationState,
    ): Boolean
}

/**
 * Represents monotonic initialization state with dedicated platform-protected key records.
 *
 * A plain sidecar would allow a stale or forged `INITIALIZING` value to turn a missing mature
 * envelope into an empty ledger. These markers use the same DPAPI/Keystore/Keychain boundary as the
 * data key. Marker material is never used for encryption and is destroyed immediately after each
 * presence check.
 */
class KeyBackedProtectedLedgerInitializationJournal(
    keyAlias: String,
    private val keyProvider: ProvisioningDatabaseKeyProvider,
) : ProtectedLedgerInitializationJournal {
    private val freshMarkerAlias = markerAlias(keyAlias, "init-fresh-v1")
    private val migrationMarkerAlias = markerAlias(keyAlias, "init-migration-v1")
    private val readyMarkerAlias = markerAlias(keyAlias, "ready-v1")

    override suspend fun readState(): ProtectedLedgerInitializationState? {
        val hasFreshMarker = markerExists(freshMarkerAlias)
        val hasMigrationMarker = markerExists(migrationMarkerAlias)
        if (hasFreshMarker && hasMigrationMarker) {
            throw StorageProtectionException(
                "Protected ledger has conflicting platform-protected initialization markers",
            )
        }
        val hasReadyMarker = markerExists(readyMarkerAlias)
        return when {
            hasReadyMarker -> ProtectedLedgerInitializationState.READY
            hasFreshMarker -> ProtectedLedgerInitializationState.INITIALIZING_FRESH
            hasMigrationMarker -> ProtectedLedgerInitializationState.INITIALIZING_MIGRATION
            else -> null
        }
    }

    override suspend fun compareAndSwap(
        expectedState: ProtectedLedgerInitializationState?,
        replacementState: ProtectedLedgerInitializationState,
    ): Boolean {
        if (readState() != expectedState) return false
        val allowed = when (replacementState) {
            ProtectedLedgerInitializationState.INITIALIZING_FRESH,
            ProtectedLedgerInitializationState.INITIALIZING_MIGRATION,
            -> expectedState == null

            ProtectedLedgerInitializationState.READY ->
                expectedState == null ||
                    expectedState == ProtectedLedgerInitializationState.INITIALIZING_FRESH ||
                    expectedState == ProtectedLedgerInitializationState.INITIALIZING_MIGRATION
        }
        if (!allowed) {
            throw StorageProtectionException("Protected ledger initialization state cannot move backwards")
        }
        val alias = when (replacementState) {
            ProtectedLedgerInitializationState.INITIALIZING_FRESH -> freshMarkerAlias
            ProtectedLedgerInitializationState.INITIALIZING_MIGRATION -> migrationMarkerAlias
            ProtectedLedgerInitializationState.READY -> readyMarkerAlias
        }
        keyProvider.loadOrCreateKey(alias).destroy()
        return readState() == replacementState
    }

    private suspend fun markerExists(alias: String): Boolean =
        keyProvider.loadKey(alias)?.let {
            it.destroy()
            true
        } ?: false

    private fun markerAlias(keyAlias: String, suffix: String): String =
        "$keyAlias.$suffix".also {
            try {
                requireValidDatabaseKeyAlias(it)
            } catch (error: IllegalArgumentException) {
                throw StorageProtectionException(
                    "Protected ledger key alias is too long for initialization markers",
                    error,
                )
            }
        }
}
