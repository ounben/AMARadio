package com.ounben.amaradio.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ounben.amaradio.service.PlayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AMARadioWidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_STATION = "com.ounben.amaradio.widget.PLAY_STATION"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.ounben.amaradio.widget.TOGGLE_PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.ounben.amaradio.widget.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.ounben.amaradio.widget.SKIP_PREV"
        const val ACTION_SWITCH_TAB = "com.ounben.amaradio.widget.SWITCH_TAB"

        const val EXTRA_STATION_UUID = "extra_station_uuid"
        const val EXTRA_TAB = "extra_tab"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("WidgetReceiver", "Action received: $action")

        when (action) {
            ACTION_PLAY_STATION -> {
                val uuid = intent.getStringExtra(EXTRA_STATION_UUID) ?: return
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    this.action = PlayerService.ACTION_PLAY_STATION
                    putExtra(PlayerService.EXTRA_STATION_ID, uuid)
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    Log.e("WidgetReceiver", "Failed to start PlayerService for PLAY_STATION", e)
                }
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    this.action = PlayerService.ACTION_TOGGLE_PLAY_PAUSE
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    Log.e("WidgetReceiver", "Failed to start PlayerService for TOGGLE", e)
                }
            }
            ACTION_SKIP_NEXT -> {
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    this.action = PlayerService.ACTION_SKIP_TO_NEXT
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    Log.e("WidgetReceiver", "Failed to start PlayerService for NEXT", e)
                }
            }
            ACTION_SKIP_PREV -> {
                val serviceIntent = Intent(context, PlayerService::class.java).apply {
                    this.action = PlayerService.ACTION_SKIP_TO_PREVIOUS
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    Log.e("WidgetReceiver", "Failed to start PlayerService for PREV", e)
                }
            }
            ACTION_SWITCH_TAB -> {
                val tab = intent.getStringExtra(EXTRA_TAB) ?: "favorites"
                CoroutineScope(Dispatchers.IO).launch {
                    val manager = GlanceAppWidgetManager(context)
                    val ids = manager.getGlanceIds(AMARadioFullWidget::class.java)
                    ids.forEach { id ->
                        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                            val m = prefs.toMutablePreferences()
                            m[WidgetState.activeTabKey] = tab
                            m
                        }
                    }
                    // Trigger manual refresh of the widgets to reflect tab change
                    WidgetUpdateHelper.refreshAllWidgets(context)
                }
            }
        }
    }
}
