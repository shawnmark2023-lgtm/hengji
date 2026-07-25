package com.hengji.data

private const val IOS_LEDGER_KEY_ALIAS = "hengji-ledger-primary"

/**
 * Opens the iOS encrypted persistence boundary.
 *
 * Existing Room artifacts are kept behind a recoverable migration source and are retired only
 * after the encrypted target authenticates and matches a second plaintext snapshot.
 */
suspend fun openIosProtectedLedger(
    applicationSupportDirectory: String,
    keyAlias: String = IOS_LEDGER_KEY_ALIAS,
): ProtectedLedgerOpenResult {
    val root = applicationSupportDirectory.trimEnd('/')
    require(root.isNotBlank()) { "Application Support directory cannot be blank" }
    return openIosProtectedLedger(
        applicationSupportDirectory = root,
        keyAlias = keyAlias,
        plaintextSource = IosRoomPlaintextMigrationSource.openIfPresent("$root/hengji.db"),
    )
}

internal suspend fun openIosProtectedLedger(
    applicationSupportDirectory: String,
    keyAlias: String,
    plaintextSource: PlaintextLedgerMigrationSource?,
): ProtectedLedgerOpenResult {
    val root = applicationSupportDirectory.trimEnd('/')
    require(root.isNotBlank()) { "Application Support directory cannot be blank" }
    return ProtectedLedgerRepository.open(
        store = IosAtomicProtectedLedgerStore(root),
        keyAlias = keyAlias,
        keyProvider = IosKeychainDatabaseKeyProvider(),
        plaintextSource = plaintextSource,
    )
}
