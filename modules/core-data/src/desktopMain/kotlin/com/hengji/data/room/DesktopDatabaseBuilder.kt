package com.hengji.data.room

import androidx.room.Room
import androidx.room.RoomDatabase

fun desktopDatabaseBuilder(absolutePath: String): RoomDatabase.Builder<HengjiDatabase> {
    require(absolutePath.isNotBlank()) { "Database path cannot be blank" }
    return Room.databaseBuilder<HengjiDatabase>(name = absolutePath)
}

/** Creates the desktop persistence boundary without exposing Room to application modules. */
fun createDesktopLedgerRepository(
    absolutePath: String,
    policy: RoomStoragePolicy = RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
): RoomLedgerRepository = RoomLedgerRepository(
    buildHengjiDatabase(desktopDatabaseBuilder(absolutePath), policy),
)
