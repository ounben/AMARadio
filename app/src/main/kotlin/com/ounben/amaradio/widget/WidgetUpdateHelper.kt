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

    /**
     * Pushes the current player state and serialized station lists to all widgets.
     * This follows the "Push" model to ensure instant updates.
     */
    fun updateAllWidgets(context: Context, station: DataRadioStation?, isPlaying: Boolean) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val userDb = AMARadioUserDatabase.getDatabase(context)
                val favorites = userDb.favoriteDao().getAllFavorites().take(15).map { it.toDataStation() }
                val history = userDb.historyDao().getAllHistory().take(15).map { it.toDataStation() }

                val favoritesJson = json.encodeToString(favorites)
                val historyJson = json.encodeToString(history)

                val smallIds = GlanceAppWidgetManager(context).getGlanceIds(AMARadioSmallWidget::class.java)
                val fullIds = GlanceAppWidgetManager(context).getGlanceIds(AMARadioFullWidget::class.java)

                Log.d(TAG, "Updating widgets. Small: ${smallIds.size}, Full: ${fullIds.size}")

                val smallWidget = AMARadioSmallWidget()
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
                        m[WidgetState.favoritesJsonKey] = favoritesJson
                        m[WidgetState.historyJsonKey] = historyJson
                        m
                    }
                    smallWidget.update(context, id)
                }

                val fullWidget = AMARadioFullWidget()
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
                        m[WidgetState.favoritesJsonKey] = favoritesJson
                        m[WidgetState.historyJsonKey] = historyJson
                        
                        // Force recomposition
                        val current = m[WidgetState.updateCounterKey] ?: 0
                        m[WidgetState.updateCounterKey] = current + 1
                        m
                    }
                    fullWidget.update(context, id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widgets", e)
            }
        }
    }

    /**
     * Refreshes all widgets by fetching fresh data from Room and pushing it to Glance state.
     */
    fun refreshAllWidgets(context: Context) {
        updateAllWidgets(context, null, false)
    }
}
