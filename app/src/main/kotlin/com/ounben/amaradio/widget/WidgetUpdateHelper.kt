package com.ounben.amaradio.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WidgetUpdateHelper {
    private const val TAG = "WidgetUpdate"

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    // State memory to throttle redundant updates
    private data class LastWidgetState(
        val stationUuid: String?,
        val isPlaying: Boolean,
        val trackInfo: String?
    )
    private var lastState: LastWidgetState? = null

    /**
     * Pushes the current player state and serialized station lists to all widgets.
     * This follows the "Push" model to ensure instant updates and avoid LazyColumn freezing.
     */
    fun updateAllWidgets(context: Context, station: DataRadioStation?, isPlaying: Boolean, trackInfo: String? = null) {
        // Filter out redundant updates (e.g., intermediate buffering states without content change)
        if (station != null) {
            val newState = LastWidgetState(station.StationUuid, isPlaying, trackInfo)
            if (newState == lastState) {
                // No visible change for the user, skip expensive DB/IPC operations
                return
            }
            lastState = newState
        } else {
            // station == null usually means a manual refresh (refreshAllWidgets). 
            // We force these through to ensure list synchronization.
            lastState = null 
        }

        val scope = CoroutineScope(Dispatchers.IO)
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
     * Triggers a push update without changing the currently playing station info.
     */
    fun refreshAllWidgets(context: Context) {
        // In a real app, you might want to preserve the current station info here,
        // but for now, we just push the lists.
        updateAllWidgets(context, null, false)
    }
}
