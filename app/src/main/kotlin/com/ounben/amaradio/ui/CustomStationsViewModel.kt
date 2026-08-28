package com.ounben.amaradio.ui

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.CustomStationManager
import com.ounben.amaradio.database.toCustomEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CustomStationsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AMARadioApp
    private val manager = app.customStationManager
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(LocalStationsViewModel.LocalStationsUiState())
    val uiState: StateFlow<LocalStationsViewModel.LocalStationsUiState> = _uiState.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "icons_only_favorites_style") {
            refreshGridMode()
        }
    }

    init {
        refreshGridMode()
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
        
        viewModelScope.launch {
            manager.stationsFlow.collect { stations ->
                _uiState.update { it.copy(stations = stations, filteredStations = stations) }
            }
        }
    }

    override fun onCleared() {
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun refreshGridMode() {
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        _uiState.update { it.copy(isGrid = isGrid) }
    }

    fun addCustomStation(name: String, url: String, iconUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            // Check global database first for existing metadata
            val catalogDb = com.ounben.amaradio.database.AMARadioDatabase.getDatabase(app)
            val existingStation = catalogDb.stationDao().getStationByUrl(url)
            
            val stationUuid = existingStation?.stationUuid ?: CustomStationManager.generateUuidFromUrl(url)
            var finalIconUrl = existingStation?.favicon ?: ""

            // Local image provided by user always wins
            if (iconUri != null) {
                finalIconUrl = saveIconLocally(iconUri, stationUuid)
            }

            val station = DataRadioStation(
                Name = name.ifBlank { existingStation?.name ?: "Custom Radio" },
                StationUuid = stationUuid,
                StreamUrl = url,
                IconUrl = finalIconUrl,
                Country = existingStation?.country ?: "",
                CountryCode = existingStation?.countryCode ?: "",
                TagsAll = existingStation?.tags ?: "",
                Language = existingStation?.language ?: "",
                Codec = existingStation?.codec ?: "",
                Bitrate = existingStation?.bitrate ?: 0
            )
            manager.add(station)
        }
    }

    fun updateCustomStation(station: DataRadioStation, newIconUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            var finalIconUrl = station.IconUrl
            if (newIconUri != null) {
                finalIconUrl = saveIconLocally(newIconUri, station.StationUuid)
            }
            val updatedStation = station.copy(IconUrl = finalIconUrl)
            manager.updateStation(updatedStation)
        }
    }

    private suspend fun saveIconLocally(uri: Uri, uuid: String): String = withContext(Dispatchers.IO) {
        val iconDir = File(app.filesDir, "station_icons").apply { if (!exists()) mkdirs() }
        val iconFile = File(iconDir, "$uuid.jpg")
        
        // Force delete old file to ensure overwrite works and cache is invalidated
        if (iconFile.exists()) iconFile.delete()

        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(iconFile).use { output ->
                    input.copyTo(output)
                }
            }
            // Append timestamp as cache-buster for Coil
            Uri.fromFile(iconFile).buildUpon()
                .appendQueryParameter("t", System.currentTimeMillis().toString())
                .build().toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        manager.reorder(fromIndex, toIndex)
    }

    fun updateAllOrder(stations: List<DataRadioStation>) {
        manager.persistOrder(stations)
    }

    fun remove(uuid: String) {
        manager.remove(uuid)
    }
}
