package com.hengji.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.hengji.data.room.RoomStoragePolicy
import com.hengji.data.room.createIosLedgerRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    val basePath = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).first() as String
    val appDirectory = "$basePath/Hengji"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = appDirectory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val repository = createIosLedgerRepository(
        absolutePath = "$appDirectory/hengji.db",
        policy = RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
    )
    val presentationHost = IosPresentationHost()
    val importDocumentPicker = IosImportDocumentPicker(
        presentingViewController = presentationHost::acquireImport,
        releasePresentation = presentationHost::releaseImport,
    )
    val ledgerExportWriter = IosLedgerExportWriter(
        presentingViewController = presentationHost::acquireExport,
        releasePresentation = presentationHost::releaseExport,
    )
    return ComposeUIViewController {
        val currentController = LocalUIViewController.current
        SideEffect {
            presentationHost.attach(currentController)
        }
        DisposableEffect(repository, presentationHost) {
            onDispose {
                presentationHost.clear()
                repository.close()
            }
        }
        HengjiApp(
            repository = repository,
            userImportDocumentPicker = importDocumentPicker,
            ledgerExportWriter = ledgerExportWriter,
        )
    }
}

@OptIn(ExperimentalNativeApi::class)
private class IosPresentationHost {
    private var controllerReference: WeakReference<UIViewController>? = null
    private var activePresentation: PresentationKind? = null

    fun attach(controller: UIViewController) {
        controllerReference = WeakReference(controller)
    }

    fun clear() {
        controllerReference?.clear()
        controllerReference = null
        activePresentation = null
    }

    fun acquireImport(): UIViewController? = acquire(PresentationKind.Import)

    fun releaseImport() = release(PresentationKind.Import)

    fun acquireExport(): UIViewController? = acquire(PresentationKind.Export)

    fun releaseExport() = release(PresentationKind.Export)

    private fun acquire(kind: PresentationKind): UIViewController? {
        if (activePresentation != null) return null
        val controller = controllerReference?.get() ?: return null
        if (controller.presentedViewController != null) return null
        activePresentation = kind
        return controller
    }

    private fun release(kind: PresentationKind) {
        if (activePresentation == kind) {
            activePresentation = null
        }
    }

    private enum class PresentationKind {
        Import,
        Export,
    }
}
