package com.hengji.data

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_PROTECTED_LEDGER_FILE_BYTES = 36L * 1024 * 1024

/**
 * JVM/desktop encrypted-ledger file store.
 *
 * Writers sharing this implementation coordinate through a process mutex and an OS file lock.
 * Replacements are written and forced in the destination directory before an atomic move.
 */
class JvmAtomicProtectedLedgerStore(
    ledgerPath: Path,
) : ProtectedLedgerStore {
    private val target = ledgerPath.toAbsolutePath().normalize()
    private val root = requireNotNull(target.parent) { "Protected ledger path must have a parent directory" }
    private val processLock = processLocks.computeIfAbsent(target) { Any() }

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
            require(replacementBytes.size <= MAX_PROTECTED_LEDGER_FILE_BYTES) {
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
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return null
        requireRegularFile(target, "Encrypted ledger")
        val size = Files.size(target)
        if (size !in 1..MAX_PROTECTED_LEDGER_FILE_BYTES) {
            throw StorageProtectionException("Encrypted ledger file has an invalid size")
        }
        val encoded = Files.readAllBytes(target)
        return try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(encoded))
                .toString()
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
        val temporary = Files.createTempFile(root, ".hengji-ledger-", ".tmp")
        try {
            FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS,
            ).use { channel ->
                val buffer = ByteBuffer.wrap(replacementBytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            if (replacingExisting) requireRegularFile(target, "Encrypted ledger")
            try {
                moveIntoPlace(temporary, replacingExisting)
            } catch (_: FileAlreadyExistsException) {
                return false
            }
            forceDirectoryMetadataWhenSupported()
            return true
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun moveIntoPlace(source: Path, replacingExisting: Boolean) {
        val options = if (replacingExisting) {
            arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            arrayOf(StandardCopyOption.ATOMIC_MOVE)
        }
        try {
            Files.move(source, target, *options)
        } catch (_: AtomicMoveNotSupportedException) {
            if (replacingExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source, target)
            }
        }
    }

    private fun validateRootIfPresent() {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw StorageProtectionException("Encrypted ledger root is not a regular directory")
        }
    }

    private fun requireRegularFile(path: Path, description: String) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw StorageProtectionException("$description is not a regular file")
        }
    }

    private fun <T> withLedgerFileLock(block: () -> T): T {
        Files.createDirectories(root)
        validateRootIfPresent()
        val lockFile = root.resolve(".hengji-ledger.lock")
        if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) {
            requireRegularFile(lockFile, "Encrypted ledger lock")
        }
        return FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            channel.lock().use { block() }
        }
    }

    private fun forceDirectoryMetadataWhenSupported() {
        try {
            FileChannel.open(root, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: UnsupportedOperationException) {
            // The replacement file itself was already forced; some JVM/OS pairs cannot open directories.
        } catch (_: java.nio.file.AccessDeniedException) {
            // Windows commonly refuses directory FileChannel handles.
        }
    }

    private companion object {
        val processLocks = ConcurrentHashMap<Path, Any>()
    }
}
