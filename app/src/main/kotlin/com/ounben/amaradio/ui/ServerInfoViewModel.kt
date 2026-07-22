package com.ounben.amaradio.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
import com.ounben.amaradio.data.DataStatistics
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.service.SyncWorker
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerInfoViewModel(application: Application) : AndroidViewModel(application) {

    data class ServerInfoUiState(
        val statistics: List<DataStatistics> = emptyList(),
        val localStationCount: Int = 0,
        val lastSyncTime: String = "Never",
        val recentChanges: List<DataRadioStation> = emptyList(),
        val favoriteIds: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val isSyncing: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(ServerInfoUiState())
    val uiState: StateFlow<ServerInfoUiState> = _uiState.asStateFlow()

    private val app = application as AMARadioApp
    private val database = AMARadioDatabase.getDatabase(app)
    private val sharedPref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "last_db_sync_time") {
            val lastSync = prefs.getString("last_db_sync_time", "Never") ?: "Never"
            _uiState.update { it.copy(lastSyncTime = lastSync) }
            // Also refresh local station count and recent changes as they likely changed too
            loadLocalDbInfo()
        }
    }

    init {
        loadStatistics()
        loadLocalDbInfo()
        
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
        
        viewModelScope.launch {
            app.favouriteManager.stationsFlow.collect { favorites ->
                val ids = favorites.map { it.StationUuid }.toSet()
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun loadStatistics(forceUpdate: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(app.httpClient, app, "json/stats", forceUpdate, null)
            }

            if (result != null) {
                val items = withContext(Dispatchers.Default) {
                    DataStatistics.DecodeJson(result).toList()
                }
                _uiState.update { it.copy(statistics = items, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load statistics") }
            }
        }
    }

    fun loadLocalDbInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = database.stationDao().getStationCount()
                val lastSync = sharedPref.getString("last_db_sync_time", "Never") ?: "Never"
                val entities = database.stationDao().getRecentlyChangedStations()
                val stations = entities.map { it.toDataStation() }
                
                _uiState.update { it.copy(
                    localStationCount = count,
                    lastSyncTime = lastSync,
                    recentChanges = stations
                ) }
            } catch (e: Exception) {
                android.util.Log.e("DB_ERROR", "Failed to load local DB info", e)
            }
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(app).enqueue(syncRequest)
            
            // The prefListener will pick up the completion of the worker 
            // when it writes to SharedPreferences. We just need to stop the indicator eventually.
            // We'll keep the delay for UI feedback but the data update is now reactive.
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(isSyncing = false) }
        }
    }
}
