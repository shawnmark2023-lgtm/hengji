package com.hengji.data

import com.hengji.data.room.RoomLedgerRepository
import com.hengji.data.room.RoomStoragePolicy
import com.hengji.data.room.createDesktopLedgerRepository
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val RETIRING_SUFFIX = ".hengji-retiring"

/**
 * Migration adapter for the legacy plaintext Room database on desktop.
 *
 * The database is snapshotted twice (before encryption and immediately before retirement). The
 * main SQLite file is then atomically renamed to a retirement marker, sidecars are removed, and
 * the marker is deleted last. A marker left by a crash can therefore be safely completed only
 * after the encrypted target authenticates on the next launch.
 */
class JvmRoomPlaintextMigrationSource private constructor(
    databasePath: Path,
) : PlaintextLedgerMigrationSource {
    private val database = databasePath.toAbsolutePath().normalize()
    private val root = requireNotNull(database.parent) { "Plaintext database path must have a parent directory" }
    private val retiring = root.resolve("${database.fileName}$RETIRING_SUFFIX")
    private val sidecars = listOf(
        root.resolve("${database.fileName}-wal"),
        root.resolve("${database.fileName}-shm"),
        root.resolve("${database.fileName}-journal"),
    )
    private val mutex = Mutex()
    private var roomRepository: RoomLedgerRepository? = null
    private var capturedSnapshot: LedgerSnapshot? = null

    override suspend fun snapshotForMigration(): LedgerSnapshot? = mutex.withLock {
        withContext(Dispatchers.IO) { validateRoot() }
        if (withContext(Dispatchers.IO) { Files.exists(retiring, LinkOption.NOFOLLOW_LINKS) }) {
            withContext(Dispatchers.IO) {
                requireRegularFile(retiring, "Plaintext retirement marker")
                if (Files.exists(database, LinkOption.NOFOLLOW_LINKS)) {
                    throw StorageProtectionException(
                        "Plaintext database and retirement marker both exist; manual recovery is required",
                    )
                }
            }
            return@withLock null
        }
        withContext(Dispatchers.IO) {
            if (!Files.exists(database, LinkOption.NOFOLLOW_LINKS)) {
                throw StorageProtectionException(
                    "Plaintext SQLite sidecars exist without a database or retirement marker",
                )
            }
            requireRegularFile(database, "Plaintext database")
            sidecars.filter { Files.exists(it, LinkOption.NOFOLLOW_LINKS) }.forEach {
                requireRegularFile(it, "Plaintext SQLite sidecar")
            }
        }
        val repository = roomRepository ?: createDesktopLedgerRepository(
            absolutePath = database.toString(),
            policy = RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
        ).also { roomRepository = it }
        repository.snapshot(includeDeleted = true).also { capturedSnapshot = it }
    }

    override suspend fun retireAfterVerifiedMigration() = mutex.withLock {
        val repository = roomRepository
        if (repository != null) {
            val latest = repository.snapshot(includeDeleted = true)
            if (latest != capturedSnapshot) throw PlaintextLedgerMigrationConflictException()
            repository.close()
            roomRepository = null
        }
        withContext(Dispatchers.IO) {
            validateRoot()
            when {
                Files.exists(retiring, LinkOption.NOFOLLOW_LINKS) -> {
                    requireRegularFile(retiring, "Plaintext retirement marker")
                    if (Files.exists(database, LinkOption.NOFOLLOW_LINKS)) {
                        throw StorageProtectionException(
                            "Plaintext database reappeared during retirement; manual recovery is required",
                        )
                    }
                }

                Files.exists(database, LinkOption.NOFOLLOW_LINKS) -> {
                    requireRegularFile(database, "Plaintext database")
                    moveWithoutReplacing(database, retiring)
                    forceDirectoryMetadataWhenSupported()
                }

                else -> throw StorageProtectionException("Plaintext migration source disappeared before retirement")
            }
            sidecars.forEach { sidecar ->
                if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
                    requireRegularFile(sidecar, "Plaintext SQLite sidecar")
                    Files.delete(sidecar)
                }
            }
            Files.delete(retiring)
            forceDirectoryMetadataWhenSupported()
        }
    }

    private fun validateRoot() {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw StorageProtectionException("Plaintext database root is not a regular directory")
        }
    }

    private fun requireRegularFile(path: Path, description: String) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw StorageProtectionException("$description is not a regular file")
        }
    }

    private fun moveWithoutReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun forceDirectoryMetadataWhenSupported() {
        try {
            FileChannel.open(root, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: UnsupportedOperationException) {
            // Some JVM/OS pairs cannot open directories as channels.
        } catch (_: java.nio.file.AccessDeniedException) {
            // Windows commonly refuses directory FileChannel handles.
        }
    }

    companion object {
        fun openIfPresent(databasePath: Path): JvmRoomPlaintextMigrationSource? {
            val database = databasePath.toAbsolutePath().normalize()
            val root = requireNotNull(database.parent) { "Plaintext database path must have a parent directory" }
            val names = listOf(
                database,
                root.resolve("${database.fileName}$RETIRING_SUFFIX"),
                root.resolve("${database.fileName}-wal"),
                root.resolve("${database.fileName}-shm"),
                root.resolve("${database.fileName}-journal"),
            )
            return if (names.any { Files.exists(it, LinkOption.NOFOLLOW_LINKS) }) {
                JvmRoomPlaintextMigrationSource(database)
            } else {
                null
            }
        }
    }
}
