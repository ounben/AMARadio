package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.preference.PreferenceManager
import android.content.SharedPreferences

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class LocalStationsViewModel(application: Application, isHistory: Boolean) : AndroidViewModel(application) {

    data class LocalStationsUiState(
        val stations: List<DataRadioStation> = emptyList(),
        val filteredStations: List<DataRadioStation> = emptyList(),
        val isGrid: Boolean = false,
        val query: String = ""
    )

    private val _uiState = MutableStateFlow(LocalStationsUiState())
    val uiState: StateFlow<LocalStationsUiState> = _uiState.asStateFlow()

    private val app = application as AMARadioApp
    private val manager = if (isHistory) app.historyManager else app.favouriteManager
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "icons_only_favorites_style") {
            refreshGridMode()
        }
    }

    init {
        refreshGridMode()
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
        
        viewModelScope.launch {
            manager.stationsFlow.collectLatest { stations ->
                val query = _uiState.value.query
                val filtered = withContext(Dispatchers.Default) {
                    if (query.isEmpty()) stations 
                    else stations.filter { it.Name.contains(query, ignoreCase = true) || it.TagsAll.contains(query, ignoreCase = true) }
                }
                _uiState.update { state ->
                    state.copy(stations = stations, filteredStations = filtered)
                }
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

    fun search(query: String) {
        viewModelScope.launch {
            val stations = _uiState.value.stations
            val filtered = withContext(Dispatchers.Default) {
                if (query.isEmpty()) stations 
                else stations.filter { it.Name.contains(query, ignoreCase = true) || it.TagsAll.contains(query, ignoreCase = true) }
            }
            _uiState.update { state ->
                state.copy(query = query, filteredStations = filtered)
            }
        }
    }
}

class LocalStationsViewModelFactory(private val app: AMARadioApp, private val isHistory: Boolean) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LocalStationsViewModel(app, isHistory) as T
    }
}
