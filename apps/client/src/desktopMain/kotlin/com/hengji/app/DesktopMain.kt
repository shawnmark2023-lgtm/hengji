package com.hengji.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.hengji.app.theme.HengjiTheme
import com.hengji.data.openDesktopProtectedLedger
import com.hengji.app.application.QuickEntryRequest
import java.io.File
import kotlinx.coroutines.runBlocking

fun main() {
    val opening = try {
        Result.success(
            runBlocking {
                openDesktopProtectedLedger(desktopDataDirectory().toPath())
            },
        )
    } catch (error: Exception) {
        Result.failure(error)
    }
    application {
        var quickEntrySequence by remember { mutableStateOf(0L) }
        var quickEntryShortcutStatus by remember {
            mutableStateOf("应用内快捷记账：Ctrl+Shift+N。")
        }
        DisposableEffect(Unit) {
            val hotkey = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                WindowsGlobalQuickEntryHotkey(
                    onTriggered = { quickEntrySequence += 1 },
                    onStatus = { quickEntryShortcutStatus = it },
                ).also { it.start() }
            } else {
                null
            }
            onDispose { hotkey?.close() }
        }
        val windowState = rememberWindowState(
            width = 1280.dp,
            height = 820.dp,
            position = WindowPosition.PlatformDefault,
        )
        val personalInsightModelProvider = remember {
            BuiltInPersonalInsightModelProvider {
                desktopBuiltInModelDirectory().absolutePath
            }
        }
        DisposableEffect(personalInsightModelProvider) {
            onDispose(personalInsightModelProvider::close)
        }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "恒迹 HENGJI",
            onPreviewKeyEvent = { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.isCtrlPressed &&
                    event.isShiftPressed &&
                    event.key == Key.N
                ) {
                    quickEntrySequence += 1
                    true
                } else {
                    false
                }
            },
        ) {
            opening.fold(
                onSuccess = { opened ->
                    HengjiApp(
                        repository = opened.repository,
                        userImportDocumentPicker = remember { DesktopImportDocumentPicker() },
                        ledgerExportWriter = remember { DesktopLedgerExportWriter() },
                        seedDemoData = false,
                        quickEntryRequest = QuickEntryRequest(quickEntrySequence),
                        quickEntryShortcutStatus = quickEntryShortcutStatus,
                        personalInsightModelProvider = personalInsightModelProvider,
                    )
                },
                onFailure = {
                    DesktopStorageStartupFailure(
                        onExit = ::exitApplication,
                    )
                },
            )
        }
    }
}

private fun desktopBuiltInModelDirectory(): File {
    System.getenv("HENGJI_MODEL_DIR")
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.let { return it.canonicalFile }
    System.getProperty("compose.application.resources.dir")
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.resolve("models/${BuiltInAiModelManifest.DIRECTORY_NAME}")
        ?.takeIf(File::isDirectory)
        ?.let { return it.canonicalFile }
    return File(
        "third_party/ai/model/common/models/${BuiltInAiModelManifest.DIRECTORY_NAME}",
    ).canonicalFile
}

@androidx.compose.runtime.Composable
private fun DesktopStorageStartupFailure(
    onExit: () -> Unit,
) {
    HengjiTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("无法安全打开本机账本")
                Text("密钥或密文存储不可用。恒迹没有创建明文替代账本，原有文件保持不变。")
                Button(onClick = onExit) {
                    Text("退出")
                }
            }
        }
    }
}

private fun desktopDataDirectory(): File {
    System.getenv("HENGJI_DATA_DIR")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.let { return it }

    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> File(
            System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
            "Hengji",
        )
        os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/Hengji")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share", "hengji")
    }
}
