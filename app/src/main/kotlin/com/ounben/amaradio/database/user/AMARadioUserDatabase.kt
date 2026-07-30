package com.ounben.amaradio.database.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ounben.amaradio.database.Converters
import com.ounben.amaradio.history.TrackHistoryDao
import com.ounben.amaradio.history.TrackHistoryEntry

@Database(
    entities = [
        FavoriteEntity::class, 
        HistoryEntity::class, 
        FilterTabEntity::class, 
        TrackHistoryEntry::class
    ], 
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AMARadioUserDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun filterTabDao(): FilterTabDao
    abstract fun songHistoryDao(): TrackHistoryDao

    val databaseExecutor: java.util.concurrent.Executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable -> 
        Thread(runnable, "UserDatabase Executor") 
    }

    companion object {
        @Volatile
        private var INSTANCE: AMARadioUserDatabase? = null

        fun getDatabase(context: Context): AMARadioUserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AMARadioUserDatabase::class.java,
                    "user_data.db"
                )
                .fallbackToDestructiveMigration()
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
