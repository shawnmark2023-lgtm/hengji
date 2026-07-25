package com.hengji.data

import android.content.Context

private const val ANDROID_LEDGER_KEY_ALIAS = "hengji-ledger-primary"

/**
 * Opens the Android encrypted persistence boundary.
 *
 * Existing Room artifacts are detected inside the data boundary and retired only after the
 * encrypted target authenticates and matches a second plaintext snapshot.
 */
suspend fun openAndroidProtectedLedger(
    context: Context,
    keyAlias: String = ANDROID_LEDGER_KEY_ALIAS,
): ProtectedLedgerOpenResult {
    val applicationContext = context.applicationContext
    return openAndroidProtectedLedger(
        context = applicationContext,
        keyAlias = keyAlias,
        plaintextSource = AndroidRoomPlaintextMigrationSource.openIfPresent(applicationContext),
    )
}

internal suspend fun openAndroidProtectedLedger(
    context: Context,
    keyAlias: String,
    plaintextSource: PlaintextLedgerMigrationSource?,
): ProtectedLedgerOpenResult {
    val applicationContext = context.applicationContext
    val keyProvider = AndroidKeystoreDatabaseKeyProvider(applicationContext)
    return ProtectedLedgerRepository.open(
        store = AndroidAtomicProtectedLedgerStore(applicationContext),
        initializationJournal = KeyBackedProtectedLedgerInitializationJournal(
            keyAlias = keyAlias,
            keyProvider = keyProvider,
        ),
        keyAlias = keyAlias,
        keyProvider = keyProvider,
        plaintextSource = plaintextSource,
    )
}
