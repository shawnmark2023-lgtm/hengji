package com.hengji.data

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ANDROID_LEDGER_DIRECTORY = "hengji-ledger"
private const val ANDROID_LEDGER_FILE = "hengji.ledger.hjenc"
private const val MAX_ANDROID_PROTECTED_LEDGER_BYTES = 36L * 1024 * 1024

internal interface AndroidLedgerFileOperations {
    fun publishWithoutReplacing(source: File, target: File)
    fun publishReplacing(source: File, target: File)
    fun syncDirectory(directory: File)
}

/**
 * Android encrypted-ledger store rooted in no-backup app storage.
 *
 * File publication uses Linux link/rename primitives so first creation cannot replace a raced
 * winner and subsequent revisions replace atomically.
 */
class AndroidAtomicProtectedLedgerStore internal constructor(
    targetFile: File,
    private val fileOperations: AndroidLedgerFileOperations,
) : ProtectedLedgerStore {
    constructor(context: Context) : this(
        targetFile = File(
            File(context.applicationContext.noBackupFilesDir, ANDROID_LEDGER_DIRECTORY),
            ANDROID_LEDGER_FILE,
        ),
        fileOperations = AndroidOsLedgerFileOperations,
    )

    private val target = targetFile.absoluteFile
    private val root = requireNotNull(target.parentFile) { "Protected ledger path must have a parent directory" }
    private val processLock = processLocks.computeIfAbsent(target.path) { Any() }

    override suspend fun readEnvelope(): String? = withContext(Dispatchers.IO) {
        synchronized(processLock) {
            validateRootIfPresent()
            readEnvelopeLocked()
        }
    }

    override suspend fun compareAndSwap(
        expectedEnvelope: String?,
        replacementEnvelope: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val replacementBytes = replacementEnvelope.encodeToByteArray()
        try {
            require(replacementBytes.isNotEmpty()) { "Protected ledger envelope cannot be empty" }
            require(replacementBytes.size <= MAX_ANDROID_PROTECTED_LEDGER_BYTES) {
                "Encrypted ledger exceeds the protected file limit"
            }
            synchronized(processLock) {
                withLedgerFileLock {
                    val current = readEnvelopeLocked()
                    if (current != expectedEnvelope) return@withLedgerFileLock false
                    publishReplacement(replacementBytes, replacingExisting = current != null)
                }
            }
        } finally {
            replacementBytes.fill(0)
        }
    }

    private fun readEnvelopeLocked(): String? {
        if (!target.exists()) return null
        requireRegularFile(target, "Encrypted ledger")
        if (target.length() !in 1..MAX_ANDROID_PROTECTED_LEDGER_BYTES) {
            throw StorageProtectionException("Encrypted ledger file has an invalid size")
        }
        val encoded = target.readBytes()
        return try {
            encoded.decodeToString(throwOnInvalidSequence = true)
        } catch (error: Exception) {
            throw StorageProtectionException("Encrypted ledger file is not valid UTF-8", error)
        } finally {
            encoded.fill(0)
        }
    }

    private fun publishReplacement(
        replacementBytes: ByteArray,
        replacingExisting: Boolean,
    ): Boolean {
        val temporary = File.createTempFile(".hengji-ledger-", ".tmp", root)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(replacementBytes)
                output.fd.sync()
            }
            if (replacingExisting) requireRegularFile(target, "Encrypted ledger")
            try {
                if (replacingExisting) {
                    fileOperations.publishReplacing(temporary, target)
                } else {
                    fileOperations.publishWithoutReplacing(temporary, target)
                }
            } catch (error: ErrnoException) {
                if (!replacingExisting && error.errno == OsConstants.EEXIST) return false
                throw StorageProtectionException("Encrypted ledger could not be published", error)
            }
            fileOperations.syncDirectory(root)
            return true
        } finally {
            temporary.delete()
        }
    }

    private fun validateRootIfPresent() {
        if (!root.exists()) return
        if (!root.isDirectory || root.canonicalFile != root) {
            throw StorageProtectionException("Encrypted ledger root is not a regular directory")
        }
    }

    private fun ensureRoot() {
        if (!root.exists() && !root.mkdirs()) {
            throw StorageProtectionException("Encrypted ledger root could not be created")
        }
        validateRootIfPresent()
    }

    private fun requireRegularFile(file: File, description: String) {
        if (!file.isFile || file.canonicalFile != file) {
            throw StorageProtectionException("$description is not a regular file")
        }
    }

    private fun <T> withLedgerFileLock(block: () -> T): T {
        ensureRoot()
        val lockFile = File(root, ".hengji-ledger.lock").absoluteFile
        if (lockFile.exists()) requireRegularFile(lockFile, "Encrypted ledger lock")
        return RandomAccessFile(lockFile, "rw").use { randomAccess ->
            if (lockFile.canonicalFile != lockFile) {
                throw StorageProtectionException("Encrypted ledger lock is not a regular file")
            }
            randomAccess.channel.lock().use { block() }
        }
    }

    private companion object {
        val processLocks = ConcurrentHashMap<String, Any>()
    }
}

private object AndroidOsLedgerFileOperations : AndroidLedgerFileOperations {
    override fun publishWithoutReplacing(source: File, target: File) {
        Os.link(source.path, target.path)
    }

    override fun publishReplacing(source: File, target: File) {
        Os.rename(source.path, target.path)
    }

    override fun syncDirectory(directory: File) {
        val descriptor = Os.open(
            directory.path,
            OsConstants.O_RDONLY,
            0,
        )
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }
}
