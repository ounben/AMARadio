package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    data class SearchUiState(
        val results: List<DataRadioStation> = emptyList(),
        val favoriteIds: Set<String> = emptySet(),
        val isSearching: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val app = application as AMARadioApp
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            app.favouriteManager.stationsFlow.collect { favorites ->
                val ids = favorites.map { it.StationUuid }.toSet()
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        
        val cleanedQuery = query.trim()
        if (cleanedQuery.length < 2) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) 
            _uiState.update { it.copy(isSearching = true, error = null) }

            // 1. OFFLINE FIRST: Search in Room
            val localResults = withContext(Dispatchers.IO) {
                AMARadioDatabase.getDatabase(app).stationDao().searchStations(cleanedQuery)
            }
            if (localResults.isNotEmpty()) {
                val prioritized = withContext(Dispatchers.Default) {
                    val decoded = localResults.map { it.toDataStation() }
                    decoded.sortedWith(compareByDescending<DataRadioStation> { station ->
                        SearchUtils.calculateScore(station.Name, cleanedQuery)
                    }.thenByDescending { it.ClickCount })
                }
                
                _uiState.update { it.copy(results = prioritized, isSearching = false) }
            }

            // 2. NETWORK SYNC (Background)
            val params = mutableMapOf<String, String>()
            params["name"] = cleanedQuery
            params["order"] = "clickcount" 
            params["reverse"] = "true"
            params["limit"] = "200"
            params["hidebroken"] = "true"

            val result = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(app.httpClient, app, "json/stations/search", false, params)
            }

            if (result != null) {
                val decoded = withContext(Dispatchers.Default) {
                    DataRadioStation.DecodeJson(result) ?: emptyList()
                }
                
                val prioritized = decoded.sortedWith(compareByDescending<DataRadioStation> { station ->
                    SearchUtils.calculateScore(station.Name, cleanedQuery)
                }.thenByDescending { it.ClickCount })

                _uiState.update { it.copy(results = prioritized, isSearching = false) }
            } else if (localResults.isEmpty()) {
                _uiState.update { it.copy(isSearching = false, error = "Search failed") }
            } else {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearResults() {
        _uiState.update { it.copy(results = emptyList(), isSearching = false, error = null) }
    }
}
