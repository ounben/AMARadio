package com.ounben.amaradio.database.user

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.ui.FilterTabItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Date

object UserDatabaseMigration {
    private const val TAG = "Migration"
    private const val PREF_MIGRATION_DONE = "user_db_migration_done_v1"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun migrateIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        if (sharedPref.getBoolean(PREF_MIGRATION_DONE, false)) {
            return@withContext
        }

        Log.i(TAG, "Starting migration to UserDatabase...")
        val userDb = AMARadioUserDatabase.getDatabase(context)
        val catalogDb = AMARadioDatabase.getDatabase(context)

        try {
            // 1. Migrate Favorites
            val favJson = sharedPref.getString("favourites", null)
            if (!favJson.isNullOrEmpty()) {
                val stations = DataRadioStation.DecodeJson(favJson)
                stations?.forEachIndexed { index, station ->
                    userDb.favoriteDao().insert(station.toFavoriteEntity(index))
                }
                Log.d(TAG, "Migrated ${stations?.size ?: 0} favorites")
            }

            // 2. Migrate History
            val historyJson = sharedPref.getString("history", null)
            if (!historyJson.isNullOrEmpty()) {
                val stations = DataRadioStation.DecodeJson(historyJson)
                stations?.forEachIndexed { index, station ->
                    // Set artificial dates to maintain order (older = earlier)
                    val date = Date(System.currentTimeMillis() - (index * 1000))
                    userDb.historyDao().insert(station.toHistoryEntity(date))
                }
                Log.d(TAG, "Migrated ${stations?.size ?: 0} history items")
            }

            // 3. Migrate Filter Tabs
            val tabsJson = sharedPref.getString("filter_tabs_json", null)
            if (!tabsJson.isNullOrEmpty()) {
                try {
                    val tabs = json.decodeFromString<List<FilterTabItem>>(tabsJson)
                    val entities = tabs.mapIndexed { index, tab -> tab.toEntity(index) }
                    userDb.filterTabDao().insertAll(entities)
                    Log.d(TAG, "Migrated ${tabs.size} filter tabs")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate filter tabs", e)
                }
            }

            // 4. Migrate Track History (Song Titles)
            try {
                val oldEntries = catalogDb.songHistoryDao().getAllHistory()
                if (oldEntries.isNotEmpty()) {
                    oldEntries.forEach { entry ->
                        userDb.songHistoryDao().insert(entry)
                    }
                    Log.d(TAG, "Migrated ${oldEntries.size} song history entries")
                    // Clear old table to save space in catalog DB
                    catalogDb.songHistoryDao().deleteHistory()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Track history migration failed (might not exist yet)", e)
            }

            // Mark as done
            sharedPref.edit().putBoolean(PREF_MIGRATION_DONE, true).apply()
            Log.i(TAG, "Migration to UserDatabase completed successfully.")

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
        }
    }

    private fun DataRadioStation.toFavoriteEntity(order: Int) = FavoriteEntity(
        StationUuid = StationUuid, Name = Name, Url = StreamUrl, Favicon = IconUrl,
        Homepage = HomePageUrl, Country = Country, CountryCode = CountryCode, 
        Tags = TagsAll, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, Subcountry = State, clickcount = ClickCount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime,
        addedAt = Date(), displayOrder = order
    )

    private fun DataRadioStation.toHistoryEntity(date: Date) = HistoryEntity(
        StationUuid = StationUuid, Name = Name, Url = StreamUrl, Favicon = IconUrl,
        Homepage = HomePageUrl, Country = Country, CountryCode = CountryCode, 
        Tags = TagsAll, Language = Language, Codec = Codec, Bitrate = Bitrate,
        Votes = Votes, Subcountry = State, clickcount = ClickCount, 
        ClickTrend = ClickTrend, LastChangeTime = LastChangeTime, 
        Creation = Creation, ChangeUuid = ChangeUuid, LastCheckOkTime = LastCheckOkTime,
        lastPlayedAt = date
    )

    private fun FilterTabItem.toEntity(pos: Int) = FilterTabEntity(
        id = id,
        label = label,
        name = name,
        countryCode = countryCode,
        countryLabel = countryLabel,
        countryEmoji = countryEmoji,
        languageCode = languageCode,
        languageLabel = languageLabel,
        tag = tag,
        sortBy = sortBy,
        reverse = reverse,
        position = pos
    )
}
