package com.hengji.data

import platform.Foundation.NSFileManager

private const val IOS_LEDGER_KEY_ALIAS = "hengji-ledger-primary"

/**
 * Opens the iOS encrypted persistence boundary.
 *
 * Existing Room artifacts require an explicit migration source. Until that source is supplied,
 * initialization fails without creating a second empty ledger.
 */
suspend fun openIosProtectedLedger(
    applicationSupportDirectory: String,
    keyAlias: String = IOS_LEDGER_KEY_ALIAS,
    plaintextSource: PlaintextLedgerMigrationSource? = null,
): ProtectedLedgerOpenResult {
    val root = applicationSupportDirectory.trimEnd('/')
    require(root.isNotBlank()) { "Application Support directory cannot be blank" }
    val legacyPath = "$root/hengji.db"
    val legacyArtifacts = listOf(
        legacyPath,
        "$legacyPath-wal",
        "$legacyPath-shm",
        "$legacyPath-journal",
        "$legacyPath.hengji-retiring",
    )
    if (plaintextSource == null && legacyArtifacts.any(NSFileManager.defaultManager::fileExistsAtPath)) {
        throw StorageProtectionException(
            "Legacy iOS plaintext storage exists but no verified migration source was provided",
        )
    }
    return ProtectedLedgerRepository.open(
        store = IosAtomicProtectedLedgerStore(root),
        keyAlias = keyAlias,
        keyProvider = IosKeychainDatabaseKeyProvider(),
        plaintextSource = plaintextSource,
    )
}
