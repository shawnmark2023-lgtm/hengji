package com.hengji.data

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt.CRYPTPROTECT_UI_FORBIDDEN
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

private const val MAX_DPAPI_BLOB_BYTES = 4 * 1024
private val DPAPI_FILE_MAGIC = byteArrayOf('H'.code.toByte(), 'J'.code.toByte(), 'K'.code.toByte(), 1)

/**
 * Windows-only database-key provider.
 *
 * The 256-bit data-encryption key is protected with current-user DPAPI and only the protected blob is written to
 * disk. An existing but unreadable blob fails closed; it is never replaced with a newly generated key.
 */
class WindowsDpapiDatabaseKeyProvider(
    rootDirectory: Path,
    private val secureRandom: SecureRandom = SecureRandom(),
) : ProvisioningDatabaseKeyProvider {
    private val root = rootDirectory.toAbsolutePath().normalize()
    private val processLock = processLocks.computeIfAbsent(root) { Any() }

    init {
        require(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "Windows DPAPI provider can only run on Windows"
        }
    }

    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? {
        requireValidDatabaseKeyAlias(alias)
        return synchronized(processLock) { loadKeyLocked(alias) }
    }

    override suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial {
        requireValidDatabaseKeyAlias(alias)
        return synchronized(processLock) {
            withVaultFileLock {
                loadKeyLocked(alias) ?: createKeyLocked(alias)
            }
        }
    }

    private fun loadKeyLocked(alias: String): DatabaseKeyMaterial? {
        val keyFile = keyFile(alias)
        if (!Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.isSymbolicLink(keyFile) || !Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS)) {
            throw StorageProtectionException("DPAPI key blob is not a regular file")
        }
        val encoded = Files.readAllBytes(keyFile)
        try {
            if (encoded.size !in (DPAPI_FILE_MAGIC.size + 1)..MAX_DPAPI_BLOB_BYTES) {
                throw StorageProtectionException("DPAPI key blob has an invalid size")
            }
            if (!encoded.copyOfRange(0, DPAPI_FILE_MAGIC.size).contentEquals(DPAPI_FILE_MAGIC)) {
                throw StorageProtectionException("DPAPI key blob has an unsupported format")
            }
            val protectedKey = encoded.copyOfRange(DPAPI_FILE_MAGIC.size, encoded.size)
            val entropy = dpapiEntropy(alias)
            val rawKey = try {
                Crypt32Util.cryptUnprotectData(
                    protectedKey,
                    entropy,
                    CRYPTPROTECT_UI_FORBIDDEN,
                    null,
                )
            } catch (error: Exception) {
                throw StorageProtectionException("DPAPI could not unprotect the database key", error)
            } finally {
                protectedKey.fill(0)
                entropy.fill(0)
            }
            return try {
                DatabaseKeyMaterial(rawKey)
            } finally {
                rawKey.fill(0)
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun createKeyLocked(alias: String): DatabaseKeyMaterial {
        val rawKey = ByteArray(AES_256_KEY_BYTES).also(secureRandom::nextBytes)
        val entropy = dpapiEntropy(alias)
        val protectedKey = try {
            Crypt32Util.cryptProtectData(
                rawKey,
                entropy,
                CRYPTPROTECT_UI_FORBIDDEN,
                "HENGJI database key",
                null,
            )
        } catch (error: Exception) {
            throw StorageProtectionException("DPAPI could not protect the database key", error)
        } finally {
            entropy.fill(0)
        }
        val encoded = DPAPI_FILE_MAGIC + protectedKey
        protectedKey.fill(0)
        val temporary = Files.createTempFile(root, ".$alias-", ".tmp")
        try {
            Files.write(temporary, encoded)
            moveWithoutReplacing(temporary, keyFile(alias))
            return DatabaseKeyMaterial(rawKey)
        } catch (_: FileAlreadyExistsException) {
            return loadKeyLocked(alias)
                ?: throw StorageProtectionException("DPAPI key creation raced but no protected key is available")
        } finally {
            Files.deleteIfExists(temporary)
            encoded.fill(0)
            rawKey.fill(0)
        }
    }

    private fun keyFile(alias: String): Path =
        root.resolve("$alias.dpapi").normalize().also {
            check(it.parent == root) { "Database key alias escaped the DPAPI root" }
        }

    private fun dpapiEntropy(alias: String): ByteArray =
        "hengji|dpapi-database-key|format=1|alias=$alias".encodeToByteArray()

    private fun rejectSymlinkRoot() {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw StorageProtectionException("DPAPI key root is not a regular directory")
        }
    }

    private fun <T> withVaultFileLock(block: () -> T): T {
        Files.createDirectories(root)
        rejectSymlinkRoot()
        val lockFile = root.resolve(".hengji-key-vault.lock")
        if (Files.isSymbolicLink(lockFile)) {
            throw StorageProtectionException("DPAPI key lock is a symbolic link")
        }
        return FileChannel.open(
            lockFile,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            channel.lock().use { block() }
        }
    }

    private fun moveWithoutReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private companion object {
        val processLocks = ConcurrentHashMap<Path, Any>()
    }
}
