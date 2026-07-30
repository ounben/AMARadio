package com.ounben.amaradio.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdateHelper {
    private const val TAG = "WidgetUpdate"

    fun updateAllWidgets(context: Context, station: DataRadioStation?, isPlaying: Boolean) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                // Update Small Widget State
                val smallWidget = AMARadioSmallWidget()
                val smallIds = GlanceAppWidgetManager(context).getGlanceIds(AMARadioSmallWidget::class.java)
                smallIds.forEach { id ->
                    updateAppWidgetState(context, id) { prefs ->
                        station?.let {
                            prefs[WidgetState.stationNameKey] = it.Name
                            prefs[WidgetState.stationDetailsKey] = it.getShortDetails(context)
                            prefs[WidgetState.stationUuidKey] = it.StationUuid
                        }
                        prefs[WidgetState.isPlayingKey] = isPlaying
                    }
                    smallWidget.update(context, id)
                }

                // Update Full Widget State
                val fullWidget = AMARadioFullWidget()
                val fullIds = GlanceAppWidgetManager(context).getGlanceIds(AMARadioFullWidget::class.java)
                fullIds.forEach { id ->
                    updateAppWidgetState(context, id) { prefs ->
                        station?.let {
                            prefs[WidgetState.stationNameKey] = it.Name
                            prefs[WidgetState.stationDetailsKey] = it.getShortDetails(context)
                            prefs[WidgetState.stationUuidKey] = it.StationUuid
                        }
                        prefs[WidgetState.isPlayingKey] = isPlaying
                    }
                    fullWidget.update(context, id)
                }
                
                Log.d(TAG, "Widgets updated: ${smallIds.size} small, ${fullIds.size} full")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widgets", e)
            }
        }
    }
}
