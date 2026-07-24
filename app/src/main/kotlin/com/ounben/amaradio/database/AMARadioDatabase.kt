package com.ounben.amaradio.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ounben.amaradio.history.TrackHistoryDao
import com.ounben.amaradio.history.TrackHistoryEntry
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Database(entities = [TrackHistoryEntry::class, StationEntity::class, TagCacheEntity::class, LanguageCacheEntity::class], version = 6)
@TypeConverters(Converters::class)
abstract class AMARadioDatabase : RoomDatabase() {
    abstract fun songHistoryDao(): TrackHistoryDao
    abstract fun stationDao(): StationDao
    abstract fun tagCacheDao(): TagCacheDao
    abstract fun languageCacheDao(): LanguageCacheDao

    val databaseExecutor: Executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "AMARadioDatabase Executor") }

    companion object {
        @Volatile
        private var INSTANCE: AMARadioDatabase? = null

        @JvmStatic
        fun getDatabase(context: Context): AMARadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AMARadioDatabase::class.java,
                    "radio_browser_database"
                )
                    .createFromAsset("databases/radio_browser_database.db")
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(CALLBACK)
                    .fallbackToDestructiveMigration(true)
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
