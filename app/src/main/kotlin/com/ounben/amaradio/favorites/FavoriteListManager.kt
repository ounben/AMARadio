package com.ounben.amaradio.favorites

import android.content.Context
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.user.FavoriteListEntity
import com.ounben.amaradio.database.user.FavoriteListItemEntity
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object FavoriteListHelper {

    fun filterStationsForList(
        allFavorites: List<DataRadioStation>,
        allItems: List<FavoriteListItemEntity>,
        selectedListId: String?
    ): List<DataRadioStation> {
        if (selectedListId == null) return allFavorites

        val favoritesByUuid = allFavorites.associateBy { it.StationUuid }
        return allItems
            .asSequence()
            .filter { it.listId == selectedListId }
            .sortedWith(compareBy<FavoriteListItemEntity> { it.displayOrder }.thenBy { it.addedAt })
            .mapNotNull { favoritesByUuid[it.stationUuid] }
            .toList()
    }

    fun computeListCounts(
        allFavorites: List<DataRadioStation>,
        allItems: List<FavoriteListItemEntity>
    ): Map<String, Int> {
        val validUuids = allFavorites.mapTo(HashSet()) { it.StationUuid }
        val counts = HashMap<String, Int>()
        for (item in allItems) {
            if (item.stationUuid in validUuids) {
                counts[item.listId] = (counts[item.listId] ?: 0) + 1
            }
        }
        return counts
    }

    fun computeMovedList(
        lists: List<FavoriteListEntity>,
        fromIndex: Int,
        toIndex: Int
    ): List<FavoriteListEntity>? {
        if (fromIndex !in lists.indices || toIndex !in lists.indices || fromIndex == toIndex) return null
        val mutable = lists.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable.mapIndexed { index, entity -> entity.copy(displayOrder = index) }
    }

    fun computeReorderedLists(
        lists: List<FavoriteListEntity>,
        listId: String,
        delta: Int
    ): List<FavoriteListEntity>? {
        val currentIndex = lists.indexOfFirst { it.id == listId }
        if (currentIndex == -1) return null
        return computeMovedList(lists, currentIndex, currentIndex + delta)
    }

    fun computeUpdatedListItems(
        listId: String,
        existingItems: List<FavoriteListItemEntity>,
        allFavoritesOrdered: List<DataRadioStation>,
        selectedUuids: Set<String>,
        now: Long = System.currentTimeMillis()
    ): List<FavoriteListItemEntity> {
        val kept = existingItems
            .filter { it.stationUuid in selectedUuids }
            .sortedWith(compareBy<FavoriteListItemEntity> { it.displayOrder }.thenBy { it.addedAt })
        val keptUuids = kept.mapTo(HashSet()) { it.stationUuid }

        val added = allFavoritesOrdered
            .asSequence()
            .map { it.StationUuid }
            .filter { it in selectedUuids && it !in keptUuids }
            .map { uuid ->
                FavoriteListItemEntity(
                    listId = listId,
                    stationUuid = uuid,
                    displayOrder = 0,
                    addedAt = now
                )
            }
            .toList()

        return (kept + added).mapIndexed { index, item ->
            item.copy(displayOrder = index)
        }
    }
}

class FavoriteListManager(private val context: Context) {

    private val userDb by lazy { AMARadioUserDatabase.getDatabase(context) }
    private val dao by lazy { userDb.favoriteListDao() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _listsFlow = MutableStateFlow<List<FavoriteListEntity>>(emptyList())
    val listsFlow: StateFlow<List<FavoriteListEntity>> = _listsFlow.asStateFlow()

    private val _allItemsFlow = MutableStateFlow<List<FavoriteListItemEntity>>(emptyList())
    val allItemsFlow: StateFlow<List<FavoriteListItemEntity>> = _allItemsFlow.asStateFlow()

    init {
        scope.launch {
            (context.applicationContext as? AMARadioApp)?.userDbMigrationJob?.join()
            launch {
                dao.getAllListsFlow().collect { lists ->
                    _listsFlow.value = lists
                }
            }
            launch {
                dao.getAllItemsFlow().collect { items ->
                    _allItemsFlow.value = items
                }
            }
        }
    }

    fun getListIdsForStationSync(stationUuid: String): Set<String> {
        return _allItemsFlow.value
            .asSequence()
            .filter { it.stationUuid == stationUuid }
            .map { it.listId }
            .toSet()
    }

    fun getStationUuidsForListSync(listId: String): Set<String> {
        return _allItemsFlow.value
            .asSequence()
            .filter { it.listId == listId }
            .map { it.stationUuid }
            .toSet()
    }

    fun createList(name: String, onCreated: ((String) -> Unit)? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val newId = UUID.randomUUID().toString()
        scope.launch(Dispatchers.IO) {
            val nextOrder = (dao.getMaxListOrder() ?: -1) + 1
            dao.insertList(
                FavoriteListEntity(
                    id = newId,
                    name = trimmed,
                    displayOrder = nextOrder,
                    createdAt = System.currentTimeMillis()
                )
            )
            if (onCreated != null) {
                scope.launch(Dispatchers.Main) {
                    onCreated(newId)
                }
            }
        }
    }

    fun renameList(listId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            dao.renameList(listId, trimmed)
        }
    }

