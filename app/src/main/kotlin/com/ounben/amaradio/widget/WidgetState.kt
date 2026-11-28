package com.ounben.amaradio.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable

@Serializable
data class WidgetPlaylistSummary(
    val id: String,
    val name: String,
    val count: Int
)

object WidgetState {
    val stationNameKey = stringPreferencesKey("current_station_name")
    val stationDetailsKey = stringPreferencesKey("current_station_details")
    val stationUuidKey = stringPreferencesKey("current_station_uuid")
    val stationIconUrlKey = stringPreferencesKey("current_station_icon_url")
    val isPlayingKey = booleanPreferencesKey("is_playing")
    val activeTabKey = stringPreferencesKey("active_tab") // "favorites" or "history"
    val favoritesJsonKey = stringPreferencesKey("favorites_json")
    val historyJsonKey = stringPreferencesKey("history_json")
    val playlistsJsonKey = stringPreferencesKey("playlists_json")
    val selectedPlaylistIdKey = stringPreferencesKey("selected_playlist_id") // "" = All
    val playlistPickerOpenKey = booleanPreferencesKey("playlist_picker_open")
    val totalFavoritesCountKey = intPreferencesKey("total_favorites_count")
    val updateCounterKey = intPreferencesKey("update_count")
    val currentTrackKey = stringPreferencesKey("current_track_info")
}
