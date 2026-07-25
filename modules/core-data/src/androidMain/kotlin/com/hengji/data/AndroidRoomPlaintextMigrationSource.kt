package com.hengji.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.hengji.data.room.RoomLedgerRepository
import com.hengji.data.room.RoomStoragePolicy
import com.hengji.data.room.createAndroidLedgerRepository
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val ANDROID_RETIRING_SUFFIX = ".hengji-retiring"
private const val ANDROID_MIGRATION_LOCK_FILE = ".hengji-room-migration.lock"
private val ANDROID_DATABASE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")

internal interface AndroidPlaintextLedgerReader {
    suspend fun snapshot(): LedgerSnapshot

    fun close()
}

internal interface AndroidPlaintextMigrationFileOperations {
    fun linkWithoutReplacing(source: File, target: File)

    fun unlink(file: File)

    fun areSameFile(first: File, second: File): Boolean

    fun syncDirectory(directory: File)
}

/**
 * Recoverable migration source for the legacy Android Room database.
 *
 * The encrypted repository authenticates a second copy of the snapshot before invoking retirement.
 * Retirement then hard-links the main database to a marker, removes the original name, cleans
 * SQLite sidecars and deletes the marker last. A crash at any boundary leaves either the readable
 * database or a marker that can only be finalized after the encrypted target authenticates.
 */
