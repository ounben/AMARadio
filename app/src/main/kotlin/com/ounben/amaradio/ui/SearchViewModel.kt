package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
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
import java.net.URLEncoder

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    data class SearchUiState(
        val results: List<DataRadioStation> = emptyList(),
        val isSearching: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val app = application as AMARadioApp
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            _uiState.update { it.copy(isSearching = true, error = null) }

            val encodedQuery = withContext(Dispatchers.IO) {
                URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            }
            val url = "json/stations/byname/$encodedQuery"

            val result = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(app.httpClient, app, url, false, mapOf("limit" to "20"))
            }

            if (result != null) {
                val decoded = withContext(Dispatchers.Default) {
                    DataRadioStation.DecodeJson(result) ?: emptyList()
                }
                _uiState.update { it.copy(results = decoded, isSearching = false) }
            } else {
                _uiState.update { it.copy(isSearching = false, error = "Search failed") }
            }
        }
    }

    fun clearResults() {
        _uiState.update { it.copy(results = emptyList(), isSearching = false, error = null) }
    }
}
