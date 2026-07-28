package com.hengji.app

import com.hengji.app.application.PickedImportDocument
import com.hengji.app.application.UserDocumentPolicy
import com.hengji.app.application.UserDocumentPurpose
import com.hengji.app.application.UserImportDocumentPicker
import com.hengji.app.importflow.ImportDocumentFormat
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileHandle
import platform.Foundation.NSURL
import platform.Foundation.fileHandleForReadingFromURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Presents an iOS document picker without retaining or copying the source document after decoding.
 *
 * The picker requests an OS-managed temporary copy. Some providers still return a security-scoped
 * URL, so access is balanced around the single coordinated and bounded read.
 */
@OptIn(ExperimentalForeignApi::class)
class IosImportDocumentPicker(
    private val presentingViewController: () -> UIViewController?,
    private val releasePresentation: () -> Unit = {},
) : UserImportDocumentPicker {
    private var activeDelegate: DocumentPickerDelegate? = null
    private var activePicker: UIDocumentPickerViewController? = null

    override suspend fun pick(
        format: ImportDocumentFormat,
        purpose: UserDocumentPurpose,
    ): PickedImportDocument? {
        val selectedUrl = withContext(Dispatchers.Main.immediate) {
            pickUrl(format)
        } ?: return null
        return withContext(Dispatchers.Default) {
            readDocument(selectedUrl, format, purpose)
        }
    }

    private suspend fun pickUrl(format: ImportDocumentFormat): NSURL? =
        suspendCancellableCoroutine { continuation ->
            check(activeDelegate == null) { "已有文件选择请求正在进行" }
            val presenter = presentingViewController()
            if (presenter == null) {
                continuation.resumeWith(
                    Result.failure(IllegalStateException("当前无法打开 iOS 文件选择器")),
                )
                return@suspendCancellableCoroutine
            }

            lateinit var delegate: DocumentPickerDelegate
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(
                    when (format) {
                        ImportDocumentFormat.Csv -> UTTypeCommaSeparatedText
                        ImportDocumentFormat.Json -> UTTypeJSON
                    },
                ),
                asCopy = true,
            ).apply {
                allowsMultipleSelection = false
            }

            fun complete(result: Result<NSURL?>) {
                if (activeDelegate !== delegate) return
                activeDelegate = null
                activePicker = null
                picker.delegate = null
                releasePresentation()
                if (!continuation.isActive) return
                continuation.resumeWith(result)
            }

            delegate = DocumentPickerDelegate(::complete)
            picker.delegate = delegate
            activeDelegate = delegate
            activePicker = picker

            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    if (activeDelegate === delegate) {
                        activeDelegate = null
                        activePicker = null
                        picker.delegate = null
                        picker.dismissViewControllerAnimated(true) {
                            releasePresentation()
                        }
                    }
                }
            }

            try {
                presenter.presentViewController(
                    viewControllerToPresent = picker,
                    animated = true,
                    completion = null,
                )
            } catch (failure: Exception) {
                complete(Result.failure(failure))
            }
        }

    private fun readDocument(
        url: NSURL,
        format: ImportDocumentFormat,
        purpose: UserDocumentPurpose,
    ): PickedImportDocument {
        val accessedSecurityScope = url.startAccessingSecurityScopedResource()
        try {
            val bytes = readBoundedCoordinated(
                url = url,
                maximumBytes = UserDocumentPolicy.maximumBytes(purpose),
            )
            return UserDocumentPolicy.decode(
                displayName = url.lastPathComponent.orEmpty(),
                bytes = bytes,
                format = format,
                purpose = purpose,
            )
        } finally {
            if (accessedSecurityScope) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }

    private fun readBoundedCoordinated(url: NSURL, maximumBytes: Int): ByteArray {
        var result: ByteArray? = null
        var accessorFailure: Exception? = null

        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(
            url = url,
            options = 0uL,
            error = null,
        ) { coordinatedUrl ->
            try {
                val safeUrl = requireNotNull(coordinatedUrl) { "所选文件暂时不可用" }
                val handle = requireNotNull(
                    NSFileHandle.fileHandleForReadingFromURL(safeUrl, error = null),
                ) { "无法打开所选文件" }
                try {
                    val data = requireNotNull(
                        handle.readDataUpToLength((maximumBytes + 1).toULong(), error = null),
                    ) { "无法读取所选文件" }
                    val byteCount = data.length
                    require(byteCount in 1uL..maximumBytes.toULong()) {
                        if (byteCount == 0uL) "文件为空" else "文件超过安全上限"
                    }
                    result = requireNotNull(data.bytes) { "无法读取所选文件" }
                        .readBytes(byteCount.toInt())
                } finally {
                    check(handle.closeAndReturnError(error = null)) { "无法安全关闭所选文件" }
                }
            } catch (failure: Exception) {
                accessorFailure = failure
            }
        }
        accessorFailure?.let { throw it }
        return requireNotNull(result) { "无法读取所选文件" }
    }

    private class DocumentPickerDelegate(
        private val complete: (Result<NSURL?>) -> Unit,
    ) : NSObject(), UIDocumentPickerDelegateProtocol {
        private var completed = false

        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            if (completed) return
            completed = true
            val result: Result<NSURL?> = try {
                require(didPickDocumentsAtURLs.size == 1) { "请选择一个文件" }
                Result.success(
                    requireNotNull(didPickDocumentsAtURLs.single() as? NSURL) {
                        "所选文件地址无效"
                    },
                )
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            complete(result)
        }

        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentAtURL: NSURL,
        ) {
            if (completed) return
            completed = true
            complete(Result.success(didPickDocumentAtURL))
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            if (completed) return
            completed = true
            complete(Result.success(null))
        }
    }
}
