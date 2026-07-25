package com.hengji.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.hengji.app.theme.HengjiTheme
import com.hengji.data.ProtectedLedgerOpenOutcome
import com.hengji.data.ProtectedLedgerOpenResult
import com.hengji.data.openIosProtectedLedger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import platform.Foundation.NSApplicationSupportDirectory
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
        var openingAttempt by remember { mutableIntStateOf(0) }
        var storageState by remember { mutableStateOf<IosStorageState>(IosStorageState.Loading) }
        val currentController = LocalUIViewController.current
        SideEffect {
            presentationHost.attach(currentController)
        }
        DisposableEffect(presentationHost) {
            onDispose {
                presentationHost.clear()
            }
        }
        LaunchedEffect(openingAttempt) {
            storageState = IosStorageState.Loading
            storageState = runCatching {
                openIosProtectedLedger(appDirectory)
            }.fold(
                onSuccess = IosStorageState::Opened,
                onFailure = { IosStorageState.Failed },
            )
        }
        when (val state = storageState) {
            IosStorageState.Loading -> IosStorageStartupStatus(
                message = "正在安全打开本机账本…",
            )

            is IosStorageState.Opened -> HengjiApp(
                repository = state.result.repository,
                userImportDocumentPicker = importDocumentPicker,
                ledgerExportWriter = ledgerExportWriter,
                seedDemoData = state.result.outcome == ProtectedLedgerOpenOutcome.CREATED_EMPTY,
            )

            IosStorageState.Failed -> IosStorageStartupStatus(
                message = "密钥、密文或旧账本迁移暂时不可用。恒迹没有创建明文替代账本，原有文件保持不变。",
                onRetry = { openingAttempt += 1 },
            )
        }
    }
}

private sealed interface IosStorageState {
    data object Loading : IosStorageState

    data class Opened(val result: ProtectedLedgerOpenResult) : IosStorageState

    data object Failed : IosStorageState
}

@Composable
private fun IosStorageStartupStatus(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    HengjiTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (onRetry == null) {
                    CircularProgressIndicator()
                } else {
                    Text("无法安全打开本机账本")
                }
                Text(message)
                onRetry?.let { retry ->
                    Button(onClick = retry) {
                        Text("重试")
                    }
                }
            }
        }
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
