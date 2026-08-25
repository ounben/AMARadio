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
    version = 2,
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

        fun getDatabase(context: Context): AMARadioUserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AMARadioUserDatabase::class.java,
                    "user_data.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationFrom(3) // Nur bei künftigen Fehlern ab v3 zerstörerisch
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
