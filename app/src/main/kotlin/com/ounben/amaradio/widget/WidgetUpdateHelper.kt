package com.ounben.amaradio.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.favorites.FavoriteListHelper
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

object WidgetUpdateHelper {
    private const val TAG = "WidgetUpdate"
    private const val MAX_WIDGET_STATIONS = 30

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val updateMutex = Mutex()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startDatabaseObservation(context: Context) {
        val userDb = AMARadioUserDatabase.getDatabase(context)
        
        scope.launch {
            combine(
                userDb.favoriteDao().getAllFavoritesFlow(),
                userDb.historyDao().getAllHistoryFlow(),
                userDb.favoriteListDao().getAllListsFlow(),
                userDb.favoriteListDao().getAllItemsFlow()
            ) { _, _, _, _ -> }
            .debounce(1000) 
            .collect {
                Log.d(TAG, "SQL Database changed -> Updating Widgets")
                refreshAllWidgets(context)
            }
        }
    }

    fun updateAllWidgets(context: Context, station: DataRadioStation?, isPlaying: Boolean, trackInfo: String? = null) {
        scope.launch {
            updateMutex.withLock {
                try {
                    // 1. Fetch fresh data from Room
                    val userDb = AMARadioUserDatabase.getDatabase(context)
                    val allFavorites = userDb.favoriteDao().getAllFavorites().map { it.toDataStation() }
                    val history = userDb.historyDao().getAllHistory().take(MAX_WIDGET_STATIONS).map { it.toDataStation() }
                    val favoriteLists = userDb.favoriteListDao().getAllLists()
                    val favoriteListItems = userDb.favoriteListDao().getAllItems()

                    val listCounts = FavoriteListHelper.computeListCounts(allFavorites, favoriteListItems)
                    val validListIds = favoriteLists.mapTo(HashSet()) { it.id }
                    val playlistSummaries = favoriteLists.map { list ->
                        WidgetPlaylistSummary(
                            id = list.id,
                            name = list.name,
                            count = listCounts[list.id] ?: 0
                        )
                    }

                    // 2. Serialize shared lists to JSON for Glance Preferences
                    val historyJson = json.encodeToString(history)
                    val playlistsJson = json.encodeToString(playlistSummaries)

                    val manager = GlanceAppWidgetManager(context)

                    // 3. Update Small Widgets
                    val smallWidget = AMARadioSmallWidget()
                    val smallIds = manager.getGlanceIds(AMARadioSmallWidget::class.java)
                    smallIds.forEach { id ->
                        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                            val m = prefs.toMutablePreferences()
                            station?.let {
                                m[WidgetState.stationNameKey] = it.Name
                                m[WidgetState.stationDetailsKey] = it.TagsAll
                                m[WidgetState.stationUuidKey] = it.StationUuid
                                m[WidgetState.stationIconUrlKey] = it.IconUrl
                            }
                            m[WidgetState.isPlayingKey] = isPlaying
                            m[WidgetState.currentTrackKey] = trackInfo ?: ""
                            
                            val current = m[WidgetState.updateCounterKey] ?: 0
                            m[WidgetState.updateCounterKey] = current + 1
                            m
                        }
                        smallWidget.update(context, id)
                    }

                    // 4. Update Full Widgets (each instance keeps its own selected playlist filter)
                    val fullWidget = AMARadioFullWidget()
                    val fullIds = manager.getGlanceIds(AMARadioFullWidget::class.java)
                    fullIds.forEach { id ->
                        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                            val m = prefs.toMutablePreferences()
                            station?.let {
                                m[WidgetState.stationNameKey] = it.Name
                                m[WidgetState.stationDetailsKey] = it.TagsAll
                                m[WidgetState.stationUuidKey] = it.StationUuid
                                m[WidgetState.stationIconUrlKey] = it.IconUrl
                            }
                            m[WidgetState.isPlayingKey] = isPlaying
                            m[WidgetState.currentTrackKey] = trackInfo ?: ""

                            val currentSelectedId = m[WidgetState.selectedPlaylistIdKey] ?: ""
                            val effectiveListId = if (currentSelectedId.isNotEmpty() && currentSelectedId in validListIds) {
                                currentSelectedId
                            } else {
                                ""
                            }
                            if (effectiveListId != currentSelectedId) {
                                m[WidgetState.selectedPlaylistIdKey] = effectiveListId
                                m[WidgetState.playlistPickerOpenKey] = false
                            }

                            val visibleFavorites = FavoriteListHelper
                                .filterStationsForList(allFavorites, favoriteListItems, effectiveListId.ifEmpty { null })
                                .take(MAX_WIDGET_STATIONS)

                            m[WidgetState.favoritesJsonKey] = json.encodeToString(visibleFavorites)
                            m[WidgetState.historyJsonKey] = historyJson
                            m[WidgetState.playlistsJsonKey] = playlistsJson
                            m[WidgetState.totalFavoritesCountKey] = allFavorites.size
                            
                            val current = m[WidgetState.updateCounterKey] ?: 0
                            m[WidgetState.updateCounterKey] = current + 1
                            m
                        }
                        fullWidget.update(context, id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Update failed", e)
                }
            }
        }
    }

