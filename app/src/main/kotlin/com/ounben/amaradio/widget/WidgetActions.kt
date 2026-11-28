package com.ounben.amaradio.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ounben.amaradio.service.PlayerService
import kotlinx.serialization.json.Json

class PlayPauseActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_TOGGLE_PLAY_PAUSE
        }
        context.startService(intent)
    }
}

class NextActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_SKIP_TO_NEXT
        }
        context.startService(intent)
    }
}

class PreviousActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_SKIP_TO_PREVIOUS
        }
        context.startService(intent)
    }
}

class PlayStationActionCallback : ActionCallback {
    companion object {
        val stationUuidKey = ActionParameters.Key<String>("extra_station_uuid")
    }
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val uuid = parameters[stationUuidKey]?.toString() ?: return
        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_PLAY_STATION
            putExtra(PlayerService.EXTRA_STATION_ID, uuid)
        }
        context.startService(intent)
    }
}

class SwitchTabActionCallback : ActionCallback {
    companion object {
        val tabKey = ActionParameters.Key<String>("extra_tab")
    }
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tab = parameters[tabKey] ?: "favorites"
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val p = prefs.toMutablePreferences()
            p[WidgetState.activeTabKey] = tab
            p[WidgetState.playlistPickerOpenKey] = false
            p
        }
        WidgetUpdateHelper.updateFullWidget(context, glanceId)
    }
}

class TogglePlaylistPickerActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val p = prefs.toMutablePreferences()
            val isOpen = p[WidgetState.playlistPickerOpenKey] ?: false
            p[WidgetState.playlistPickerOpenKey] = !isOpen
            p[WidgetState.updateCounterKey] = (p[WidgetState.updateCounterKey] ?: 0) + 1
            p
        }
        AMARadioFullWidget().update(context, glanceId)
    }
}

class SelectPlaylistActionCallback : ActionCallback {
    companion object {
        val playlistIdKey = ActionParameters.Key<String>("playlist_id")
    }
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val playlistId = parameters[playlistIdKey] ?: ""
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val p = prefs.toMutablePreferences()
            p[WidgetState.activeTabKey] = "favorites"
            p[WidgetState.selectedPlaylistIdKey] = playlistId
            p[WidgetState.playlistPickerOpenKey] = false
            p
        }
        WidgetUpdateHelper.updateFullWidget(context, glanceId)
    }
}

class CyclePlaylistActionCallback : ActionCallback {
    companion object {
        val deltaKey = ActionParameters.Key<Int>("delta")
        private val json = Json { ignoreUnknownKeys = true }
    }
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[deltaKey] ?: 1
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val p = prefs.toMutablePreferences()
            val playlistsJson = p[WidgetState.playlistsJsonKey] ?: "[]"
            val playlists = runCatching {
                json.decodeFromString<List<WidgetPlaylistSummary>>(playlistsJson)
            }.getOrDefault(emptyList())

            if (playlists.isNotEmpty()) {
                val options = listOf("") + playlists.map { it.id }
                val current = p[WidgetState.selectedPlaylistIdKey] ?: ""
                val currentIdx = options.indexOf(current).let { if (it == -1) 0 else it }
                val nextIdx = Math.floorMod(currentIdx + delta, options.size)
                p[WidgetState.selectedPlaylistIdKey] = options[nextIdx]
            } else {
                p[WidgetState.selectedPlaylistIdKey] = ""
            }
            p[WidgetState.activeTabKey] = "favorites"
            p[WidgetState.playlistPickerOpenKey] = false
            p
        }
        WidgetUpdateHelper.updateFullWidget(context, glanceId)
    }
}
