package com.ounben.amaradio.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WidgetUpdateHelper {
    private const val TAG = "WidgetUpdate"

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startDatabaseObservation(context: Context) {
        val userDb = AMARadioUserDatabase.getDatabase(context)
        
        scope.launch {
            // Combine both flows to update widgets whenever favorites OR history change
            combine(
                userDb.favoriteDao().getAllFavoritesFlow(),
                userDb.historyDao().getAllHistoryFlow()
            ) { _, _ -> }
            .debounce(1000) // Settling time for rapid changes
            .collect {
                Log.d(TAG, "SQL Database changed (Fav/Hist) -> Updating Widgets")
                refreshAllWidgets(context)
            }
        }
    }

    fun updateAllWidgets(context: Context, station: DataRadioStation?, isPlaying: Boolean, trackInfo: String? = null) {
        scope.launch {
            try {
                // 1. Fetch fresh data from Room
                val userDb = AMARadioUserDatabase.getDatabase(context)
                val favorites = userDb.favoriteDao().getAllFavorites().take(30).map { it.toDataStation() }
                val history = userDb.historyDao().getAllHistory().take(30).map { it.toDataStation() }

                // 2. Serialize to JSON for Glance Preferences
                val favoritesJson = json.encodeToString(favorites)
                val historyJson = json.encodeToString(history)

                // 3. Update Small Widgets
                val smallWidget = AMARadioSmallWidget()
                val smallIds = GlanceAppWidgetManager(context).getGlanceIds(AMARadioSmallWidget::class.java)
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
                        
                        // Dependency trigger
                        val current = m[WidgetState.updateCounterKey] ?: 0
                        m[WidgetState.updateCounterKey] = current + 1
                        m
                    }
                    smallWidget.update(context, id)
                }

                // 4. Update Full Widgets
                val fullWidget = AMARadioFullWidget()
                val fullIds = GlanceAppWidgetManager(context).getGlanceIds(AMARadioFullWidget::class.java)
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
                        
                        // Push full lists
                        m[WidgetState.favoritesJsonKey] = favoritesJson
                        m[WidgetState.historyJsonKey] = historyJson
                        
                        // Dependency trigger for Re-rendering
                        val current = m[WidgetState.updateCounterKey] ?: 0
                        m[WidgetState.updateCounterKey] = current + 1
                        m
                    }
                    fullWidget.update(context, id)
                }
                
                Log.d(TAG, "Push complete: ${smallIds.size} small, ${fullIds.size} full updated.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push widget updates", e)
            }
        }
    }

    /**
     * Triggers a push update while preserving the current player state from the service.
     */
    fun refreshAllWidgets(context: Context) {
        val currentStation = PlayerServiceUtil.getCurrentStation()
        val isPlaying = PlayerServiceUtil.isPlaying()
        
        // Construct track info from live metadata if available
        val live = PlayerServiceUtil.getMetadataLive()
        val trackInfo = if (live.track.isNotEmpty() && live.artist.isNotEmpty()) {
            "${live.artist} - ${live.track}"
        } else if (live.track.isNotEmpty()) {
            live.track
        } else if (live.title.isNotEmpty()) {
            live.title
        } else {
            null
        }

        updateAllWidgets(context, currentStation, isPlaying, trackInfo)
    }
}
