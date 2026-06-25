package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.*
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.station.StationsFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.preference.PreferenceManager
import android.content.SharedPreferences

class CategoriesViewModel(application: Application) : AndroidViewModel(application) {

    data class CategoriesUiState(
        val categories: List<DataCategory> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val isGrid: Boolean = false
    )

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

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

    fun loadCategories(url: String, searchStyle: StationsFilter.SearchStyle, singleUseFilter: Boolean, forceUpdate: Boolean = false) {
        if (url.isBlank()) return
        if (currentUrl == url && !forceUpdate && _uiState.value.categories.isNotEmpty()) return
        currentUrl = url

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val showSingleUseTags = sharedPref.getBoolean("single_use_tags", false)

            val result = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(app.httpClient, app, url, forceUpdate, null)
            }

            if (result != null) {
                val data = withContext(Dispatchers.Default) {
                    val categories = DataCategory.DecodeJson(result).toList()
                    val countryDict = CountryCodeDictionary.instance
                    val flagsDict = CountryFlagsLoader.instance
                    
                    val filtered = categories.filter { 
                        !singleUseFilter || showSingleUseTags || (it.UsedCount > 1)
                    }.onEach { cat ->
                        if (searchStyle == StationsFilter.SearchStyle.ByCountryCodeExact) {
                            cat.Label = countryDict.getCountryByCode(cat.Name)
                            cat.Icon = flagsDict.getFlag(app, cat.Name)
                        }
                    }

                    if (searchStyle == StationsFilter.SearchStyle.ByCountryCodeExact) {
                        filtered.sorted()
                    } else {
                        filtered
                    }
                }
                _uiState.update { it.copy(categories = data, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load categories") }
            }
        }
    }
}
