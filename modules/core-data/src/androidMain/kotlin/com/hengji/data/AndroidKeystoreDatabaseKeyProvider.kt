package com.hengji.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val ANDROID_KEY_VAULT_DIRECTORY = "hengji-key-vault"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_GCM_NONCE_BYTES = 12
private const val AES_GCM_TAG_BYTES = 16
private const val PROTECTED_KEY_BLOB_BYTES = 4 + AES_GCM_NONCE_BYTES + AES_256_KEY_BYTES + AES_GCM_TAG_BYTES
private val ANDROID_KEY_FILE_MAGIC =
    byteArrayOf('H'.code.toByte(), 'J'.code.toByte(), 'A'.code.toByte(), 1)

internal data class AndroidProtectedKeyBlob(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

internal interface AndroidKeyProtector {
    fun protect(alias: String, plaintext: ByteArray, associatedData: ByteArray): AndroidProtectedKeyBlob

    fun unprotect(alias: String, blob: AndroidProtectedKeyBlob, associatedData: ByteArray): ByteArray
}

internal interface AndroidVaultFileOperations {
    fun isRegularDirectory(directory: File): Boolean

    fun isRegularFile(file: File): Boolean

    fun publishWithoutReplacing(source: File, target: File)

    fun syncDirectory(directory: File)
}

/**
 * Android database-key provider backed by a non-exportable Android Keystore AES key.
 *
 * Only an authenticated, versioned envelope containing the 256-bit data key is stored in no-backup app storage.
 * An existing envelope is never replaced when its wrapping key is missing or authentication fails.
 */
class AndroidKeystoreDatabaseKeyProvider internal constructor(
    rootDirectory: File,
    private val protector: AndroidKeyProtector,
    private val secureRandom: SecureRandom,
    private val fileOperations: AndroidVaultFileOperations,
) : ProvisioningDatabaseKeyProvider {
    constructor(context: Context) : this(
        rootDirectory = File(context.applicationContext.noBackupFilesDir, ANDROID_KEY_VAULT_DIRECTORY),
        protector = AndroidKeystoreKeyProtector(),
        secureRandom = SecureRandom(),
        fileOperations = AndroidOsVaultFileOperations,
    )

    private val root = rootDirectory.absoluteFile
    private val processLock = processLocks.computeIfAbsent(root.path) { Any() }

    override suspend fun loadKey(alias: String): DatabaseKeyMaterial? = withContext(Dispatchers.IO) {
        requireValidDatabaseKeyAlias(alias)
        synchronized(processLock) { loadKeyLocked(alias) }
    }

    override suspend fun loadOrCreateKey(alias: String): DatabaseKeyMaterial = withContext(Dispatchers.IO) {
        requireValidDatabaseKeyAlias(alias)
        synchronized(processLock) {
            withVaultFileLock {
                loadKeyLocked(alias) ?: createKeyLocked(alias)
            }
        }
    }

    private fun loadKeyLocked(alias: String): DatabaseKeyMaterial? {
        val keyFile = keyFile(alias)
        if (!keyFile.exists()) return null
        requireRegularVaultFile(keyFile, "Android protected key blob")
        if (keyFile.length() != PROTECTED_KEY_BLOB_BYTES.toLong()) {
            throw StorageProtectionException("Android protected key blob has an invalid size")
        }
        val encoded = keyFile.readBytes()
        try {
            if (!encoded.copyOfRange(0, ANDROID_KEY_FILE_MAGIC.size).contentEquals(ANDROID_KEY_FILE_MAGIC)) {
                throw StorageProtectionException("Android protected key blob has an unsupported format")
            }
            val nonceStart = ANDROID_KEY_FILE_MAGIC.size
            val ciphertextStart = nonceStart + AES_GCM_NONCE_BYTES
            val blob = AndroidProtectedKeyBlob(
                nonce = encoded.copyOfRange(nonceStart, ciphertextStart),
                ciphertext = encoded.copyOfRange(ciphertextStart, encoded.size),
            )
            val associatedData = associatedData(alias)
            val rawKey = try {
                protector.unprotect(alias, blob, associatedData)
            } catch (error: Exception) {
                throw StorageProtectionException(
                    "Android Keystore could not authenticate the protected database key",
                    error,
                )
            } finally {
                blob.nonce.fill(0)
                blob.ciphertext.fill(0)
                associatedData.fill(0)
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
        val associatedData = associatedData(alias)
        val blob = try {
            protector.protect(alias, rawKey, associatedData)
        } catch (error: Exception) {
            throw StorageProtectionException("Android Keystore could not protect the database key", error)
        } finally {
            associatedData.fill(0)
        }
        if (blob.nonce.size != AES_GCM_NONCE_BYTES ||
            blob.ciphertext.size != AES_256_KEY_BYTES + AES_GCM_TAG_BYTES
        ) {
            blob.nonce.fill(0)
            blob.ciphertext.fill(0)
            rawKey.fill(0)
            throw StorageProtectionException("Android Keystore returned an invalid protected key payload")
        }
        val encoded = ANDROID_KEY_FILE_MAGIC + blob.nonce + blob.ciphertext
        blob.nonce.fill(0)
        blob.ciphertext.fill(0)
        val temporary = File.createTempFile(".$alias-", ".tmp", root)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            fileOperations.publishWithoutReplacing(temporary, keyFile(alias))
            fileOperations.syncDirectory(root)
            return DatabaseKeyMaterial(rawKey)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.EEXIST) {
                return loadKeyLocked(alias)
                    ?: throw StorageProtectionException(
                        "Android key creation raced but no protected key is available",
                        error,
                    )
            }
            throw StorageProtectionException("Android protected key blob could not be persisted", error)
        } catch (error: Exception) {
            throw StorageProtectionException("Android protected key blob could not be persisted", error)
        } finally {
            temporary.delete()
            encoded.fill(0)
            rawKey.fill(0)
        }
    }

    private fun keyFile(alias: String): File =
        File(root, "$alias.keystore").absoluteFile.also {
            check(it.parentFile == root) { "Database key alias escaped the Android key vault" }
        }

    private fun associatedData(alias: String): ByteArray =
        "hengji|android-keystore-database-key|format=1|alias=$alias".encodeToByteArray()

    private fun ensureVaultRoot() {
        if (!root.exists() && !root.mkdirs()) {
            throw StorageProtectionException("Android key vault directory could not be created")
        }
        if (!fileOperations.isRegularDirectory(root)) {
            throw StorageProtectionException("Android key vault root is not a regular directory")
        }
    }

    private fun requireRegularVaultFile(file: File, description: String) {
        if (!fileOperations.isRegularFile(file)) {
            throw StorageProtectionException("$description is not a regular file")
        }
    }

    private fun <T> withVaultFileLock(block: () -> T): T {
        ensureVaultRoot()
        val lockFile = File(root, ".hengji-key-vault.lock").absoluteFile
        if (lockFile.exists()) {
            requireRegularVaultFile(lockFile, "Android key vault lock")
        }
        return RandomAccessFile(lockFile, "rw").use { randomAccess ->
            if (!fileOperations.isRegularFile(lockFile)) {
                throw StorageProtectionException("Android key vault lock is not a regular file")
            }
            randomAccess.channel.lock().use { block() }
        }
    }

    private companion object {
        val processLocks = ConcurrentHashMap<String, Any>()
    }
}

private object AndroidOsVaultFileOperations : AndroidVaultFileOperations {
    override fun isRegularDirectory(directory: File): Boolean =
        OsConstants.S_ISDIR(Os.lstat(directory.path).st_mode)

    override fun isRegularFile(file: File): Boolean =
        OsConstants.S_ISREG(Os.lstat(file.path).st_mode)

    override fun publishWithoutReplacing(source: File, target: File) {
        if (target.exists()) throw ErrnoException("rename", OsConstants.EEXIST)
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

private class AndroidKeystoreKeyProtector : AndroidKeyProtector {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun protect(
        alias: String,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): AndroidProtectedKeyBlob {
        val wrappingKey = loadWrappingKey(alias) ?: generateWrappingKey(alias)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        cipher.updateAAD(associatedData)
        return AndroidProtectedKeyBlob(
            nonce = cipher.iv.copyOf(),
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    override fun unprotect(
        alias: String,
        blob: AndroidProtectedKeyBlob,
        associatedData: ByteArray,
    ): ByteArray {
        val wrappingKey = loadWrappingKey(alias)
            ?: throw StorageProtectionException("Android Keystore wrapping key is unavailable")
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(AES_GCM_TAG_BYTES * 8, blob.nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(blob.ciphertext)
    }

    private fun loadWrappingKey(alias: String): SecretKey? {
        val key = keyStore.getKey(wrappingAlias(alias), null) ?: return null
        return key as? SecretKey
            ?: throw StorageProtectionException("Android Keystore entry has an unexpected key type")
    }

    private fun generateWrappingKey(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                wrappingAlias(alias),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun wrappingAlias(alias: String): String =
        "com.hengji.database-key-wrap.v1.$alias"
}
