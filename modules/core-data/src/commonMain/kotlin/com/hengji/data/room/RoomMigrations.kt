package com.hengji.data.room

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object MIGRATION_1_2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE insight_preferences " +
                "ADD COLUMN adoptedDeduplicationKeysJson TEXT NOT NULL DEFAULT '[]'",
        )
        connection.execSQL(
            "ALTER TABLE insight_preferences " +
                "ADD COLUMN snoozedUntilEpochMillisByKeyJson TEXT NOT NULL DEFAULT '{}'",
        )
    }
}
