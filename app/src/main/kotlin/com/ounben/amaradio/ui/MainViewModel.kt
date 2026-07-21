package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)

    data class MainUiState(
        val isSearching: Boolean = false,
        val searchQuery: String = "",
        val isLoading: Boolean = false,
        val isGridView: Boolean = false,
        val stationsInitialTab: Int = 0,
        val favoriteIds: Set<String> = emptySet()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val app = application as com.ounben.amaradio.AMARadioApp

    init {
        _uiState.update { it.copy(isGridView = sharedPref.getBoolean("icons_only_favorites_style", false)) }
        
        viewModelScope.launch {
            app.favouriteManager.stationsFlow.collect { favorites ->
                val ids = withContext(Dispatchers.Default) {
                    favorites.map { it.StationUuid }.toSet()
                }
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearching = active, searchQuery = if (active) it.searchQuery else "") }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading) }
    }

    fun toggleGridView() {
        val newValue = !_uiState.value.isGridView
        _uiState.update { it.copy(isGridView = newValue) }
        sharedPref.edit().putBoolean("icons_only_favorites_style", newValue).apply()
    }

    fun setStationsInitialTab(tabIndex: Int) {
        _uiState.update { it.copy(stationsInitialTab = tabIndex) }
    }
}
