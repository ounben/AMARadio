package net.ounben.AMARadio.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import net.ounben.AMARadio.history.TrackHistoryDao
import net.ounben.AMARadio.history.TrackHistoryEntry
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Database(entities = [TrackHistoryEntry::class], version = 1)
@TypeConverters(Converters::class)
abstract class AMARadioDatabase : RoomDatabase() {
    abstract fun songHistoryDao(): TrackHistoryDao

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
                    "radio_droid_database"
                )
                    .addCallback(CALLBACK)
                    .fallbackToDestructiveMigration()
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
