package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
import com.ounben.amaradio.data.DataStatistics
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
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(ServerInfoUiState())
    val uiState: StateFlow<ServerInfoUiState> = _uiState.asStateFlow()

    private val app = application as AMARadioApp

    init {
        loadStatistics()
    }

    fun loadStatistics(forceUpdate: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(app.httpClient, app, "json/stats", forceUpdate, null)
            }

            if (result != null) {
                val items = DataStatistics.DecodeJson(result).toList()
                _uiState.update { it.copy(statistics = items, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load statistics") }
            }
        }
    }
}
