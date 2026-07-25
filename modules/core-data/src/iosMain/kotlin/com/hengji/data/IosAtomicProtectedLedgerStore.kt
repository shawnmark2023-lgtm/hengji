@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hengji.data

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorWritingForReplacing
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.fileHandleForWritingToURL
import platform.Foundation.writeToURL

private const val IOS_LEDGER_FILE = "hengji.ledger.hjenc"
private const val MAX_IOS_PROTECTED_LEDGER_BYTES = 36 * 1024 * 1024

/**
 * iOS encrypted-ledger store using coordinated, atomic Foundation file replacement.
 *
 * File coordination serializes cooperating app/extension writers. Every callback re-reads the
 * current envelope before replacement, preserving compare-and-swap semantics.
 */
class IosAtomicProtectedLedgerStore(
    applicationSupportDirectory: String,
) : ProtectedLedgerStore {
    private val root = applicationSupportDirectory.trimEnd('/')
    private val targetPath = "$root/$IOS_LEDGER_FILE"
    private val targetUrl = NSURL.fileURLWithPath(targetPath)
    private val fileManager = NSFileManager.defaultManager

    init {
        require(root.isNotBlank()) { "Protected ledger root cannot be blank" }
    }

    override suspend fun readEnvelope(): String? = withContext(Dispatchers.Default) {
        ensureRoot()
        readEnvelopeLocked()
    }

    override suspend fun compareAndSwap(
        expectedEnvelope: String?,
        replacementEnvelope: String,
    ): Boolean = withContext(Dispatchers.Default) {
        val replacementBytes = replacementEnvelope.encodeToByteArray()
        try {
            require(replacementBytes.isNotEmpty()) { "Protected ledger envelope cannot be empty" }
            require(replacementBytes.size <= MAX_IOS_PROTECTED_LEDGER_BYTES) {
                "Encrypted ledger exceeds the protected file limit"
            }
            ensureRoot()
            var completed = false
            var committed = false
            var failure: Throwable? = null
            NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(
                url = targetUrl,
                options = NSFileCoordinatorWritingForReplacing,
                error = null,
            ) { coordinatedUrl ->
                try {
                    val current = readEnvelopeLocked()
                    if (current == expectedEnvelope) {
                        val data = replacementBytes.usePinned { pinned ->
                            NSData.dataWithBytes(
                                bytes = pinned.addressOf(0),
                                length = replacementBytes.size.toULong(),
                            )
                        }
                        val destination = requireNotNull(coordinatedUrl) {
                            "Encrypted ledger destination is unavailable"
                        }
                        if (!data.writeToURL(destination, atomically = true)) {
                            throw StorageProtectionException("Encrypted ledger could not be atomically published")
                        }
                        val handle = NSFileHandle.fileHandleForWritingToURL(destination, error = null)
                            ?: throw StorageProtectionException("Encrypted ledger could not be opened for synchronization")
                        try {
                            if (!handle.synchronizeAndReturnError(error = null)) {
                                throw StorageProtectionException("Encrypted ledger could not be synchronized")
                            }
                        } finally {
                            handle.closeAndReturnError(error = null)
                        }
                        applyDeviceOnlyFilePolicy(destination)
                        committed = true
                    }
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    completed = true
                }
            }
            failure?.let { throw it }
            if (!completed) {
                throw StorageProtectionException("Encrypted ledger file coordination did not complete")
            }
            committed
        } finally {
            replacementBytes.fill(0)
        }
    }

    private fun readEnvelopeLocked(): String? {
        if (!fileManager.fileExistsAtPath(targetPath)) return null
        requireFileType(targetPath, NSFileTypeRegular, "Encrypted ledger")
        applyDeviceOnlyFilePolicy(targetUrl)
        val data = NSData.dataWithContentsOfFile(targetPath)
            ?: throw StorageProtectionException("Encrypted ledger could not be read")
        val size = data.length
        if (size !in 1uL..MAX_IOS_PROTECTED_LEDGER_BYTES.toULong()) {
            throw StorageProtectionException("Encrypted ledger file has an invalid size")
        }
        val encoded = requireNotNull(data.bytes) { "Encrypted ledger returned no bytes" }
            .readBytes(size.toInt())
        return try {
            encoded.decodeToString(throwOnInvalidSequence = true)
        } catch (error: Exception) {
            throw StorageProtectionException("Encrypted ledger file is not valid UTF-8", error)
        } finally {
            encoded.fill(0)
        }
    }

    private fun ensureRoot() {
        if (!fileManager.fileExistsAtPath(root)) {
            val created = fileManager.createDirectoryAtPath(
                path = root,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            if (!created) throw StorageProtectionException("Encrypted ledger root could not be created")
        }
        requireFileType(root, NSFileTypeDirectory, "Encrypted ledger root")
    }

    private fun applyDeviceOnlyFilePolicy(url: NSURL) {
        val path = requireNotNull(url.path) { "Encrypted ledger destination has no filesystem path" }
        val protected = fileManager.setAttributes(
            attributes = mapOf(NSFileProtectionKey to NSFileProtectionComplete),
            ofItemAtPath = path,
            error = null,
        )
        if (!protected) {
            throw StorageProtectionException("Encrypted ledger file protection could not be applied")
        }
        if (!url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)) {
            throw StorageProtectionException("Encrypted ledger backup exclusion could not be applied")
        }
        val attributes = fileManager.attributesOfItemAtPath(path, error = null)
            ?: throw StorageProtectionException("Encrypted ledger attributes are unavailable after publication")
        if (attributes[NSFileProtectionKey] != NSFileProtectionComplete) {
            throw StorageProtectionException("Encrypted ledger file protection could not be verified")
        }
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
}
