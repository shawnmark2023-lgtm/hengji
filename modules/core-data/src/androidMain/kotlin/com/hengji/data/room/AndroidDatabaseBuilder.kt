package com.hengji.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun androidDatabaseBuilder(context: Context, databaseName: String = "hengji.db"): RoomDatabase.Builder<HengjiDatabase> {
    require(databaseName.isNotBlank()) { "Database name cannot be blank" }
    return Room.databaseBuilder<HengjiDatabase>(context.applicationContext, databaseName)
}

/** Creates the Android persistence boundary without exposing Room to application modules. */
fun createAndroidLedgerRepository(
    context: Context,
    databaseName: String = "hengji.db",
    policy: RoomStoragePolicy = RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
): RoomLedgerRepository = RoomLedgerRepository(
    buildHengjiDatabase(androidDatabaseBuilder(context, databaseName), policy),
)
