package com.ounben.amaradio.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.MutablePreferences
import com.ounben.amaradio.service.PlayerService

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
        val tab = parameters[tabKey]?.toString() ?: "favorites"
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val p = prefs.toMutablePreferences()
            p[WidgetState.activeTabKey] = tab
            p
        }
        // Use the helper to push fresh data and current player state
        WidgetUpdateHelper.refreshAllWidgets(context)
    }
}
