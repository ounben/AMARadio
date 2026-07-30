package com.ounben.amaradio.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetState {
    val stationNameKey = stringPreferencesKey("current_station_name")
    val stationDetailsKey = stringPreferencesKey("current_station_details")
    val stationUuidKey = stringPreferencesKey("current_station_uuid")
    val stationIconUrlKey = stringPreferencesKey("current_station_icon_url")
    val isPlayingKey = booleanPreferencesKey("is_playing")
    val activeTabKey = stringPreferencesKey("active_tab") // "favorites" or "history"
}
