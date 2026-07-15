package com.hengji.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import com.hengji.data.room.RoomStoragePolicy
import com.hengji.data.room.createIosLedgerRepository
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
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
    return ComposeUIViewController {
        HengjiApp(repository)
    }
}
