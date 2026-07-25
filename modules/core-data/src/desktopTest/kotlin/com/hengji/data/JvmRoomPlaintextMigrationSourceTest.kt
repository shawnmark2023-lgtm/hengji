package com.hengji.data

import com.hengji.data.room.RoomStoragePolicy
import com.hengji.data.room.createDesktopLedgerRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class JvmRoomPlaintextMigrationSourceTest {
    @Test
    fun verifiedRoomSnapshotMigratesAndPlaintextArtifactsRetire() = withDirectory { directory ->
        runTest {
            val databasePath = directory.resolve("hengji.db")
            val legacy = createDesktopLedgerRepository(
                databasePath.toString(),
                RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
            )
            legacy.replaceWith(DemoLedger.snapshot())
            val expected = legacy.snapshot(includeDeleted = true)
            legacy.close()

            val opened = ProtectedLedgerRepository.open(
                store = JvmAtomicProtectedLedgerStore(directory.resolve("hengji.ledger.hjenc")),
                keyAlias = "ledger-primary",
                keyProvider = MigrationTestKeyProvider(),
                plaintextSource = requireNotNull(
                    JvmRoomPlaintextMigrationSource.openIfPresent(databasePath),
                ),
            )

            assertEquals(ProtectedLedgerOpenOutcome.MIGRATED_PLAINTEXT, opened.outcome)
            assertEquals(expected, opened.repository.snapshot(includeDeleted = true))
            assertFalse(Files.exists(databasePath))
            assertFalse(Files.exists(directory.resolve("hengji.db-wal")))
            assertFalse(Files.exists(directory.resolve("hengji.db-shm")))
            assertFalse(Files.exists(directory.resolve("hengji.db.hengji-retiring")))
            assertTrue(Files.isRegularFile(directory.resolve("hengji.ledger.hjenc")))
        }
    }

    @Test
    fun authenticatedTargetCompletesRetirementMarkerLeftByCrash() = withDirectory { directory ->
        runTest {
            val store = JvmAtomicProtectedLedgerStore(directory.resolve("hengji.ledger.hjenc"))
            val keys = MigrationTestKeyProvider()
            ProtectedLedgerRepository.open(store, "ledger-primary", keys)
            val marker = directory.resolve("hengji.db.hengji-retiring")
            Files.writeString(marker, "legacy plaintext bytes")
            val source = requireNotNull(
                JvmRoomPlaintextMigrationSource.openIfPresent(directory.resolve("hengji.db")),
            )

            val recovered = ProtectedLedgerRepository.open(
                store,
                "ledger-primary",
                keys,
                plaintextSource = source,
            )

            assertEquals(ProtectedLedgerOpenOutcome.COMPLETED_INTERRUPTED_MIGRATION, recovered.outcome)
            assertFalse(Files.exists(marker))
        }
    }

    @Test
    fun retirementMarkerWithoutEncryptedTargetFailsClosed() = withDirectory { directory ->
        runTest {
            val marker = directory.resolve("hengji.db.hengji-retiring")
            Files.writeString(marker, "only surviving plaintext")
            val source = requireNotNull(
                JvmRoomPlaintextMigrationSource.openIfPresent(directory.resolve("hengji.db")),
            )

            assertFailsWith<StorageProtectionException> {
                ProtectedLedgerRepository.open(
                    JvmAtomicProtectedLedgerStore(directory.resolve("hengji.ledger.hjenc")),
                    "ledger-primary",
                    MigrationTestKeyProvider(),
                    plaintextSource = source,
                )
            }
            assertTrue(Files.exists(marker))
        }
    }

    private fun withDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("hengji-room-migration-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}

private class MigrationTestKeyProvider : ProvisioningDatabaseKeyProvider {
    private val key = ByteArray(32) { 31 }
    private var available = false

    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? =
        if (available) DatabaseKeyMaterial(key) else null

    override suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial {
        available = true
        return DatabaseKeyMaterial(key)
    }
}
