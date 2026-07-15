package com.hengji.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(
        width = 1280.dp,
        height = 820.dp,
        position = WindowPosition.PlatformDefault,
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "衡记 HENGJI",
    ) {
        HengjiApp()
    }
}
