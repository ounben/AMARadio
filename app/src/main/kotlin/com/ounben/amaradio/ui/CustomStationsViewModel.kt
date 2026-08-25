package com.ounben.amaradio.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.CustomStationManager
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
import java.util.UUID

class CustomStationsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AMARadioApp
    private val manager = app.customStationManager

    private val _uiState = MutableStateFlow(LocalStationsViewModel.LocalStationsUiState())
    val uiState: StateFlow<LocalStationsViewModel.LocalStationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            manager.stationsFlow.collect { stations ->
                _uiState.update { it.copy(stations = stations, filteredStations = stations) }
            }
        }
    }

    fun addCustomStation(name: String, url: String, iconUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val stationUuid = CustomStationManager.generateUuidFromUrl(url)
            var finalIconUrl = ""

            if (iconUri != null) {
                finalIconUrl = saveIconLocally(iconUri, stationUuid)
            }

            val station = DataRadioStation(
                Name = name,
                StationUuid = stationUuid,
                StreamUrl = url,
                IconUrl = finalIconUrl
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
        val iconDir = File(app.filesDir, "station_pictures").apply { if (!exists()) mkdirs() }
        val iconFile = File(iconDir, "$uuid.jpg")
        
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(iconFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(iconFile).toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        manager.reorder(fromIndex, toIndex)
    }

    fun remove(uuid: String) {
        manager.remove(uuid)
    }
}