    /**
     * Immediately refreshes a single [AMARadioFullWidget] instance after an interactive tab or
     * playlist filter change, preserving player state and recomputing only that widget's filtered list.
     */
    suspend fun updateFullWidget(context: Context, glanceId: GlanceId) {
        updateMutex.withLock {
            try {
                val userDb = AMARadioUserDatabase.getDatabase(context)
                val allFavorites = userDb.favoriteDao().getAllFavorites().map { it.toDataStation() }
                val history = userDb.historyDao().getAllHistory().take(MAX_WIDGET_STATIONS).map { it.toDataStation() }
                val favoriteLists = userDb.favoriteListDao().getAllLists()
                val favoriteListItems = userDb.favoriteListDao().getAllItems()

                val listCounts = FavoriteListHelper.computeListCounts(allFavorites, favoriteListItems)
                val validListIds = favoriteLists.mapTo(HashSet()) { it.id }
                val playlistSummaries = favoriteLists.map { list ->
                    WidgetPlaylistSummary(
                        id = list.id,
                        name = list.name,
                        count = listCounts[list.id] ?: 0
                    )
                }

                val currentStation = PlayerServiceUtil.getCurrentStation()
                val isPlaying = PlayerServiceUtil.isPlaying()
                val live = PlayerServiceUtil.getMetadataLive()
                val trackInfo = if (live.track.isNotEmpty()) live.track else if (live.title.isNotEmpty()) live.title else null

                val fullWidget = AMARadioFullWidget()
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    val m = prefs.toMutablePreferences()
                    currentStation?.let {
                        m[WidgetState.stationNameKey] = it.Name
                        m[WidgetState.stationDetailsKey] = it.TagsAll
                        m[WidgetState.stationUuidKey] = it.StationUuid
                        m[WidgetState.stationIconUrlKey] = it.IconUrl
                    }
                    m[WidgetState.isPlayingKey] = isPlaying
                    m[WidgetState.currentTrackKey] = trackInfo ?: ""

                    val currentSelectedId = m[WidgetState.selectedPlaylistIdKey] ?: ""
                    val effectiveListId = if (currentSelectedId.isNotEmpty() && currentSelectedId in validListIds) {
                        currentSelectedId
                    } else {
                        ""
                    }
                    if (effectiveListId != currentSelectedId) {
                        m[WidgetState.selectedPlaylistIdKey] = effectiveListId
                        m[WidgetState.playlistPickerOpenKey] = false
                    }

                    val visibleFavorites = FavoriteListHelper
                        .filterStationsForList(allFavorites, favoriteListItems, effectiveListId.ifEmpty { null })
                        .take(MAX_WIDGET_STATIONS)

                    m[WidgetState.favoritesJsonKey] = json.encodeToString(visibleFavorites)
                    m[WidgetState.historyJsonKey] = json.encodeToString(history)
                    m[WidgetState.playlistsJsonKey] = json.encodeToString(playlistSummaries)
                    m[WidgetState.totalFavoritesCountKey] = allFavorites.size

                    val current = m[WidgetState.updateCounterKey] ?: 0
                    m[WidgetState.updateCounterKey] = current + 1
                    m
                }
                fullWidget.update(context, glanceId)
            } catch (e: Exception) {
                Log.e(TAG, "Single full widget update failed", e)
            }
        }
    }

    fun refreshAllWidgets(context: Context) {
        val currentStation = PlayerServiceUtil.getCurrentStation()
        val isPlaying = PlayerServiceUtil.isPlaying()
        val live = PlayerServiceUtil.getMetadataLive()
        val trackInfo = if (live.track.isNotEmpty()) live.track else if (live.title.isNotEmpty()) live.title else null
        updateAllWidgets(context, currentStation, isPlaying, trackInfo)
    }
}
