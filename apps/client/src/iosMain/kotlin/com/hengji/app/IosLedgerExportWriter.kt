package com.hengji.app

import com.hengji.app.application.LedgerExportPolicy
import com.hengji.app.application.LedgerExportWriter
import com.hengji.app.application.PreparedLedgerExport
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * Exports a local copy through the system document picker.
 *
 * The temporary source file stays inside the app sandbox and is removed after
 * the picker finishes or the calling coroutine is cancelled.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLedgerExportWriter(
    private val presentingViewController: () -> UIViewController?,
    private val releasePresentation: () -> Unit = {},
) : LedgerExportWriter {
    private val exportMutex = Mutex()
    private var pending: PendingExport? = null

    override suspend fun save(
        suggestedFileName: String,
        utf8Content: String,
        mediaType: String,
    ): String? {
        exportMutex.lock()
        try {
            val export = withContext(Dispatchers.Default) {
                LedgerExportPolicy.prepare(suggestedFileName, utf8Content, mediaType)
            }
            val temporaryExport = withContext(Dispatchers.Default) {
                removeOrphanedTemporaryExports()
                writeTemporaryExport(export)
            }
            return try {
                presentDocumentPicker(export, temporaryExport.filePath)
            } finally {
                withContext(NonCancellable + Dispatchers.Default) {
                    removeTemporaryExport(temporaryExport.directoryPath)
                }
            }
        } finally {
            exportMutex.unlock()
        }
    }

    private suspend fun presentDocumentPicker(
        export: PreparedLedgerExport,
        temporaryFilePath: String,
    ): String? = withContext(Dispatchers.Main) {
        val presenter = requireNotNull(presentingViewController()) {
            "当前无法打开 iOS 导出选择器"
        }
        var activePicker: UIDocumentPickerViewController? = null
        try {
            suspendCancellableCoroutine { continuation ->
                check(pending == null) { "An iOS ledger export is already in progress" }
                val picker = UIDocumentPickerViewController(
                    forExportingURLs = listOf(NSURL(fileURLWithPath = temporaryFilePath)),
                    asCopy = true,
                )
                activePicker = picker
                val delegate = ExportPickerDelegate(
                    onPicked = { result ->
                        result.fold(
                            onSuccess = { selectedUrl ->
                                complete(
                                    picker = picker,
                                    locationLabel = selectedUrl.lastPathComponent ?: export.fileName,
                                )
                            },
                            onFailure = { failure ->
                                fail(picker = picker, failure = failure)
                            },
                        )
                    },
                    onCancelled = {
                        complete(picker = picker, locationLabel = null)
                    },
                )
                pending = PendingExport(
                    picker = picker,
                    delegate = delegate,
                    continuation = continuation,
                )
                picker.delegate = delegate
                try {
                    presenter.presentViewController(
                        viewControllerToPresent = picker,
                        animated = true,
                        completion = null,
                    )
                } catch (failure: Exception) {
                    fail(picker = picker, failure = failure)
                }
            }
        } finally {
            withContext(NonCancellable) {
                activePicker?.let { picker ->
                    cancelPendingPickerAndAwaitDismissal(picker)
                }
            }
        }
    }

    private fun complete(
        picker: UIDocumentPickerViewController,
        locationLabel: String?,
    ) {
        val request = pending?.takeIf { it.picker === picker } ?: return
        pending = null
        picker.delegate = null
        releasePresentation()
        if (request.continuation.isActive) {
            request.continuation.resume(locationLabel)
        }
    }

    private fun fail(
        picker: UIDocumentPickerViewController,
        failure: Throwable,
    ) {
        val request = pending?.takeIf { it.picker === picker } ?: return
        pending = null
        picker.delegate = null
        releasePresentation()
        if (request.continuation.isActive) {
            request.continuation.resumeWithException(failure)
        }
    }

    private suspend fun cancelPendingPickerAndAwaitDismissal(picker: UIDocumentPickerViewController) {
        val request = pending?.takeIf { it.picker === picker } ?: return
        pending = null
        picker.delegate = null
        if (request.continuation.isActive) {
            request.continuation.cancel()
        }
        suspendCancellableCoroutine { dismissal ->
            picker.dismissViewControllerAnimated(flag = true) {
                releasePresentation()
                if (dismissal.isActive) {
                    dismissal.resume(Unit)
                }
            }
        }
    }

    private fun writeTemporaryExport(export: PreparedLedgerExport): TemporaryExport {
        val directoryPath = "${NSTemporaryDirectory().trimEnd('/')}/hengji-export-${NSUUID().UUIDString}"
        val fileManager = NSFileManager.defaultManager
        check(
            fileManager.createDirectoryAtPath(
                path = directoryPath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            ),
        ) { "Unable to create the temporary export directory" }

        val filePath = "$directoryPath/${export.fileName}"
        try {
            val data = export.bytes.usePinned { pinnedBytes ->
                NSData.dataWithBytes(
                    bytes = pinnedBytes.addressOf(0),
                    length = export.bytes.size.toULong(),
                )
            }
            check(data.writeToFile(path = filePath, atomically = true)) {
                "Unable to write the temporary ledger export"
            }
        } catch (failure: Exception) {
            removeTemporaryExport(directoryPath)
            throw failure
        }
        return TemporaryExport(filePath = filePath, directoryPath = directoryPath)
    }

    private fun removeOrphanedTemporaryExports() {
        val temporaryRoot = NSTemporaryDirectory().trimEnd('/')
        val fileManager = NSFileManager.defaultManager
        val entries = fileManager.contentsOfDirectoryAtPath(
            path = temporaryRoot,
            error = null,
        ).orEmpty()
        entries.filterIsInstance<String>()
            .filter { it.startsWith(TEMPORARY_EXPORT_PREFIX) }
            .forEach { entry ->
                removeTemporaryExport("$temporaryRoot/$entry")
            }
    }

    private fun removeTemporaryExport(directoryPath: String) {
        val temporaryRoot = NSTemporaryDirectory().trimEnd('/')
        val expectedPrefix = "$temporaryRoot/$TEMPORARY_EXPORT_PREFIX"
        require(directoryPath.startsWith(expectedPrefix)) {
            "拒绝清理应用临时导出目录之外的路径"
        }
        require('/' !in directoryPath.removePrefix(expectedPrefix)) {
            "拒绝递归清理嵌套临时路径"
        }
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(directoryPath)) {
            check(fileManager.removeItemAtPath(path = directoryPath, error = null)) {
                "无法清理本地临时导出；请重试以避免账本副本残留"
            }
        }
    }

    private class ExportPickerDelegate(
        private val onPicked: (Result<NSURL>) -> Unit,
        private val onCancelled: () -> Unit,
    ) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            val result: Result<NSURL> = try {
                require(didPickDocumentsAtURLs.size == 1) { "请选择一个导出位置" }
                Result.success(
                    requireNotNull(didPickDocumentsAtURLs.single() as? NSURL) {
                        "导出位置无效"
                    },
                )
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            onPicked(result)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onCancelled()
        }
    }

    private data class PendingExport(
        val picker: UIDocumentPickerViewController,
        @Suppress("unused")
        val delegate: ExportPickerDelegate,
        val continuation: CancellableContinuation<String?>,
    )

    private data class TemporaryExport(
        val filePath: String,
        val directoryPath: String,
    )

    private companion object {
        const val TEMPORARY_EXPORT_PREFIX = "hengji-export-"
    }
}
