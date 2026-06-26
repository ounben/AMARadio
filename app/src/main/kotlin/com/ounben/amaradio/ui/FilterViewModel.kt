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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    data class FilterUiState(
        val tabName: String = "",
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
        val tags: List<CategoryItem> = emptyList(),
        val error: String? = null,
        val isGrid: Boolean = false
    )

    data class CategoryItem(val code: String, val label: String, val emoji: String = "", val count: Int = 0)

    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)
    private val app = application as AMARadioApp

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "icons_only_favorites_style") {
            refreshGridMode()
        }
    }

    init {
        loadSavedFilters()
        viewModelScope.launch {
            delay(100.milliseconds)
            fetchMetadata()
            if (hasAnyFilter()) {
                performSearch()
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
        _uiState.update { 
            val cCode = sharedPref.getString("filter_country_code", "") ?: ""
            it.copy(
                tabName = sharedPref.getString("filter_tab_name", "") ?: "",
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
            putString("filter_tab_name", state.tabName)
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

    fun onTabNameChange(newName: String) {
        _uiState.update { it.copy(tabName = newName) }
        saveFilters() // Immediate save for tab title responsiveness
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

    fun onTagSelect(selectedTag: String) {
        _uiState.update { it.copy(tag = selectedTag) }
    }

    fun clearTag() {
        _uiState.update { it.copy(tag = "") }
    }

    fun onSortByChange(newSort: String) {
        _uiState.update { it.copy(sortBy = newSort) }
    }

    fun onReverseChange(newReverse: Boolean) {
        _uiState.update { it.copy(reverse = newReverse) }
    }

    private suspend fun loadLocalOrRemote(resName: String, remoteUrl: String, debugName: String): List<DataCategory> = withContext(Dispatchers.IO) {
        val resId = app.resources.getIdentifier(resName, "raw", app.packageName)
        if (resId != 0) {
            try {
                val inputStream = app.resources.openRawResource(resId)
                val content = inputStream.bufferedReader().use { it.readText() }.trim()
                
                val jsonToDecode = if (content.startsWith("{")) {
                    val root = Json.parseToJsonElement(content).jsonObject
                    if (root.containsKey("rows")) {
                        root["rows"]?.jsonArray?.toString() ?: "[]"
                    } else {
                        "[$content]"
                    }
                } else {
                    content
                }

                val decoded = DataCategory.DecodeJson(jsonToDecode).toList()
                if (decoded.isNotEmpty()) {
                    Log.d("FILTER_DEBUG", "Loaded local $debugName: ${decoded.size} items")
                    return@withContext decoded
                }
            } catch (e: Exception) {
                Log.w("FILTER_DEBUG", "Error parsing local $debugName: ${e.message}")
            }
        }
        
        Log.d("FILTER_DEBUG", "Local $debugName not found, trying remote: $remoteUrl")
        val result = Utils.downloadFeedRelative(app.httpClient, app, remoteUrl, false, null)
        DataCategory.DecodeJson(result).toList()
    }

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

    fun performSearch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            withContext(Dispatchers.IO) { saveFilters() }
            
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
                val filtered = withContext(Dispatchers.Default) {
                    DataRadioStation.DecodeJson(resultString) ?: emptyList()
                }
                _uiState.update { it.copy(stations = filtered, isSearching = false) }
            } else {
                _uiState.update { it.copy(isSearching = false, error = "Failed to fetch stations") }
            }
        }
    }
}
