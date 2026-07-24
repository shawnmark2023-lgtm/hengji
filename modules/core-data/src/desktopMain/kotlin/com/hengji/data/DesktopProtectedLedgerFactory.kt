package com.hengji.data

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

private const val DESKTOP_LEDGER_KEY_ALIAS = "hengji-ledger-primary"

/**
 * Opens the production desktop persistence boundary and migrates the legacy plaintext Room file
 * behind the encrypted startup gate when it is present.
 */
suspend fun openDesktopProtectedLedger(
    dataDirectory: Path,
    keyAlias: String = DESKTOP_LEDGER_KEY_ALIAS,
): ProtectedLedgerOpenResult {
    val root = dataDirectory.toAbsolutePath().normalize()
    Files.createDirectories(root)
    if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
        throw StorageProtectionException("Desktop data root is not a regular directory")
    }
    val keyProvider = when {
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) ->
            WindowsDpapiDatabaseKeyProvider(root.resolve("key-vault"))

        System.getProperty("os.name").startsWith("Mac", ignoreCase = true) ->
            MacOsKeychainDatabaseKeyProvider()

        else -> throw StorageProtectionException(
            "No audited protected database-key provider is available for this desktop operating system",
        )
    }
    return ProtectedLedgerRepository.open(
        store = JvmAtomicProtectedLedgerStore(root.resolve("hengji.ledger.hjenc")),
        keyAlias = keyAlias,
        keyProvider = keyProvider,
        plaintextSource = JvmRoomPlaintextMigrationSource.openIfPresent(root.resolve("hengji.db")),
    )
}
