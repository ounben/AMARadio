package com.ounben.amaradio.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.favorites.FavoriteListHelper
import com.ounben.amaradio.database.user.FavoriteListEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LocalManagerType {
    HISTORY, FAVOURITES, CUSTOM
}

class LocalStationsViewModel(application: Application, private val type: LocalManagerType) : AndroidViewModel(application) {

    data class LocalStationsUiState(
        val stations: List<DataRadioStation> = emptyList(),
        val filteredStations: List<DataRadioStation> = emptyList(),
        val isGrid: Boolean = false,
        val query: String = "",
        val favoriteLists: List<FavoriteListEntity> = emptyList(),
        val selectedListId: String? = null,
        val listItemCounts: Map<String, Int> = emptyMap()
    )

    private val _uiState = MutableStateFlow(LocalStationsUiState())
    val uiState: StateFlow<LocalStationsUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    private val _selectedListId = MutableStateFlow<String?>(null)

    private val app = application as AMARadioApp
    private val manager = when (type) {
        LocalManagerType.HISTORY -> app.historyManager
        LocalManagerType.FAVOURITES -> app.favouriteManager
        LocalManagerType.CUSTOM -> app.customStationManager
    }
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "icons_only_favorites_style") {
            refreshGridMode()
        }
    }

    init {
        refreshGridMode()
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)

        if (type == LocalManagerType.FAVOURITES) {
            viewModelScope.launch {
                combine(
                    manager.stationsFlow,
                    app.favoriteListManager.listsFlow,
                    app.favoriteListManager.allItemsFlow,
                    _selectedListId,
                    _query
                ) { stations, lists, allItems, selectedListId, query ->
                    val effectiveListId = if (selectedListId != null && lists.none { it.id == selectedListId }) {
                        null
                    } else {
                        selectedListId
                    }
                    val counts = FavoriteListHelper.computeListCounts(stations, allItems)
                    val baseStations = FavoriteListHelper.filterStationsForList(stations, allItems, effectiveListId)
                    val filtered = withContext(Dispatchers.Default) {
                        if (query.isEmpty()) baseStations
                        else baseStations.filter {
                            it.Name.contains(query, ignoreCase = true) || it.TagsAll.contains(query, ignoreCase = true)
                        }
                    }
                    if (effectiveListId != selectedListId) {
                        _selectedListId.value = null
                    }
                    LocalStationsUiState(
                        stations = stations,
                        filteredStations = filtered,
                        isGrid = _uiState.value.isGrid,
                        query = query,
                        favoriteLists = lists,
                        selectedListId = effectiveListId,
                        listItemCounts = counts
                    )
                }.collectLatest { newState ->
                    _uiState.value = newState
                }
            }
        } else {
            viewModelScope.launch {
                combine(manager.stationsFlow, _query) { stations, query ->
                    val filtered = withContext(Dispatchers.Default) {
                        if (query.isEmpty()) stations
                        else stations.filter {
                            it.Name.contains(query, ignoreCase = true) || it.TagsAll.contains(query, ignoreCase = true)
                        }
                    }
                    stations to (query to filtered)
                }.collectLatest { (stations, queryAndFiltered) ->
                    val (query, filtered) = queryAndFiltered
                    _uiState.update { state ->
                        state.copy(stations = stations, query = query, filteredStations = filtered)
                    }
                }
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

    fun search(query: String) {
        _query.value = query
    }

    fun selectFavoriteList(listId: String?) {
        _selectedListId.value = listId
    }

    fun createFavoriteList(name: String, selectAfterCreate: Boolean = true) {
        app.favoriteListManager.createList(name) { newId ->
            if (selectAfterCreate) {
                _selectedListId.value = newId
            }
        }
    }

    fun renameFavoriteList(listId: String, newName: String) {
        app.favoriteListManager.renameList(listId, newName)
    }

    fun deleteFavoriteList(listId: String) {
        if (_selectedListId.value == listId) {
            _selectedListId.value = null
        }
        app.favoriteListManager.deleteList(listId)
    }

    fun moveFavoriteList(listId: String, delta: Int) {
        app.favoriteListManager.moveList(listId, delta)
    }

    fun updateFavoriteListsOrder(orderedLists: List<FavoriteListEntity>) {
        app.favoriteListManager.persistListsOrder(orderedLists)
    }

    fun setStationsForList(listId: String, selectedUuids: Set<String>) {
        app.favoriteListManager.setListStations(listId, _uiState.value.stations, selectedUuids)
    }

    fun removeStationFromSelectedList(stationUuid: String) {
        val listId = _selectedListId.value ?: return
        app.favoriteListManager.removeStationFromList(listId, stationUuid)
    }

    fun getStationUuidsForList(listId: String): Set<String> {
        return app.favoriteListManager.getStationUuidsForListSync(listId)
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        manager.reorder(fromIndex, toIndex)
    }

    fun updateAllOrder(stations: List<DataRadioStation>) {
        val activeListId = if (type == LocalManagerType.FAVOURITES) _selectedListId.value else null
        if (activeListId != null) {
            app.favoriteListManager.persistListOrder(activeListId, stations)
        } else {
            manager.persistOrder(stations)
        }
    }
}

class LocalStationsViewModelFactory(private val app: AMARadioApp, private val type: LocalManagerType) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LocalStationsViewModel(app, type) as T
    }
}
