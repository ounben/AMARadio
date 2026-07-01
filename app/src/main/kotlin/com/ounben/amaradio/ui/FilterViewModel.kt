package com.ounben.amaradio.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.CountryCodeDictionary
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.EmojiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class FilterTabItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "New Filter",
    val name: String = "",
    val countryCode: String = "",
    val countryLabel: String = "",
    val countryEmoji: String = "",
    val languageCode: String = "",
    val languageLabel: String = "",
    val tag: String = "",
    val sortBy: String = "clickcount",
    val reverse: Boolean = true,
    @kotlinx.serialization.Transient val stations: List<DataRadioStation> = emptyList()
)

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    data class FilterUiState(
        val tabs: List<FilterTabItem> = listOf(FilterTabItem(label = "Default")),
        val selectedTabIndex: Int = 0,
        val favoriteIds: Set<String> = emptySet(),
        val isSearching: Boolean = false,
        val countries: List<CategoryItem> = emptyList(),
        val languages: List<CategoryItem> = emptyList(),
        val tags: List<CategoryItem> = emptyList(),
        val error: String? = null,
        val isGrid: Boolean = false
    )

    data class CategoryItem(val code: String, val label: String, val emoji: String = "", val count: Int = 0)

    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)
    private val app = application as AMARadioApp
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "icons_only_favorites_style") {
            refreshGridMode()
        } else if (key == "settings_language") {
            fetchMetadata()
        }
    }

    init {
        loadSavedFilters()
        viewModelScope.launch {
            delay(100.milliseconds)
            fetchMetadata()
            // Search all tabs on startup if they have filters
            _uiState.value.tabs.forEachIndexed { index, tab ->
                if (tab.name.isNotEmpty() || tab.countryCode.isNotEmpty() || tab.languageCode.isNotEmpty() || tab.tag.isNotEmpty()) {
                    performSearch(index)
                }
            }
        }
        
        viewModelScope.launch {
            app.favouriteManager.stationsFlow.collect { favorites ->
                val ids = favorites.map { it.StationUuid }.toSet()
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }

        refreshGridMode()
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun refreshGridMode() {
        val isGrid = sharedPref.getBoolean("icons_only_favorites_style", false)
        _uiState.update { it.copy(isGrid = isGrid) }
    }

    private fun loadSavedFilters() {
        val savedTabsJson = sharedPref.getString("filter_tabs_json", null)
        val savedIndex = sharedPref.getInt("filter_selected_index", 0)
        
        if (savedTabsJson != null) {
            try {
                val decodedTabs = json.decodeFromString<List<FilterTabItem>>(savedTabsJson)
                if (decodedTabs.isNotEmpty()) {
                    _uiState.update { it.copy(tabs = decodedTabs, selectedTabIndex = savedIndex.coerceIn(0, decodedTabs.size - 1)) }
                    return
                }
            } catch (e: Exception) {
                Log.e("FILTER", "Error loading tabs", e)
            }
        }
        _uiState.update { it.copy(tabs = listOf(FilterTabItem(label = "Filter"))) }
    }

    fun saveFilters() {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            sharedPref.edit {
                putString("filter_tabs_json", json.encodeToString(state.tabs))
                putInt("filter_selected_index", state.selectedTabIndex)
            }
        }
    }

    // Tab Management
    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
        saveFilters()
    }

    fun addTab() {
        if (_uiState.value.tabs.size >= 5) return
        _uiState.update { 
            val newTabs = it.tabs + FilterTabItem(label = "")
            it.copy(tabs = newTabs, selectedTabIndex = newTabs.size - 1)
        }
        saveFilters()
    }

    fun removeTab(index: Int) {
        if (_uiState.value.tabs.size <= 1) return
        _uiState.update { 
            val newTabs = it.tabs.toMutableList().apply { removeAt(index) }
            val newIndex = if (it.selectedTabIndex >= newTabs.size) newTabs.size - 1 else it.selectedTabIndex
            it.copy(tabs = newTabs, selectedTabIndex = newIndex)
        }
        saveFilters()
    }

    fun updateTabLabel(index: Int, newLabel: String) {
        _uiState.update { state ->
            val newTabs = state.tabs.toMutableList()
            if (index in newTabs.indices) {
                newTabs[index] = newTabs[index].copy(label = newLabel)
            }
            state.copy(tabs = newTabs)
        }
        saveFilters()
    }

    // Indexed Filter Updates
    private fun updateTabAt(index: Int, update: (FilterTabItem) -> FilterTabItem) {
        _uiState.update { state ->
            val newTabs = state.tabs.toMutableList()
            if (index in newTabs.indices) {
                newTabs[index] = update(newTabs[index])
            }
            state.copy(tabs = newTabs)
        }
    }

    fun onNameChange(index: Int, newName: String) = updateTabAt(index) { it.copy(name = newName) }
    fun onCountrySelect(index: Int, code: String, label: String) = updateTabAt(index) { 
        it.copy(countryCode = code, countryLabel = label, countryEmoji = EmojiUtils.getFlagEmoji(code) ?: "") 
    }
    fun clearCountry(index: Int) = updateTabAt(index) { it.copy(countryCode = "", countryLabel = "", countryEmoji = "") }
    fun onLanguageSelect(index: Int, code: String, label: String) = updateTabAt(index) { it.copy(languageCode = code, languageLabel = label) }
    fun clearLanguage(index: Int) = updateTabAt(index) { it.copy(languageCode = "", languageLabel = "") }
    fun onTagSelect(index: Int, selectedTag: String) = updateTabAt(index) { it.copy(tag = selectedTag) }
    fun clearTag(index: Int) = updateTabAt(index) { it.copy(tag = "") }
    fun onSortByChange(index: Int, newSort: String) = updateTabAt(index) { it.copy(sortBy = newSort) }
    fun onReverseChange(index: Int, newReverse: Boolean) = updateTabAt(index) { it.copy(reverse = newReverse) }

    fun fetchMetadata() {
        viewModelScope.launch {
            val tagsData = loadLocalOrRemote("radio_browser_tag_cache", "json/tags?limit=1000", "tags")
            val countriesData = loadLocalOrRemote("radio_browser_country_cache", "json/countrycodes", "countries")
            val languagesData = loadLocalOrRemote("radio_browser_language_cache", "json/languages", "languages")

            val processedTags = withContext(Dispatchers.Default) {
                tagsData.map { CategoryItem(it.Name, it.Name, count = it.UsedCount) }.sortedByDescending { it.count }
            }

            val processedCountries = withContext(Dispatchers.Default) {
                countriesData.map { 
                    val label = CountryCodeDictionary.instance.getCountryByCode(it.Name) ?: it.Name
                    val emoji = EmojiUtils.getFlagEmoji(it.Name) ?: ""
                    CategoryItem(it.Name, label, emoji, count = it.UsedCount)
                }.sortedBy { it.label }
            }

            val processedLanguages = withContext(Dispatchers.Default) {
                languagesData.map { 
                    val label = it.Name.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString() }
                    CategoryItem(it.Name, label, count = it.UsedCount)
                }.sortedBy { it.label }
            }

            _uiState.update { state ->
                state.copy(
                    countries = processedCountries,
                    languages = processedLanguages,
                    tags = processedTags
                )
            }
        }
    }

    private suspend fun loadLocalOrRemote(resName: String, remoteUrl: String, debugName: String): List<DataCategory> = withContext(Dispatchers.IO) {
        val resId = app.resources.getIdentifier(resName, "raw", app.packageName)
        if (resId != 0) {
            try {
                val content = app.resources.openRawResource(resId).bufferedReader().use { it.readText() }.trim()
                val jsonToDecode = if (content.startsWith("{")) {
                    val root = json.parseToJsonElement(content).jsonObject
                    if (root.containsKey("rows")) root["rows"]?.jsonArray?.toString() ?: "[]" else "[$content]"
                } else content
                DataCategory.DecodeJson(jsonToDecode).toList().ifEmpty { emptyList() }
            } catch (e: Exception) { emptyList() }
        } else {
            val result = Utils.downloadFeedRelative(app.httpClient, app, remoteUrl, false, null)
            DataCategory.DecodeJson(result).toList()
        }
    }

    fun performSearch(index: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            saveFilters()
            
            val state = _uiState.value
            val tab = state.tabs.getOrNull(index) ?: return@launch
            val params = mutableMapOf<String, String>()
            
            if (tab.name.isNotEmpty()) params["name"] = tab.name
            if (tab.countryCode.isNotEmpty()) params["countrycode"] = tab.countryCode
            if (tab.languageCode.isNotEmpty()) params["language"] = tab.languageCode
            if (tab.tag.isNotEmpty()) params["tag"] = tab.tag
            
            params["order"] = tab.sortBy
            params["reverse"] = tab.reverse.toString()
            params["hidebroken"] = "true"
            params["limit"] = "100"

            val resultString = withContext(Dispatchers.IO) {
                Utils.downloadFeedRelative(app.httpClient, app, "json/stations/search", true, params)
            }
            
            if (resultString != null) {
                val filtered = withContext(Dispatchers.Default) {
                    DataRadioStation.DecodeJson(resultString) ?: emptyList()
                }
                _uiState.update { s ->
                    val newTabs = s.tabs.toMutableList()
                    if (index in newTabs.indices) {
                        newTabs[index] = newTabs[index].copy(stations = filtered)
                    }
                    s.copy(tabs = newTabs, isSearching = false)
                }
            } else {
                _uiState.update { it.copy(isSearching = false, error = "Failed to fetch stations") }
            }
        }
    }
}
