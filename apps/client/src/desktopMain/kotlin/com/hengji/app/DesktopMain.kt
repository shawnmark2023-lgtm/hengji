package com.hengji.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.remember
import com.hengji.data.room.RoomStoragePolicy
import com.hengji.data.room.createDesktopLedgerRepository
import java.io.File

fun main() = application {
    val repository = remember {
        val databaseFile = File(desktopDataDirectory(), "hengji.db")
        databaseFile.parentFile.mkdirs()
        createDesktopLedgerRepository(
            absolutePath = databaseFile.absolutePath,
            policy = RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
        )
    }
    val windowState = rememberWindowState(
        width = 1280.dp,
        height = 820.dp,
        position = WindowPosition.PlatformDefault,
    )

    Window(
        onCloseRequest = {
            repository.close()
            exitApplication()
        },
        state = windowState,
        title = "衡记 HENGJI",
    ) {
        HengjiApp(
            repository = repository,
            userImportDocumentPicker = remember { DesktopImportDocumentPicker() },
            ledgerExportWriter = remember { DesktopLedgerExportWriter() },
        )
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
