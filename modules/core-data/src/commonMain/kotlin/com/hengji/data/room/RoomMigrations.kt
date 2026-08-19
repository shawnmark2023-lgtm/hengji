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

object MIGRATION_2_3 : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE assets ADD COLUMN saleTargetMinor INTEGER")
    }
}

object MIGRATION_3_4 : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE insight_preferences " +
                "ADD COLUMN feedbackTypeByKeyJson TEXT NOT NULL DEFAULT '{}'",
        )
    }
}

object MIGRATION_4_5 : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE insight_preferences " +
                "ADD COLUMN personalAiEnabled INTEGER NOT NULL DEFAULT 1",
        )
        connection.execSQL(
            "ALTER TABLE insight_preferences " +
                "ADD COLUMN onboardingCompletedAtEpochMillis INTEGER",
        )
        connection.execSQL(
            "ALTER TABLE insight_preferences " +
                "ADD COLUMN personalAnalysisHistoryJson TEXT NOT NULL DEFAULT '[]'",
        )
    }
}

object MIGRATION_5_6 : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE insight_preferences ADD COLUMN monthlyBudgetMinor INTEGER DEFAULT NULL",
        )
    }
}