class AndroidRoomPlaintextMigrationSource internal constructor(
    databaseFile: File,
    private val readerFactory: () -> AndroidPlaintextLedgerReader,
    private val fileOperations: AndroidPlaintextMigrationFileOperations,
) : PlaintextLedgerMigrationSource {
    private val database = databaseFile.absoluteFile
    private val root = requireNotNull(database.parentFile) {
        "Plaintext database path must have a parent directory"
    }
    private val retiring = File("${database.path}$ANDROID_RETIRING_SUFFIX").absoluteFile
    private val sidecars = listOf(
        File("${database.path}-wal").absoluteFile,
        File("${database.path}-shm").absoluteFile,
        File("${database.path}-journal").absoluteFile,
    )
    private val processMutex = processMutexes.computeIfAbsent(database.path) { Mutex() }
    private var reader: AndroidPlaintextLedgerReader? = null
    private var capturedSnapshot: LedgerSnapshot? = null

    override suspend fun snapshotForMigration(): LedgerSnapshot? = withMigrationLock {
        validateRoot()
        if (retiring.exists()) {
            requireRegularFile(retiring, "Plaintext retirement marker")
            if (database.exists()) {
                requireRegularFile(database, "Plaintext database")
                if (!fileOperations.areSameFile(database, retiring)) {
                    throw StorageProtectionException(
                        "Plaintext database and unrelated retirement marker both exist; " +
                            "manual recovery is required",
                    )
                }
            }
            return@withMigrationLock null
        }
        if (!database.exists()) {
            throw StorageProtectionException(
                "Plaintext SQLite sidecars exist without a database or retirement marker",
            )
        }
        requireRegularFile(database, "Plaintext database")
        sidecars.filter(File::exists).forEach {
            requireRegularFile(it, "Plaintext SQLite sidecar")
        }
        val activeReader = reader ?: readerFactory().also { reader = it }
        activeReader.snapshot().also {
            validateLedgerSnapshot(it)
            capturedSnapshot = it
        }
    }

    override suspend fun retireAfterVerifiedMigration() = withMigrationLock {
        val activeReader = reader
        if (activeReader != null) {
            val latest = activeReader.snapshot()
            validateLedgerSnapshot(latest)
            activeReader.close()
            reader = null
            if (latest != capturedSnapshot) throw PlaintextLedgerMigrationConflictException()
        }
        validateRoot()
        if (capturedSnapshot == null && !retiring.exists()) {
            throw StorageProtectionException(
                "Plaintext migration source was not snapshotted before retirement",
            )
        }
        when {
            retiring.exists() -> {
                requireRegularFile(retiring, "Plaintext retirement marker")
                if (database.exists()) {
                    requireRegularFile(database, "Plaintext database")
                    if (!fileOperations.areSameFile(database, retiring)) {
                        throw StorageProtectionException(
                            "Plaintext database reappeared during retirement; manual recovery is required",
                        )
                    }
                    fileOperations.unlink(database)
                    fileOperations.syncDirectory(root)
                }
            }

            database.exists() -> {
                requireRegularFile(database, "Plaintext database")
                fileOperations.linkWithoutReplacing(database, retiring)
                fileOperations.syncDirectory(root)
                fileOperations.unlink(database)
                fileOperations.syncDirectory(root)
            }

            else -> throw StorageProtectionException(
                "Plaintext migration source disappeared before retirement",
            )
        }
        sidecars.forEach { sidecar ->
            if (sidecar.exists()) {
                requireRegularFile(sidecar, "Plaintext SQLite sidecar")
                fileOperations.unlink(sidecar)
            }
        }
        fileOperations.unlink(retiring)
        fileOperations.syncDirectory(root)
    }

    private suspend fun <T> withMigrationLock(block: suspend () -> T): T = processMutex.withLock {
        withContext(Dispatchers.IO) {
            validateRoot()
        }
        val lockFile = File(root, ANDROID_MIGRATION_LOCK_FILE).absoluteFile
        if (lockFile.exists()) requireRegularFile(lockFile, "Plaintext migration lock")
        val randomAccess = withContext(Dispatchers.IO) { RandomAccessFile(lockFile, "rw") }
        val fileLock = try {
            withContext(Dispatchers.IO) { randomAccess.channel.lock() }
        } catch (error: Throwable) {
            randomAccess.close()
            throw error
        }
        try {
            block()
        } finally {
            withContext(Dispatchers.IO) {
                fileLock.close()
                randomAccess.close()
            }
        }
    }

    private fun validateRoot() {
        if (!root.isDirectory || root.canonicalFile != root) {
            throw StorageProtectionException("Plaintext database root is not a regular directory")
        }
    }

    private fun requireRegularFile(file: File, description: String) {
        if (!file.isFile || file.canonicalFile != file) {
            throw StorageProtectionException("$description is not a regular file")
        }
    }

    companion object {
        fun openIfPresent(
            context: Context,
            databaseName: String = "hengji.db",
        ): AndroidRoomPlaintextMigrationSource? {
            require(ANDROID_DATABASE_NAME.matches(databaseName)) {
                "Android plaintext database name must be a simple filename"
            }
            val applicationContext = context.applicationContext
            val database = applicationContext.getDatabasePath(databaseName).absoluteFile
            val artifacts = listOf(
                database,
                File("${database.path}$ANDROID_RETIRING_SUFFIX"),
                File("${database.path}-wal"),
                File("${database.path}-shm"),
                File("${database.path}-journal"),
            )
            if (artifacts.none(File::exists)) return null
            return AndroidRoomPlaintextMigrationSource(
                databaseFile = database,
                readerFactory = {
                    AndroidRoomLedgerReader(
                        createAndroidLedgerRepository(
                            context = applicationContext,
                            databaseName = databaseName,
                            policy = RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
                        ),
                    )
                },
                fileOperations = AndroidOsPlaintextMigrationFileOperations,
            )
        }

        private val processMutexes = ConcurrentHashMap<String, Mutex>()
    }
}

private class AndroidRoomLedgerReader(
    private val repository: RoomLedgerRepository,
) : AndroidPlaintextLedgerReader {
    override suspend fun snapshot(): LedgerSnapshot =
        repository.snapshot(includeDeleted = true)

    override fun close() {
        repository.close()
    }
}

private object AndroidOsPlaintextMigrationFileOperations : AndroidPlaintextMigrationFileOperations {
    override fun linkWithoutReplacing(source: File, target: File) {
        Os.link(source.path, target.path)
    }

    override fun unlink(file: File) {
        Os.remove(file.path)
    }

    override fun areSameFile(first: File, second: File): Boolean {
        val firstStat = Os.stat(first.path)
        val secondStat = Os.stat(second.path)
        return firstStat.st_dev == secondStat.st_dev && firstStat.st_ino == secondStat.st_ino
    }

    override fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }
}
