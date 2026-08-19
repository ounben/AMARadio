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
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.TagCacheEntity
import com.ounben.amaradio.database.LanguageCacheEntity
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.FilterTabEntity
import com.ounben.amaradio.database.toDataStation
import kotlinx.serialization.Serializable
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

fun FilterTabItem.toEntity(pos: Int) = FilterTabEntity(
    id = id, label = label, name = name, countryCode = countryCode,
    countryLabel = countryLabel, countryEmoji = countryEmoji,
    languageCode = languageCode, languageLabel = languageLabel,
    tag = tag, sortBy = sortBy, reverse = reverse, position = pos
)

fun FilterTabEntity.toItem() = FilterTabItem(
    id = id, label = label, name = name, countryCode = countryCode,
    countryLabel = countryLabel, countryEmoji = countryEmoji,
    languageCode = languageCode, languageLabel = languageLabel,
    tag = tag, sortBy = sortBy, reverse = reverse
)

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    data class FilterUiState(
        val tabs: List<FilterTabItem> = emptyList(),
        val selectedTabIndex: Int = 0,
        val favoriteIds: Set<String> = emptySet(),
        val isSearching: Boolean = false,
        val countries: List<CategoryItem> = emptyList(),
        val languages: List<CategoryItem> = emptyList(),
        val tags: List<CategoryItem> = emptyList(),
        val error: String? = null,
        val isGrid: Boolean = false,
        val isLoadedFromDb: Boolean = false
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

    private var isFirstTabsLoad = true

    init {
        viewModelScope.launch {
            val userDb = AMARadioUserDatabase.getDatabase(app)
            userDb.filterTabDao().getAllTabsFlow().collect { entities ->
                val dbTabs = entities.map { it.toItem() }
                val currentTabs = _uiState.value.tabs
                
                // SMART MERGE: Preserve stations in memory if they exist
                val mergedTabs = dbTabs.map { dbTab ->
                    val existing = currentTabs.find { it.id == dbTab.id }
                    if (existing != null && existing.stations.isNotEmpty()) {
                        dbTab.copy(stations = existing.stations)
                    } else {
                        dbTab
                    }
                }

                val savedIndex = sharedPref.getInt("filter_selected_index", 0)
                
                // FALLBACK: Only if confirmed empty
                val finalTabs = if (mergedTabs.isEmpty()) {
                    listOf(FilterTabItem(label = "Filter"))
                } else {
                    mergedTabs
                }

                _uiState.update { it.copy(
                    tabs = finalTabs, 
                    selectedTabIndex = savedIndex.coerceIn(0, finalTabs.size - 1),
                    isLoadedFromDb = true
                ) }
                
                // Trigger initial search ONLY on first load
                if (isFirstTabsLoad) {
                    isFirstTabsLoad = false
                    finalTabs.forEachIndexed { index, tab ->
                        if (tab.stations.isEmpty() && (tab.name.isNotEmpty() || tab.countryCode.isNotEmpty() || tab.tag.isNotEmpty())) {
                            performSearch(index)
                        }
                    }
                }
            }
        }
        
        // REAKTIVE DATEN: Nur für Tags und Sprachen (SQL-Tabellen)
        viewModelScope.launch {
            val db = AMARadioDatabase.getDatabase(app)
            
            launch {
                db.tagCacheDao().getAllTagsFlow().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _uiState.update { it.copy(tags = entities.map { CategoryItem(it.tagName, it.tagName, count = it.stationCount ?: 0) }) }
                    }
                }
            }

            launch {
                db.languageCacheDao().getAllLanguagesFlow().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _uiState.update { it.copy(languages = entities.map { 
                            val label = it.languageName.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString() }
                            CategoryItem(it.languageName, label, count = it.stationCount ?: 0)
                        }.sortedBy { it.label }) }
                    }
                }
            }
        }

        // Initiales Laden
        viewModelScope.launch {
            delay(100.milliseconds)
            fetchMetadata()
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

    fun saveFilters() {
        val state = _uiState.value
        if (!state.isLoadedFromDb) return

        viewModelScope.launch(Dispatchers.IO) {
            val userDb = AMARadioUserDatabase.getDatabase(app)
            val entities = state.tabs.mapIndexed { index, item -> item.toEntity(index) }
            // ATOMIC UPDATE: Use the transaction method to prevent empty emission flickering
            userDb.filterTabDao().updateAllTabs(entities)
            
            sharedPref.edit {
                putInt("filter_selected_index", state.selectedTabIndex)
            }
        }
    }

    // Tab Management
    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
        sharedPref.edit {
            putInt("filter_selected_index", index)
        }
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

    fun onNameChange(index: Int, newName: String) {
        updateTabAt(index) { it.copy(name = newName) }
        saveFilters()
    }
    
    fun onCountrySelect(index: Int, code: String, label: String) {
        updateTabAt(index) { it.copy(countryCode = code, countryLabel = label, countryEmoji = EmojiUtils.getFlagEmoji(code) ?: "") }
        saveFilters()
    }
    
    fun clearCountry(index: Int) {
        updateTabAt(index) { it.copy(countryCode = "", countryLabel = "", countryEmoji = "") }
        saveFilters()
    }
    
    fun onLanguageSelect(index: Int, code: String, label: String) {
        updateTabAt(index) { it.copy(languageCode = code, languageLabel = label) }
        saveFilters()
    }
    
    fun clearLanguage(index: Int) {
        updateTabAt(index) { it.copy(languageCode = "", languageLabel = "") }
        saveFilters()
    }
    
    fun onTagSelect(index: Int, selectedTag: String) {
        updateTabAt(index) { it.copy(tag = selectedTag) }
        saveFilters()
    }
    
    fun clearTag(index: Int) {
        updateTabAt(index) { it.copy(tag = "") }
        saveFilters()
    }
    
    fun onSortByChange(index: Int, newSort: String) {
        updateTabAt(index) { it.copy(sortBy = newSort) }
        saveFilters()
    }
    
    fun onReverseChange(index: Int, newReverse: Boolean) {
        updateTabAt(index) { it.copy(reverse = newReverse) }
        saveFilters()
    }

    fun fetchMetadata() {
        viewModelScope.launch {
            val db = AMARadioDatabase.getDatabase(app)
            
            // 1. LÄNDER: Immer aus JSON laden (da stabil und bewährt)
            val countriesData = loadLocalOrRemote("radio_browser_country_cache", "json/countrycodes", "countries")
            val processedCountries = withContext(Dispatchers.Default) {
                countriesData.map { 
                    val label = CountryCodeDictionary.instance.getCountryByCode(it.Name) ?: it.Name
                    val emoji = EmojiUtils.getFlagEmoji(it.Name) ?: ""
                    CategoryItem(it.Name, label, emoji, count = it.UsedCount)
                }.sortedBy { it.label }
            }

            // 2. TAGS & SPRACHEN: Nur laden, wenn SQL leer ist (Initial-Migration)
            val localTags = withContext(Dispatchers.IO) { db.tagCacheDao().getAllTags() }
            val localLanguages = withContext(Dispatchers.IO) { db.languageCacheDao().getAllLanguages() }

            if (localTags.isEmpty()) {
                val tagsData = loadLocalOrRemote("radio_browser_tag_cache", "json/tags?order=stationcount&reverse=true", "tags")
                if (tagsData.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        db.tagCacheDao().insertAll(tagsData.map { TagCacheEntity(it.Name, it.UsedCount, it.UsedCount) })
                    }
                }
            }

            if (localLanguages.isEmpty()) {
                val languagesData = loadLocalOrRemote("radio_browser_language_cache", "json/languages", "languages")
                if (languagesData.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        db.languageCacheDao().insertAll(languagesData.map { LanguageCacheEntity(it.Name, it.UsedCount, it.UsedCount) })
                    }
                }
            }

            // UI-State nur für Länder sofort setzen (Tags/Sprachen kommen über Flows)
            _uiState.update { it.copy(countries = processedCountries) }
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
            
            val state = _uiState.value
            val tab = state.tabs.getOrNull(index) ?: return@launch
            val nameQuery = tab.name.trim()
            val queryWords = nameQuery.split(Regex("\\s+")).filter { it.isNotBlank() }

            // STRICT OFFLINE SEARCH: Only use local SQL database
            val localResults = withContext(Dispatchers.IO) {
                val dao = AMARadioDatabase.getDatabase(app).stationDao()
                if (queryWords.size > 1 && tab.countryCode.isEmpty() && tab.tag.isEmpty() && tab.languageCode.isEmpty()) {
                    // Optimized Multi-Word logic for name-heavy searches
                    dao.searchStationsMulti(
                        queryWords.getOrNull(0),
                        queryWords.getOrNull(1),
                        queryWords.getOrNull(2)
                    )
                } else {
                    dao.getStationsFiltered(
                        name = nameQuery.ifEmpty { null },
                        countryCode = tab.countryCode.ifEmpty { null },
                        language = tab.languageCode.ifEmpty { null },
                        tag = tab.tag.ifEmpty { null },
                        orderBy = tab.sortBy // 'clickcount', 'name', 'votes', 'lastchange'
                    )
                }
            }

            val decoded = withContext(Dispatchers.Default) {
                val list = localResults.map { it.toDataStation() }
                if (queryWords.isNotEmpty()) {
                    list.sortedByDescending { SearchUtils.calculateMultiWordScore(it.Name, queryWords) }
                } else {
                    list
                }
            }
            _uiState.update { s ->
                val newTabs = s.tabs.toMutableList()
                if (index in newTabs.indices) {
                    newTabs[index] = newTabs[index].copy(stations = decoded)
                }
                s.copy(tabs = newTabs, isSearching = false)
            }
        }
    }
}
