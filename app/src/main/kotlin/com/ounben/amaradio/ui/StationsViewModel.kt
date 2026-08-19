package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.Utils
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.SearchStyle
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
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toDataStation
import java.net.URLEncoder
import kotlin.time.Duration.Companion.milliseconds

class StationsViewModel(application: Application) : AndroidViewModel(application) {

    data class StationsUiState(
        val stations: List<DataRadioStation> = emptyList(),
        val filteredStations: List<DataRadioStation> = emptyList(),
        val favoriteIds: Set<String> = emptySet(),
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
        
        viewModelScope.launch {
            app.favouriteManager.stationsFlow.collect { favorites ->
                val ids = favorites.map { it.StationUuid }.toSet()
                _uiState.update { it.copy(favoriteIds = ids) }
            }
        }
    }

    override fun onCleared() {
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
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
            if (!forceUpdate) delay(50.milliseconds)
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. OFFLINE FIRST: Versuche Daten aus Room zu laden
            var localDataFound = false
            
            if (url.startsWith("json/stations/bycountrycodeexact/")) {
                val countryCode = url.substringAfter("bycountrycodeexact/").substringBefore("?").uppercase()
                val localStations = withContext(Dispatchers.IO) {
                    AMARadioDatabase.getDatabase(app).stationDao().getStationsByCountryCode(countryCode)
                }
                if (localStations.isNotEmpty()) {
                    val decoded = localStations.map { it.toDataStation() }
                    _uiState.update { it.copy(stations = decoded, filteredStations = decoded, isLoading = false) }
                    localDataFound = true
                }
            } else if (url.startsWith("json/stations/byname/")) {
                val nameQuery = java.net.URLDecoder.decode(url.substringAfter("byname/"), "UTF-8").trim()
                val queryWords = nameQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
                
                val localStations = withContext(Dispatchers.IO) {
                    val dao = AMARadioDatabase.getDatabase(app).stationDao()
                    if (queryWords.size > 1) {
                        dao.searchStationsMulti(
                            queryWords.getOrNull(0),
                            queryWords.getOrNull(1),
                            queryWords.getOrNull(2)
                        )
                    } else {
                        dao.searchStations(nameQuery)
                    }
                }

                if (localStations.isNotEmpty()) {
                    val decoded = withContext(Dispatchers.Default) {
                        localStations.map { it.toDataStation() }
                            .sortedByDescending { SearchUtils.calculateMultiWordScore(it.Name, queryWords) }
                    }
                    _uiState.update { it.copy(stations = decoded, filteredStations = decoded, isLoading = false) }
                    localDataFound = true
                }
            } else if (url.startsWith("json/stations/bytagexact/")) {
                val tagQuery = java.net.URLDecoder.decode(url.substringAfter("bytagexact/"), "UTF-8")
                val localStations = withContext(Dispatchers.IO) {
                    AMARadioDatabase.getDatabase(app).stationDao().getStationsFiltered(null, null, null, tagQuery, "clickcount")
                }
                if (localStations.isNotEmpty()) {
                    val decoded = localStations.map { it.toDataStation() }
                    _uiState.update { it.copy(stations = decoded, filteredStations = decoded, isLoading = false) }
                    localDataFound = true
                }
            }

            // 2. Netzwerk-Abfrage: NUR wenn lokal absolut nichts gefunden wurde und die Liste leer ist.
            // Wir entfernen den automatischen Netzwerk-Refresh bei 'forceUpdate' (Swipe Down),
            // da die lokale SQL-Datenbank als einzige Quelle dienen soll.
            if (!localDataFound && _uiState.value.stations.isEmpty()) {
                val params = HashMap<String, String>()
                params["hidebroken"] = "true"

                val result = withContext(Dispatchers.IO) {
                    Utils.downloadFeedRelative(app.httpClient, app, url, forceUpdate, params)
                }

                if (result != null) {
                    withContext(Dispatchers.Default) {
                        val decoded = DataRadioStation.DecodeJson(result) ?: emptyList()
                        _uiState.update { it.copy(stations = decoded, filteredStations = decoded, isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load stations") }
                }
            } else {
                // Wenn lokale Daten da sind, beenden wir das Laden einfach hier.
                // Ein Swipe-Down aktualisiert somit nur den Stand aus der SQL-DB.
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun search(searchStyle: SearchStyle, query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(stations = emptyList(), filteredStations = emptyList()) }
            return
        }
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = when (searchStyle) {
            SearchStyle.ByName -> "json/stations/byname/$encodedQuery"
            SearchStyle.ByTagExact -> "json/stations/bytagexact/$encodedQuery"
            SearchStyle.ByCountryCodeExact -> "json/stations/bycountrycodeexact/$encodedQuery"
            SearchStyle.ByLanguageExact -> "json/stations/bylanguageexact/$encodedQuery"
        }
        loadStations(url, forceUpdate = false)
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
}