    fun deleteList(listId: String) {
        scope.launch(Dispatchers.IO) {
            dao.deleteListById(listId)
            val remaining = dao.getAllLists().mapIndexed { index, list ->
                list.copy(displayOrder = index)
            }
            if (remaining.isNotEmpty()) {
                dao.updateLists(remaining)
            }
        }
    }

    fun moveList(listId: String, delta: Int) {
        scope.launch(Dispatchers.IO) {
            val currentLists = dao.getAllLists()
            val updated = FavoriteListHelper.computeReorderedLists(currentLists, listId, delta) ?: return@launch
            dao.updateLists(updated)
        }
    }

    fun persistListsOrder(orderedLists: List<FavoriteListEntity>) {
        scope.launch(Dispatchers.IO) {
            val updated = orderedLists.mapIndexed { index, list ->
                list.copy(displayOrder = index)
            }
            if (updated.isNotEmpty()) {
                dao.updateLists(updated)
            }
        }
    }

    fun setStationLists(station: DataRadioStation, selectedListIds: Set<String>) {
        val app = context.applicationContext as? AMARadioApp
        if (selectedListIds.isNotEmpty() && app != null && !app.favouriteManager.has(station.StationUuid)) {
            app.favouriteManager.add(station)
        }

        scope.launch(Dispatchers.IO) {
            val currentIds = dao.getListIdsForStation(station.StationUuid).toSet()
            val toAdd = selectedListIds - currentIds
            val toRemove = currentIds - selectedListIds
            val now = System.currentTimeMillis()

            for (listId in toRemove) {
                dao.removeStationFromList(listId, station.StationUuid)
            }
            for (listId in toAdd) {
                val nextOrder = (dao.getMaxItemOrder(listId) ?: -1) + 1
                dao.insertItem(
                    FavoriteListItemEntity(
                        listId = listId,
                        stationUuid = station.StationUuid,
                        displayOrder = nextOrder,
                        addedAt = now
                    )
                )
            }
        }
    }

    fun setListStations(
        listId: String,
        allFavoritesOrdered: List<DataRadioStation>,
        selectedStationUuids: Set<String>
    ) {
        scope.launch(Dispatchers.IO) {
            val existing = dao.getItemsForList(listId)
            val updated = FavoriteListHelper.computeUpdatedListItems(
                listId = listId,
                existingItems = existing,
                allFavoritesOrdered = allFavoritesOrdered,
                selectedUuids = selectedStationUuids
            )
            dao.replaceListItems(listId, updated)
        }
    }

    fun removeStationFromList(listId: String, stationUuid: String) {
        scope.launch(Dispatchers.IO) {
            dao.removeStationFromList(listId, stationUuid)
        }
    }

    fun persistListOrder(listId: String, orderedStations: List<DataRadioStation>) {
        scope.launch(Dispatchers.IO) {
            val existingByUuid = dao.getItemsForList(listId).associateBy { it.stationUuid }
            val now = System.currentTimeMillis()
            val items = orderedStations.mapIndexed { index, station ->
                val existing = existingByUuid[station.StationUuid]
                FavoriteListItemEntity(
                    listId = listId,
                    stationUuid = station.StationUuid,
                    displayOrder = index,
                    addedAt = existing?.addedAt ?: now
                )
            }
            dao.replaceListItems(listId, items)
        }
    }
}
