package com.hengji.data.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

const val HENGJI_DATABASE_VERSION: Int = 4

@Database(
    entities = [
        LedgerMetadataEntity::class,
        TransactionEntity::class,
        AssetEntity::class,
        MaintenanceCostEntity::class,
        UsageEventEntity::class,
        MarketQuoteEntity::class,
        InsightPreferencesEntity::class,
        ImportBatchEntity::class,
        ImportBatchItemEntity::class,
    ],
    version = HENGJI_DATABASE_VERSION,
    exportSchema = true,
)
@ConstructedBy(HengjiDatabaseConstructor::class)
abstract class HengjiDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}

@Suppress("KotlinNoActualForExpect")
expect object HengjiDatabaseConstructor : RoomDatabaseConstructor<HengjiDatabase> {
    override fun initialize(): HengjiDatabase
}

enum class RoomStoragePolicy {
    /** Explicitly accepted only for tests and the local development prototype. */
    ALLOW_UNENCRYPTED_DEVELOPMENT,

    /** Fails until an audited encrypted SQLite driver or payload mapping is integrated. */
    REQUIRE_APPLICATION_ENCRYPTION,
}

class DatabaseEncryptionUnavailableException(message: String) : IllegalStateException(message)

fun buildHengjiDatabase(
    builder: RoomDatabase.Builder<HengjiDatabase>,
    storagePolicy: RoomStoragePolicy,
): HengjiDatabase {
    if (storagePolicy == RoomStoragePolicy.REQUIRE_APPLICATION_ENCRYPTION) {
        throw DatabaseEncryptionUnavailableException(
            "Bundled SQLite is not encrypted. Configure an audited encrypted driver before production use.",
        )
    }
    return builder
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .setDriver(BundledSQLiteDriver())
        // Dispatchers.IO is not available to common metadata in every KMP target.
        // Room executes blocking driver work through this portable background context.
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
