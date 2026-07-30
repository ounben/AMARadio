package com.ounben.amaradio.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.history.TrackHistoryDao
import com.ounben.amaradio.history.TrackHistoryEntry
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Database(entities = [StationEntity::class, TagCacheEntity::class, LanguageCacheEntity::class], version = 7)
@TypeConverters(Converters::class)
abstract class AMARadioDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun tagCacheDao(): TagCacheDao
    abstract fun languageCacheDao(): LanguageCacheDao

    // DEPRECATED: Song history moved to UserDatabase
    fun songHistoryDao(): TrackHistoryDao {
        return com.ounben.amaradio.database.user.AMARadioUserDatabase.getDatabase(AMARadioApp.instance).songHistoryDao()
    }

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
                // Cleanup logic moved to UserDatabase where the table now resides
            }
        }
    }
}
