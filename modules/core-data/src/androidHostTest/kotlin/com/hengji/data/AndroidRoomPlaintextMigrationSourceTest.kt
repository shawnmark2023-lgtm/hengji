package com.hengji.data

import java.io.File
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AndroidRoomPlaintextMigrationSourceTest {
    @Test
    fun verifiedSnapshotRetiresDatabaseSidecarsAndMarker() = withDirectory { directory ->
        runTest {
            val database = File(directory, "hengji.db").apply { writeText("sqlite") }
            val wal = File("${database.path}-wal").apply { writeText("wal") }
            val shm = File("${database.path}-shm").apply { writeText("shm") }
            val reader = FakeAndroidPlaintextLedgerReader(
                snapshots = ArrayDeque(listOf(DemoLedger.snapshot(), DemoLedger.snapshot())),
            )
            val source = source(database, reader)

            assertEquals(DemoLedger.snapshot(), source.snapshotForMigration())
            source.retireAfterVerifiedMigration()

            assertTrue(reader.closed)
            assertFalse(database.exists())
            assertFalse(wal.exists())
            assertFalse(shm.exists())
            assertFalse(File("${database.path}.hengji-retiring").exists())
        }
    }

    @Test
    fun changedSecondSnapshotFailsClosedAndKeepsPlaintext() = withDirectory { directory ->
        runTest {
            val database = File(directory, "hengji.db").apply { writeText("sqlite") }
            val changed = DemoLedger.snapshot().copy(revision = DemoLedger.snapshot().revision + 1)
            val reader = FakeAndroidPlaintextLedgerReader(
                snapshots = ArrayDeque(listOf(DemoLedger.snapshot(), changed)),
            )
            val source = source(database, reader)

            source.snapshotForMigration()
            assertFailsWith<PlaintextLedgerMigrationConflictException> {
                source.retireAfterVerifiedMigration()
            }

            assertTrue(reader.closed)
            assertTrue(database.exists())
            assertFalse(File("${database.path}.hengji-retiring").exists())
        }
    }

    @Test
    fun hardLinkedRetirementBoundaryCompletesWithoutOpeningRoom() = withDirectory { directory ->
        runTest {
            val database = File(directory, "hengji.db").apply { writeText("sqlite") }
            val marker = File("${database.path}.hengji-retiring")
            Files.createLink(marker.toPath(), database.toPath())
            val journal = File("${database.path}-journal").apply { writeText("journal") }
            var readerCreated = false
            val source = AndroidRoomPlaintextMigrationSource(
                databaseFile = database,
                readerFactory = {
                    readerCreated = true
                    FakeAndroidPlaintextLedgerReader(ArrayDeque())
                },
                fileOperations = HostAndroidPlaintextMigrationFileOperations,
            )

            assertEquals(null, source.snapshotForMigration())
            source.retireAfterVerifiedMigration()

            assertFalse(readerCreated)
            assertFalse(database.exists())
            assertFalse(marker.exists())
            assertFalse(journal.exists())
        }
    }

    @Test
    fun unrelatedDatabaseAndRetirementMarkerFailClosed() = withDirectory { directory ->
        runTest {
            val database = File(directory, "hengji.db").apply { writeText("database") }
            File("${database.path}.hengji-retiring").writeText("different marker")
            val source = source(
                database,
                FakeAndroidPlaintextLedgerReader(ArrayDeque(listOf(DemoLedger.snapshot()))),
            )

            assertFailsWith<StorageProtectionException> {
                source.snapshotForMigration()
            }
            assertTrue(database.exists())
        }
    }

    @Test
    fun orphanedSqliteSidecarFailsClosed() = withDirectory { directory ->
        runTest {
            val database = File(directory, "hengji.db")
            File("${database.path}-wal").writeText("orphaned")
            val source = source(
                database,
                FakeAndroidPlaintextLedgerReader(ArrayDeque(listOf(DemoLedger.snapshot()))),
            )

            assertFailsWith<StorageProtectionException> {
                source.snapshotForMigration()
            }
        }
    }

    private fun source(
        database: File,
        reader: FakeAndroidPlaintextLedgerReader,
    ) = AndroidRoomPlaintextMigrationSource(
        databaseFile = database,
        readerFactory = { reader },
        fileOperations = HostAndroidPlaintextMigrationFileOperations,
    )

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("hengji-android-room-migration-").toFile()
        try {
            block(directory)
        } finally {
            Files.walk(directory.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}

private class FakeAndroidPlaintextLedgerReader(
    private val snapshots: ArrayDeque<LedgerSnapshot>,
) : AndroidPlaintextLedgerReader {
    var closed = false
        private set

    override suspend fun snapshot(): LedgerSnapshot =
        snapshots.removeFirstOrNull() ?: error("No fake plaintext snapshot remains")

    override fun close() {
        closed = true
    }
}

private object HostAndroidPlaintextMigrationFileOperations :
    AndroidPlaintextMigrationFileOperations {
    override fun linkWithoutReplacing(source: File, target: File) {
        Files.createLink(target.toPath(), source.toPath())
    }

    override fun unlink(file: File) {
        Files.delete(file.toPath())
    }

    override fun areSameFile(first: File, second: File): Boolean =
        Files.isSameFile(first.toPath(), second.toPath())

    override fun syncDirectory(directory: File) = Unit
}
