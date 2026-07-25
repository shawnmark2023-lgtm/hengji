@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hengji.data

import com.hengji.data.room.RoomLedgerRepository
import com.hengji.data.room.RoomStoragePolicy
import com.hengji.data.room.createIosLedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeRegular

private const val IOS_RETIRING_SUFFIX = ".hengji-retiring"

/**
 * Recoverable migration boundary for the legacy iOS plaintext Room database.
 *
 * The encrypted target is committed and authenticated by [ProtectedLedgerRepository] before this
 * source is retired. Retirement renames the main database to a marker, removes SQLite sidecars,
 * and removes the marker last so an interrupted launch can finish cleanup without inventing data.
 */
class IosRoomPlaintextMigrationSource private constructor(
    private val databasePath: String,
) : PlaintextLedgerMigrationSource {
    private val root = databasePath.substringBeforeLast('/', missingDelimiterValue = "")
    private val retiringPath = "$databasePath$IOS_RETIRING_SUFFIX"
    private val sidecars = listOf(
        "$databasePath-wal",
        "$databasePath-shm",
        "$databasePath-journal",
    )
    private val fileManager = NSFileManager.defaultManager
    private val mutex = Mutex()
    private var roomRepository: RoomLedgerRepository? = null
    private var capturedSnapshot: LedgerSnapshot? = null

    init {
        require(root.isNotBlank()) { "Plaintext database path must have a parent directory" }
    }

    override suspend fun snapshotForMigration(): LedgerSnapshot? = mutex.withLock {
        withContext(Dispatchers.Default) { validateRoot() }
        if (withContext(Dispatchers.Default) { fileManager.fileExistsAtPath(retiringPath) }) {
            withContext(Dispatchers.Default) {
                requireRegularFile(retiringPath, "Plaintext retirement marker")
                if (fileManager.fileExistsAtPath(databasePath)) {
                    throw StorageProtectionException(
                        "Plaintext database and retirement marker both exist; manual recovery is required",
                    )
                }
            }
            return@withLock null
        }
        withContext(Dispatchers.Default) {
            if (!fileManager.fileExistsAtPath(databasePath)) {
                throw StorageProtectionException(
                    "Plaintext SQLite sidecars exist without a database or retirement marker",
                )
            }
            requireRegularFile(databasePath, "Plaintext database")
            sidecars.filter(fileManager::fileExistsAtPath).forEach {
                requireRegularFile(it, "Plaintext SQLite sidecar")
            }
        }
        val repository = roomRepository ?: createIosLedgerRepository(
            absolutePath = databasePath,
            policy = RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
        ).also { roomRepository = it }
        repository.snapshot(includeDeleted = true).also {
            validateLedgerSnapshot(it)
            capturedSnapshot = it
        }
    }

    override suspend fun retireAfterVerifiedMigration() = mutex.withLock {
        val repository = roomRepository
        if (repository != null) {
            val latest = repository.snapshot(includeDeleted = true)
            validateLedgerSnapshot(latest)
            if (latest != capturedSnapshot) throw PlaintextLedgerMigrationConflictException()
            repository.close()
            roomRepository = null
        }
        withContext(Dispatchers.Default) {
            validateRoot()
            if (roomRepository == null &&
                capturedSnapshot == null &&
                !fileManager.fileExistsAtPath(retiringPath)
            ) {
                throw StorageProtectionException(
                    "Plaintext migration source was not snapshotted before retirement",
                )
            }
            when {
                fileManager.fileExistsAtPath(retiringPath) -> {
                    requireRegularFile(retiringPath, "Plaintext retirement marker")
                    if (fileManager.fileExistsAtPath(databasePath)) {
                        throw StorageProtectionException(
                            "Plaintext database reappeared during retirement; manual recovery is required",
                        )
                    }
                }

                fileManager.fileExistsAtPath(databasePath) -> {
                    requireRegularFile(databasePath, "Plaintext database")
                    if (!fileManager.moveItemAtPath(databasePath, retiringPath, error = null)) {
                        throw StorageProtectionException(
                            "Plaintext database could not be moved to its retirement marker",
                        )
                    }
                }

                else -> throw StorageProtectionException(
                    "Plaintext migration source disappeared before retirement",
                )
            }
            sidecars.forEach { sidecar ->
                if (fileManager.fileExistsAtPath(sidecar)) {
                    requireRegularFile(sidecar, "Plaintext SQLite sidecar")
                    if (!fileManager.removeItemAtPath(sidecar, error = null)) {
                        throw StorageProtectionException("Plaintext SQLite sidecar could not be retired")
                    }
                }
            }
            if (!fileManager.removeItemAtPath(retiringPath, error = null)) {
                throw StorageProtectionException("Plaintext retirement marker could not be removed")
            }
        }
    }

    private fun validateRoot() {
        requireFileType(root, NSFileTypeDirectory, "Plaintext database root")
    }

    private fun requireRegularFile(path: String, description: String) {
        requireFileType(path, NSFileTypeRegular, description)
    }

    private fun requireFileType(
        path: String,
        expectedType: String?,
        description: String,
    ) {
        val attributes = fileManager.attributesOfItemAtPath(path, error = null)
            ?: throw StorageProtectionException("$description attributes are unavailable")
        if (attributes[NSFileType] != expectedType) {
            throw StorageProtectionException("$description is not the expected regular filesystem type")
        }
    }

    companion object {
        fun openIfPresent(databasePath: String): IosRoomPlaintextMigrationSource? {
            require(databasePath.isNotBlank()) { "Plaintext database path cannot be blank" }
            val artifacts = listOf(
                databasePath,
                "$databasePath$IOS_RETIRING_SUFFIX",
                "$databasePath-wal",
                "$databasePath-shm",
                "$databasePath-journal",
            )
            return if (artifacts.any(NSFileManager.defaultManager::fileExistsAtPath)) {
                IosRoomPlaintextMigrationSource(databasePath)
            } else {
                null
            }
        }
    }
}
