package com.ounben.amaradio.database.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ounben.amaradio.database.Converters
import com.ounben.amaradio.history.TrackHistoryDao
import com.ounben.amaradio.history.TrackHistoryEntry

@Database(
    entities = [
        FavoriteEntity::class, 
        HistoryEntity::class, 
        FilterTabEntity::class, 
        TrackHistoryEntry::class,
        CustomStationEntity::class
    ], 
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AMARadioUserDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun filterTabDao(): FilterTabDao
    abstract fun songHistoryDao(): TrackHistoryDao
    abstract fun customStationDao(): CustomStationDao

    val databaseExecutor: java.util.concurrent.Executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable -> 
        Thread(runnable, "UserDatabase Executor") 
    }

    companion object {
        @Volatile
        private var INSTANCE: AMARadioUserDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_station` (
                        `stationUuid` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `streamUrl` TEXT NOT NULL, 
                        `iconUrl` TEXT NOT NULL, 
                        `country` TEXT NOT NULL DEFAULT '', 
                        `countryCode` TEXT NOT NULL DEFAULT '', 
                        `tags` TEXT NOT NULL DEFAULT '', 
                        `language` TEXT NOT NULL DEFAULT '', 
                        `codec` TEXT NOT NULL DEFAULT '', 
                        `bitrate` INTEGER NOT NULL DEFAULT 0, 
                        `displayOrder` INTEGER NOT NULL DEFAULT 0, 
                        `addedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`stationUuid`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add displayOrder column to station_favourite
                db.execSQL("ALTER TABLE `station_favourite` ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0")
                
                // 2. Initialize displayOrder based on existing addedAt (DESC)
                // This ensures the current visual order is preserved as the new base order.
                db.execSQL("""
                    UPDATE `station_favourite` 
                    SET `displayOrder` = (
                        SELECT COUNT(*) 
                        FROM `station_favourite` AS f2 
                        WHERE f2.`addedAt` > `station_favourite`.`addedAt`
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration for station_favourite
                db.execSQL("""
                    CREATE TABLE `station_favourite_new` (
                        `StationUuid` TEXT NOT NULL, `Name` TEXT NOT NULL, `Url` TEXT NOT NULL, 
                        `Homepage` TEXT NOT NULL DEFAULT '', `Favicon` TEXT NOT NULL, 
                        `Country` TEXT NOT NULL, `CountryCode` TEXT NOT NULL, `Tags` TEXT NOT NULL, 
                        `Language` TEXT NOT NULL, `Votes` INTEGER NOT NULL DEFAULT 0, 
                        `Subcountry` TEXT NOT NULL DEFAULT '', `clickcount` INTEGER NOT NULL DEFAULT 0, 
                        `ClickTrend` INTEGER NOT NULL DEFAULT 0, `Codec` TEXT NOT NULL, 
                        `Bitrate` INTEGER NOT NULL, `LastChangeTime` TEXT NOT NULL DEFAULT '', 
                        `Creation` TEXT NOT NULL DEFAULT '', `ChangeUuid` TEXT NOT NULL DEFAULT '', 
                        `LastCheckOkTime` TEXT NOT NULL DEFAULT '', `addedAt` INTEGER NOT NULL, 
                        `displayOrder` INTEGER NOT NULL, PRIMARY KEY(`StationUuid`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `station_favourite_new` 
                    (`StationUuid`, `Name`, `Url`, `Favicon`, `Country`, `CountryCode`, `Tags`, `Language`, `Codec`, `Bitrate`, `addedAt`, `displayOrder`)
                    SELECT `stationUuid`, `name`, `streamUrl`, `iconUrl`, `country`, `countryCode`, `tags`, `language`, `codec`, `bitrate`, `addedAt`, `displayOrder` 
                    FROM `station_favourite`
                """.trimIndent())
                db.execSQL("DROP TABLE `station_favourite`")
                db.execSQL("ALTER TABLE `station_favourite_new` RENAME TO `station_favourite`")

                // Migration for station_history
                db.execSQL("""
                    CREATE TABLE `station_history_new` (
                        `StationUuid` TEXT NOT NULL, `Name` TEXT NOT NULL, `Url` TEXT NOT NULL, 
                        `Homepage` TEXT NOT NULL DEFAULT '', `Favicon` TEXT NOT NULL, 
                        `Country` TEXT NOT NULL, `CountryCode` TEXT NOT NULL, `Tags` TEXT NOT NULL, 
                        `Language` TEXT NOT NULL, `Votes` INTEGER NOT NULL DEFAULT 0, 
                        `Subcountry` TEXT NOT NULL DEFAULT '', `clickcount` INTEGER NOT NULL DEFAULT 0, 
                        `ClickTrend` INTEGER NOT NULL DEFAULT 0, `Codec` TEXT NOT NULL, 
                        `Bitrate` INTEGER NOT NULL, `LastChangeTime` TEXT NOT NULL DEFAULT '', 
                        `Creation` TEXT NOT NULL DEFAULT '', `ChangeUuid` TEXT NOT NULL DEFAULT '', 
                        `LastCheckOkTime` TEXT NOT NULL DEFAULT '', `lastPlayedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`StationUuid`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `station_history_new` 
                    (`StationUuid`, `Name`, `Url`, `Favicon`, `Country`, `CountryCode`, `Tags`, `Language`, `Codec`, `Bitrate`, `lastPlayedAt`)
                    SELECT `stationUuid`, `name`, `streamUrl`, `iconUrl`, `country`, `countryCode`, `tags`, `language`, `codec`, `bitrate`, `lastPlayedAt` 
                    FROM `station_history`
                """.trimIndent())
                db.execSQL("DROP TABLE `station_history`")
                db.execSQL("ALTER TABLE `station_history_new` RENAME TO `station_history`")

                // Migration for custom_station
                db.execSQL("""
                    CREATE TABLE `custom_station_new` (
                        `StationUuid` TEXT NOT NULL, `Name` TEXT NOT NULL, `Url` TEXT NOT NULL, 
                        `Homepage` TEXT NOT NULL DEFAULT '', `Favicon` TEXT NOT NULL, 
                        `Country` TEXT NOT NULL DEFAULT '', `CountryCode` TEXT NOT NULL DEFAULT '', 
                        `Tags` TEXT NOT NULL DEFAULT '', `Language` TEXT NOT NULL DEFAULT '', 
                        `Votes` INTEGER NOT NULL DEFAULT 0, `Subcountry` TEXT NOT NULL DEFAULT '', 
                        `clickcount` INTEGER NOT NULL DEFAULT 0, `ClickTrend` INTEGER NOT NULL DEFAULT 0, 
                        `Codec` TEXT NOT NULL DEFAULT '', `Bitrate` INTEGER NOT NULL DEFAULT 0, 
                        `LastChangeTime` TEXT NOT NULL DEFAULT '', `Creation` TEXT NOT NULL DEFAULT '', 
                        `ChangeUuid` TEXT NOT NULL DEFAULT '', `LastCheckOkTime` TEXT NOT NULL DEFAULT '', 
                        `displayOrder` INTEGER NOT NULL DEFAULT 0, `addedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`StationUuid`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `custom_station_new` 
                    (`StationUuid`, `Name`, `Url`, `Favicon`, `Country`, `CountryCode`, `Tags`, `Language`, `Codec`, `Bitrate`, `displayOrder`, `addedAt`)
                    SELECT `stationUuid`, `name`, `streamUrl`, `iconUrl`, `country`, `countryCode`, `tags`, `language`, `codec`, `bitrate`, `displayOrder`, `addedAt` 
                    FROM `custom_station`
                """.trimIndent())
                db.execSQL("DROP TABLE `custom_station`")
                db.execSQL("ALTER TABLE `custom_station_new` RENAME TO `custom_station`")
            }
        }

        fun getDatabase(context: Context): AMARadioUserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AMARadioUserDatabase::class.java,
                    "user_data.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationFrom(5) // Nur bei künftigen Fehlern ab v5 zerstörerisch
                .addCallback(CALLBACK)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val CALLBACK: Callback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.databaseExecutor?.execute {
                    INSTANCE?.songHistoryDao()?.setLastHistoryItemEndTimeRelative(TrackHistoryEntry.MAX_UNKNOWN_TRACK_DURATION)
                }
            }
        }
    }
}
