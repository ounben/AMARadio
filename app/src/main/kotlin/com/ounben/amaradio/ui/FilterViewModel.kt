package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.CountryCodeDictionary
import com.ounben.amaradio.Utils
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.EmojiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import java.util.Locale

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    data class FilterUiState(
        val name: String = "",
        val countryCode: String = "",
        val countryLabel: String = "",
        val countryEmoji: String = "",
        val languageCode: String = "",
        val languageLabel: String = "",
        val tag: String = "",
        val sortBy: String = "clickcount",
        val reverse: Boolean = true,
        val isSearching: Boolean = false,
        val stations: List<DataRadioStation> = emptyList(),
        val countries: List<CategoryItem> = emptyList(),
        val languages: List<CategoryItem> = emptyList(),
        val suggestedTags: List<String> = emptyList(),
        val error: String? = null,
        val isGrid: Boolean = false
    )

    data class CategoryItem(val code: String, val label: String, val emoji: String = "")

    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)
    private val app = application as AMARadioApp
    private var tagSearchJob: Job? = null
    private var allTags: List<String> = emptyList()

    init {
        loadSavedFilters()
        fetchMetadata()
        refreshGridMode()
        if (hasAnyFilter()) {
            performSearch()
        }
    }

    fun refreshGridMode() {
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        _uiState.update { it.copy(isGrid = isGrid) }
    }

    private fun loadSavedFilters() {
        _uiState.update { 
            val cCode = sharedPref.getString("filter_country_code", "") ?: ""
            it.copy(
                name = sharedPref.getString("filter_name", "") ?: "",
                countryCode = cCode,
                countryLabel = sharedPref.getString("filter_country_label", "") ?: "",
                countryEmoji = EmojiUtils.getFlagEmoji(cCode) ?: "",
                languageCode = sharedPref.getString("filter_language_code", "") ?: "",
                languageLabel = sharedPref.getString("filter_language_label", "") ?: "",
                tag = sharedPref.getString("filter_tag", "") ?: "",
                sortBy = sharedPref.getString("filter_sort", "clickcount") ?: "clickcount",
                reverse = sharedPref.getBoolean("filter_reverse", true)
            )
        }
    }

    private fun saveFilters() {
        val state = _uiState.value
        sharedPref.edit {
            putString("filter_name", state.name)
            putString("filter_country_code", state.countryCode)
            putString("filter_country_label", state.countryLabel)
            putString("filter_language_code", state.languageCode)
            putString("filter_language_label", state.languageLabel)
            putString("filter_tag", state.tag)
            putString("filter_sort", state.sortBy)
            putBoolean("filter_reverse", state.reverse)
        }
    }

    private fun hasAnyFilter(): Boolean {
        val state = _uiState.value
        return state.name.isNotEmpty() || state.countryCode.isNotEmpty() || state.languageCode.isNotEmpty() || state.tag.isNotEmpty()
    }

    fun onNameChange(newName: String) {
        _uiState.update { it.copy(name = newName) }
    }

    fun onCountrySelect(code: String, label: String) {
        val emoji = EmojiUtils.getFlagEmoji(code) ?: ""
        _uiState.update { it.copy(countryCode = code, countryLabel = label, countryEmoji = emoji) }
    }

    fun clearCountry() {
        _uiState.update { it.copy(countryCode = "", countryLabel = "", countryEmoji = "") }
    }

    fun onLanguageSelect(code: String, label: String) {
        _uiState.update { it.copy(languageCode = code, languageLabel = label) }
    }

    fun clearLanguage() {
        _uiState.update { it.copy(languageCode = "", languageLabel = "") }
    }

    fun onTagChange(newTag: String) {
        _uiState.update { it.copy(tag = newTag) }
        updateTagSuggestions(newTag)
    }

    fun onTagSelect(selectedTag: String) {
        _uiState.update { it.copy(tag = selectedTag, suggestedTags = emptyList()) }
    }

    private fun updateTagSuggestions(query: String) {
        if (query.length < 2) {
            _uiState.update { it.copy(suggestedTags = emptyList()) }
            return
        }
        
        val filtered = allTags.filter { 
            it.lowercase(Locale.ROOT).contains(query.lowercase(Locale.ROOT)) 
        }.take(20)
        
        _uiState.update { it.copy(suggestedTags = filtered) }
        
        // Also kick off an online refined search if list is small or empty
        if (filtered.size < 5) {
            searchTagsOnline(query)
        }
    }

    private fun searchTagsOnline(query: String) {
        tagSearchJob?.cancel()
        tagSearchJob = viewModelScope.launch {
            delay(500)
            val tags = withContext(Dispatchers.IO) {
                fetchCategoriesRaw("json/tags/$query")
            }
            val onlineResults = tags.map { it.Name }
            _uiState.update { state ->
                val combined = (state.suggestedTags + onlineResults).distinct().take(20)
                state.copy(suggestedTags = combined)
            }
        }
    }

    fun onSortByChange(newSort: String) {
        _uiState.update { it.copy(sortBy = newSort) }
    }

    fun onReverseChange(newReverse: Boolean) {
        _uiState.update { it.copy(reverse = newReverse) }
    }

    private fun fetchMetadata() {
        viewModelScope.launch {
            val countriesData = withContext(Dispatchers.IO) { fetchCategoriesRaw("json/countrycodes") }
            val languagesData = withContext(Dispatchers.IO) { fetchCategoriesRaw("json/languages") }
            val tagsData = withContext(Dispatchers.IO) { fetchCategoriesRaw("json/tags?limit=500") }
            allTags = tagsData.map { it.Name }

            val processedCountries = withContext(Dispatchers.Default) {
                countriesData.map { 
                    val label = CountryCodeDictionary.instance.getCountryByCode(it.Name) ?: it.Name
                    val emoji = EmojiUtils.getFlagEmoji(it.Name) ?: ""
                    CategoryItem(it.Name, label, emoji)
                }.sortedBy { it.label }
            }

            val processedLanguages = withContext(Dispatchers.Default) {
                languagesData.map { 
                    CategoryItem(it.Name, it.Name.replaceFirstChar { c -> c.uppercase() })
                }.sortedBy { it.label }
            }

            _uiState.update { state ->
                state.copy(
                    countries = processedCountries,
                    languages = processedLanguages
                )
            }
        }
    }

    private suspend fun fetchCategoriesRaw(url: String): List<DataCategory> {
        val result = Utils.downloadFeedRelative(app.httpClient, app, url, false, null)
        return withContext(Dispatchers.Default) {
            DataCategory.DecodeJson(result).toList()
        }
    }

    fun performSearch() {
        saveFilters()
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            
            val params = mutableMapOf<String, String>()
            val state = _uiState.value
            
            if (state.name.isNotEmpty()) params["name"] = state.name
            if (state.countryCode.isNotEmpty()) params["countrycode"] = state.countryCode
            if (state.languageCode.isNotEmpty()) params["language"] = state.languageCode
            if (state.tag.isNotEmpty()) params["tag"] = state.tag
            
            params["order"] = state.sortBy
            params["reverse"] = state.reverse.toString()
            params["hidebroken"] = (!(sharedPref.getBoolean("show_broken", false))).toString()
            params["limit"] = "100"

            val resultString = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(app.httpClient, app, "json/stations/search", true, params)
            }
            
            if (resultString != null) {
                val stations = withContext(Dispatchers.Default) {
                    DataRadioStation.DecodeJson(resultString) ?: emptyList()
                }
                _uiState.update { it.copy(stations = stations, isSearching = false) }
            } else {
                _uiState.update { it.copy(isSearching = false, error = "Failed to fetch stations") }
            }
        }
    }
}
