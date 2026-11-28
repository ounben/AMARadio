package com.ounben.amaradio.backup

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.FavoriteListEntity
import com.ounben.amaradio.database.user.FavoriteListItemEntity
import com.ounben.amaradio.database.user.FilterTabEntity
import com.ounben.amaradio.database.user.StationAudioEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    @SerialName("version") val version: Int = 4,
    @SerialName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("favorites") val favorites: List<DataRadioStation> = emptyList(),
    @SerialName("customStations") val customStations: List<DataRadioStation> = emptyList(),
    @SerialName("filterTabs") val filterTabs: List<FilterTabEntity> = emptyList(),
    @SerialName("favoriteLists") val favoriteLists: List<FavoriteListEntity> = emptyList(),
    @SerialName("favoriteListItems") val favoriteListItems: List<FavoriteListItemEntity> = emptyList(),
    @SerialName("stationAudioSettings") val stationAudioSettings: List<StationAudioEntity> = emptyList(),
    @SerialName("settings") val settings: Map<String, String> = emptyMap()
)

object BackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportBackup(context: Context, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as AMARadioApp
            val userDb = AMARadioUserDatabase.getDatabase(context)
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)

            val favorites = app.favouriteManager.getList()
            val customStations = app.customStationManager.getList()
            val filterTabs = userDb.filterTabDao().getAllTabs()
            val favoriteLists = userDb.favoriteListDao().getAllLists()
            val favoriteListItems = userDb.favoriteListDao().getAllItems()
            val stationAudioSettings = userDb.stationAudioDao().getAll()

            // Filter out temporary runtime keys
            val exportedSettings = sharedPref.all
                .filterKeys { key ->
                    !key.startsWith("last_") && key != "filter_selected_index"
                }
                .mapValues { it.value?.toString() ?: "" }

            val backupData = BackupData(
                version = 4,
                createdAt = System.currentTimeMillis(),
                favorites = favorites,
                customStations = customStations,
                filterTabs = filterTabs,
                favoriteLists = favoriteLists,
                favoriteListItems = favoriteListItems,
                stationAudioSettings = stationAudioSettings,
                settings = exportedSettings
            )

            val jsonString = json.encodeToString(backupData)

            ZipOutputStream(outputStream.buffered()).use { zipOut ->
                // 1. Write backup.json
                val jsonEntry = ZipEntry("backup.json")
                zipOut.putNextEntry(jsonEntry)
                zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2. Export custom station icons
                val iconDir = File(context.filesDir, "station_icons")
                if (iconDir.exists()) {
                    val allUuids = (favorites.map { it.StationUuid } + customStations.map { it.StationUuid }).toSet()
                    allUuids.forEach { uuid ->
                        val iconFile = File(iconDir, "$uuid.jpg")
                        if (iconFile.exists()) {
                            try {
                                val iconEntry = ZipEntry("icons/$uuid.jpg")
                                zipOut.putNextEntry(iconEntry)
                                FileInputStream(iconFile).use { fis ->
                                    fis.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            } catch (e: Exception) {
                                Log.e("BackupManager", "Failed to zip icon $uuid", e)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Export backup failed", e)
            false
        }
    }

    suspend fun importBackup(context: Context, inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as AMARadioApp
            val iconDir = File(context.filesDir, "station_icons")
            if (!iconDir.exists()) iconDir.mkdirs()

            var backupData: BackupData? = null

            ZipInputStream(inputStream.buffered()).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        val jsonText = zipIn.bufferedReader(Charsets.UTF_8).readText()
                        backupData = json.decodeFromString<BackupData>(jsonText)
                    } else if (entry.name.startsWith("icons/")) {
                        val fileName = File(entry.name).name
                        if (fileName.endsWith(".jpg")) {
                            val targetFile = File(iconDir, fileName)
                            FileOutputStream(targetFile).use { fos ->
                                zipIn.copyTo(fos)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            backupData?.let { data ->
                // 1. Restore Custom Stations sequentially
                if (data.customStations.isNotEmpty()) {
                    val newCustom = data.customStations.filter { !app.customStationManager.has(it.StationUuid) }
                    if (newCustom.isNotEmpty()) {
                        app.customStationManager.addMultiple(newCustom)
                    }
                }

                // 2. Restore Favorites sequentially
                if (data.favorites.isNotEmpty()) {
                    val newFavs = data.favorites.filter { !app.favouriteManager.has(it.StationUuid) }
                    if (newFavs.isNotEmpty()) {
                        app.favouriteManager.addMultiple(newFavs)
                    }
                }

                // 3. Restore Custom Filter Tabs
                val userDb = AMARadioUserDatabase.getDatabase(context)
                if (data.filterTabs.isNotEmpty()) {
                    userDb.filterTabDao().updateAllTabs(data.filterTabs)
                }

                // 3b. Restore Favorite Playlists (v3+, backward-compatible with v1/v2)
                if (data.favoriteLists.isNotEmpty()) {
                    val favoriteListDao = userDb.favoriteListDao()
                    favoriteListDao.insertListsIgnore(data.favoriteLists)
                    if (data.favoriteListItems.isNotEmpty()) {
                        val validListIds = favoriteListDao.getAllLists().mapTo(HashSet()) { it.id }
                        val validItems = data.favoriteListItems.filter { it.listId in validListIds }
                        if (validItems.isNotEmpty()) {
                            favoriteListDao.insertItemsIgnore(validItems)
                        }
                    }
                }

                // 3c. Restore Per-Station Audio Gain Settings (v4+, backward-compatible with v1-v3)
                if (data.stationAudioSettings.isNotEmpty()) {
                    userDb.stationAudioDao().insertAllIgnore(data.stationAudioSettings)
                    app.stationAudioManager.reloadFromDb()
                }

                // 4. Restore App Settings
                if (data.settings.isNotEmpty()) {
                    val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
                    sharedPref.edit {
                        data.settings.forEach { (key, strValue) ->
                            when {
                                strValue == "true" || strValue == "false" -> putBoolean(key, strValue.toBoolean())
                                strValue.toIntOrNull() != null -> putInt(key, strValue.toInt())
                                else -> putString(key, strValue)
                            }
                        }
                    }
                }

                true
            } ?: false
        } catch (e: Exception) {
            Log.e("BackupManager", "Import backup failed", e)
            false
        }
    }
}
