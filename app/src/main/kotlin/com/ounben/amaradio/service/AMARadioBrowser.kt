package com.ounben.amaradio.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.ui.FilterTabItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.serialization.json.Json
import androidx.preference.PreferenceManager

class AMARadioBrowser(private val app: AMARadioApp) {
    private val stationIdToStation = HashMap<String, DataRadioStation>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    fun onGetLibraryRoot(browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId(MEDIA_ID_ROOT)
            .setMediaMetadata(MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setTitle(app.resources.getString(R.string.app_name))
                .build())
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    fun onGetChildren(browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (MEDIA_ID_ROOT == parentId) {
            val rootChildren = createBrowsableMediaItemsForRoot()
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(rootChildren), params))
        }

        if (parentId == MEDIA_ID_MUSICS_FAVORITE) {
            val stations = app.favouriteManager.getList()
            return Futures.immediateFuture(LibraryResult.ofItemList(createMediaItemsFromStations(stations), params))
        }

        if (parentId == MEDIA_ID_MUSICS_HISTORY) {
            val stations = app.historyManager.getList()
            return Futures.immediateFuture(LibraryResult.ofItemList(createMediaItemsFromStations(stations), params))
        }

        if (parentId.startsWith(MEDIA_ID_FILTER_PREFIX)) {
            val filterId = parentId.substring(MEDIA_ID_FILTER_PREFIX.length)
            return scope.future {
                val stations = fetchStationsForFilter(filterId)
                LibraryResult.ofItemList(createMediaItemsFromStations(stations), params)
            }
        }

        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params))
    }

    private fun createMediaItemsFromStations(stations: List<DataRadioStation>): ImmutableList<MediaItem> {
        val mediaItems = ArrayList<MediaItem>()
        for (station in stations) {
            stationIdToStation[station.StationUuid] = station
            val metadata = MediaMetadata.Builder()
                .setTitle(station.Name)
                .setSubtitle("${station.Country} ${station.TagsAll}")
                .setIsBrowsable(false)
                .setIsPlayable(true)
            
            val iconUrl = station.IconUrl
            if (!iconUrl.isNullOrEmpty()) {
                metadata.setArtworkUri(Uri.parse(iconUrl))
            }
            
            mediaItems.add(MediaItem.Builder()
                .setMediaId(LEAF_PREFIX + station.StationUuid)
                .setMediaMetadata(metadata.build())
                .build())
        }
        return ImmutableList.copyOf(mediaItems)
    }

    private suspend fun fetchStationsForFilter(filterId: String): List<DataRadioStation> {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(app)
        val jsonStr = sharedPref.getString("filter_tabs_json", null) ?: return emptyList()
        val tabs = try { json.decodeFromString<List<FilterTabItem>>(jsonStr) } catch (e: Exception) { emptyList() }
        val tab = tabs.find { it.id == filterId } ?: return emptyList()

        val params = mutableMapOf<String, String>()
        if (tab.name.isNotEmpty()) params["name"] = tab.name
        if (tab.countryCode.isNotEmpty()) params["countrycode"] = tab.countryCode
        if (tab.languageCode.isNotEmpty()) params["language"] = tab.languageCode
        if (tab.tag.isNotEmpty()) params["tag"] = tab.tag
        params["order"] = tab.sortBy
        params["reverse"] = tab.reverse.toString()
        params["hidebroken"] = "true"
        params["limit"] = "50"

        val resultString = Utils.downloadFeedRelative(app.httpClient, app, "json/stations/search", true, params)
        return if (resultString != null) {
            DataRadioStation.DecodeJson(resultString)?.toList() ?: emptyList()
        } else emptyList()
    }

    fun onGetItem(browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        val stationId = stationIdFromMediaId(mediaId)
        val station = stationIdToStation[stationId]
        if (station != null) {
            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(station.Name)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkUri(Uri.parse(station.IconUrl ?: ""))
                    .build())
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }
        return Futures.immediateFuture(LibraryResult.ofError<MediaItem>(SessionError.ERROR_BAD_VALUE))
    }

    fun getStationById(stationId: String): DataRadioStation? = stationIdToStation[stationId]

    private fun createBrowsableMediaItemsForRoot(): List<MediaItem> {
        val resources = app.resources
        val mediaItems = ArrayList<MediaItem>()
        
        // 1. Favorites
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_starred))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build())
            
        // 2. History
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_HISTORY)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_history))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build())

        // 3. Saved Filter Tabs
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(app)
        val jsonStr = sharedPref.getString("filter_tabs_json", null)
        if (jsonStr != null) {
            try {
                val tabs = json.decodeFromString<List<FilterTabItem>>(jsonStr)
                for (tab in tabs) {
                    if (tab.label.isNotEmpty()) {
                        mediaItems.add(MediaItem.Builder()
                            .setMediaId(MEDIA_ID_FILTER_PREFIX + tab.id)
                            .setMediaMetadata(MediaMetadata.Builder()
                                .setTitle(tab.label)
                                .setSubtitle(app.getString(R.string.action_filter))
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build())
                            .build())
                    }
                }
            } catch (e: Exception) { /* ignore */ }
        }
        
        return mediaItems
    }

    companion object {
        private const val MEDIA_ID_ROOT = "__ROOT__"
        private const val MEDIA_ID_MUSICS_FAVORITE = "__FAVORITE__"
        private const val MEDIA_ID_MUSICS_HISTORY = "__HISTORY__"
        private const val MEDIA_ID_FILTER_PREFIX = "__FILTER__"
        private const val LEAF_PREFIX = "STATION|"

        @JvmStatic
        fun stationIdFromMediaId(mediaId: String?): String {
            if (mediaId == null) return ""
            return if (mediaId.startsWith(LEAF_PREFIX)) {
                mediaId.substring(LEAF_PREFIX.length)
            } else {
                mediaId
            }
        }
    }
}
