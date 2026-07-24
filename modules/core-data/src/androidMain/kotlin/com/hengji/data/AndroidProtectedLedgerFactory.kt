package com.hengji.data

import android.content.Context
import java.io.File

private const val ANDROID_LEDGER_KEY_ALIAS = "hengji-ledger-primary"

/**
 * Opens the Android encrypted persistence boundary.
 *
 * A caller migrating an existing Room database must provide its migration source explicitly.
 * Legacy artifacts without that source fail closed instead of creating an unrelated empty ledger.
 */
suspend fun openAndroidProtectedLedger(
    context: Context,
    keyAlias: String = ANDROID_LEDGER_KEY_ALIAS,
    plaintextSource: PlaintextLedgerMigrationSource? = null,
): ProtectedLedgerOpenResult {
    val applicationContext = context.applicationContext
    val legacyDatabase = applicationContext.getDatabasePath("hengji.db").absoluteFile
    val legacyArtifacts = listOf(
        legacyDatabase,
        File("${legacyDatabase.path}-wal"),
        File("${legacyDatabase.path}-shm"),
        File("${legacyDatabase.path}-journal"),
        File("${legacyDatabase.path}.hengji-retiring"),
    )
    if (plaintextSource == null && legacyArtifacts.any(File::exists)) {
        throw StorageProtectionException(
            "Legacy Android plaintext storage exists but no verified migration source was provided",
        )
    }
    return ProtectedLedgerRepository.open(
        store = AndroidAtomicProtectedLedgerStore(applicationContext),
        keyAlias = keyAlias,
        keyProvider = AndroidKeystoreDatabaseKeyProvider(applicationContext),
        plaintextSource = plaintextSource,
    )
}
