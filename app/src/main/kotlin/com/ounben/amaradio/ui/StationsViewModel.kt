package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.StationsFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.preference.PreferenceManager
import android.content.SharedPreferences
import java.net.URLEncoder

class StationsViewModel(application: Application) : AndroidViewModel(application) {

    data class StationsUiState(
        val stations: List<DataRadioStation> = emptyList(),
        val filteredStations: List<DataRadioStation> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val isGrid: Boolean = false
    )

    private val _uiState = MutableStateFlow(StationsUiState())
    val uiState: StateFlow<StationsUiState> = _uiState.asStateFlow()

    private val app = application as AMARadioApp
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)
    private var currentUrl: String? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "icons_only_favorites_style") {
            refreshGridMode()
        }
    }

    init {
        refreshGridMode()
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onCleared()
    }

    fun refreshGridMode() {
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        _uiState.update { it.copy(isGrid = isGrid) }
    }

    fun loadStations(url: String, forceUpdate: Boolean = false) {
        if (url.isBlank()) return
        if (currentUrl == url && !forceUpdate && _uiState.value.stations.isNotEmpty()) return
        currentUrl = url

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val showBroken = sharedPref.getBoolean("show_broken", false)
            val params = HashMap<String, String>()
            params["hidebroken"] = (!showBroken).toString()

            val result = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(app.httpClient, app, url, forceUpdate, params)
            }

            if (result != null) {
                val filtered = withContext(Dispatchers.Default) {
                    val stations = DataRadioStation.DecodeJson(result) ?: emptyList()
                    stations.filter { showBroken || it.Working }
                }
                _uiState.update { it.copy(stations = filtered, filteredStations = filtered, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load stations") }
            }
        }
    }

    fun search(searchStyle: StationsFilter.SearchStyle, query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(stations = emptyList(), filteredStations = emptyList()) }
            return
        }
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = when (searchStyle) {
            StationsFilter.SearchStyle.ByName -> "json/stations/byname/$encodedQuery"
            StationsFilter.SearchStyle.ByTagExact -> "json/stations/bytagexact/$encodedQuery"
            StationsFilter.SearchStyle.ByCountryCodeExact -> "json/stations/bycountrycodeexact/$encodedQuery"
            StationsFilter.SearchStyle.ByLanguageExact -> "json/stations/bylanguageexact/$encodedQuery"
            else -> "json/stations/byname/$encodedQuery"
        }
        loadStations(url, forceUpdate = true)
    }

    fun filter(query: String) {
        val stations = _uiState.value.stations
        if (query.isEmpty()) {
            _uiState.update { it.copy(filteredStations = stations) }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val filtered = stations.filter { 
                it.Name.contains(query, ignoreCase = true) || 
                it.TagsAll.contains(query, ignoreCase = true)
            }
            _uiState.update { it.copy(filteredStations = filtered) }
        }
    }

    fun toggleViewMode() {
        val newMode = !_uiState.value.isGrid
        sharedPref.edit().putBoolean("icons_only_favorites_style", newMode).apply()
        _uiState.update { it.copy(isGrid = newMode) }
    }
}
