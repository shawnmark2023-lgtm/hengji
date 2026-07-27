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
import com.hengji.data.ProtectedLedgerOpenOutcome
import com.hengji.data.openDesktopProtectedLedger
import com.hengji.app.application.QuickEntryRequest
import com.hengji.app.application.ModelExplanationControl
import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking

fun main() {
    val opening = runCatching {
        runBlocking {
            openDesktopProtectedLedger(desktopDataDirectory().toPath())
        }
    }
    application {
        var quickEntrySequence by remember { mutableStateOf(0L) }
        var quickEntryShortcutStatus by remember {
            mutableStateOf("应用内快捷记账：Ctrl+Shift+N。")
        }
        val consentPreferences = remember {
            Preferences.userRoot().node("com/hengji/model-explanation")
        }
        var modelExplanationEnabled by remember {
            mutableStateOf(consentPreferences.getBoolean("enabled", false))
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

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "衡记 HENGJI",
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
                        seedDemoData = opened.outcome == ProtectedLedgerOpenOutcome.CREATED_EMPTY,
                        quickEntryRequest = QuickEntryRequest(quickEntrySequence),
                        quickEntryShortcutStatus = quickEntryShortcutStatus,
                        modelExplanationControl = ModelExplanationControl(
                            enabled = modelExplanationEnabled,
                            status = if (modelExplanationEnabled) {
                                "已记录本机同意；未配置隐私评审提供方，仍保持零外发并使用离线规则。"
                            } else {
                                "默认关闭；离线规则解释保持可用。"
                            },
                            onEnabledChange = { enabled ->
                                consentPreferences.putBoolean("enabled", enabled)
                                modelExplanationEnabled = enabled
                            },
                        ),
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
