package com.ounben.amaradio.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.service.MediaSessionCallback

class PlayPauseActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val toggleIntent = Intent("com.ounben.amaradio.TOGGLE_PLAY_PAUSE")
        context.sendBroadcast(toggleIntent)
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
        val targetUuid = parameters.get(stationUuidKey) ?: return
        val intent = Intent(MediaSessionCallback.BROADCAST_PLAY_STATION_BY_ID)
        intent.putExtra(MediaSessionCallback.EXTRA_STATION_ID, targetUuid.toString())
        context.sendBroadcast(intent)
    }
}

class SwitchTabActionCallback : ActionCallback {
    companion object {
        val tabKey = ActionParameters.Key<String>("extra_tab")
    }
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val targetTab = parameters.get(tabKey) ?: "favorites"
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetState.activeTabKey] = targetTab.toString()
        }
        AMARadioFullWidget().update(context, glanceId)
    }
}
