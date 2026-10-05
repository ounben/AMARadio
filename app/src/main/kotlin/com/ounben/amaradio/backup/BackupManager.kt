package com.ounben.amaradio.backup

import android.content.Context
import android.util.Log
import com.ounben.amaradio.AMARadioApp
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
    @SerialName("version") val version: Int = 1,
    @SerialName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("favorites") val favorites: List<DataRadioStation> = emptyList(),
    @SerialName("customStations") val customStations: List<DataRadioStation> = emptyList()
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
            val favorites = app.favouriteManager.getList()
            val customStations = app.customStationManager.getList()

            val backupData = BackupData(
                version = 1,
                createdAt = System.currentTimeMillis(),
                favorites = favorites,
                customStations = customStations
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
                // 1. Restore Custom Stations (into CustomStationManager -> Custom Stations tab)
                data.customStations.forEach { station ->
                    app.customStationManager.add(station)
                }

                // 2. Restore Favorites (into FavouriteManager -> Favorites tab)
                data.favorites.forEach { station ->
                    app.favouriteManager.add(station)
                }
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("BackupManager", "Import backup failed", e)
            false
        }
    }
}
