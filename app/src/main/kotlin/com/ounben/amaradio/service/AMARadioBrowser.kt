package com.ounben.amaradio.service

import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toDataStation
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
    private val database = AMARadioDatabase.getDatabase(app)
    private val stationDao = database.stationDao()

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
        Log.d("BROWSER", "onGetChildren for: $parentId")
        
        return when (parentId) {
            MEDIA_ID_ROOT -> {
                val rootChildren = createBrowsableMediaItemsForRoot()
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(rootChildren), params))
            }
            MEDIA_ID_STATIONS_GROUP -> {
                val stationTabs = createStationTabMediaItems()
                Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(stationTabs), params))
            }
            MEDIA_ID_MUSICS_FAVORITE -> {
                scope.future {
                    val stations = app.favouriteManager.getList()
                    LibraryResult.ofItemList(createMediaItemsFromStations(stations), params)
                }
            }
            MEDIA_ID_MUSICS_HISTORY -> {
                scope.future {
                    val stations = app.historyManager.getList()
                    LibraryResult.ofItemList(createMediaItemsFromStations(stations), params)
                }
            }
            else -> {
                if (parentId.startsWith(MEDIA_ID_FILTER_PREFIX)) {
                    val filterId = parentId.substring(MEDIA_ID_FILTER_PREFIX.length)
                    scope.future {
                        val stations = if (filterId == "local") fetchLocalStations() else fetchStationsForFilter(filterId)
                        LibraryResult.ofItemList(createMediaItemsFromStations(stations), params)
                    }
                } else {
                    Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            }
        }
    }

    private fun createMediaItemsFromStations(stations: List<DataRadioStation>): ImmutableList<MediaItem> {
        val mediaItems = ArrayList<MediaItem>()
        
        for (station in stations) {
            stationIdToStation[station.StationUuid] = station
            val isFavorite = app.favouriteManager.has(station.StationUuid)
            
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(station.Name)
                .setSubtitle("${station.Country} ${station.TagsAll}")
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    putString("com.ounben.amaradio.STATION_ID", station.StationUuid)
                    putBoolean("androidx.media3.session.IS_FAVORITE", isFavorite)
                })
            
            // PERFORMANCE & SECURITY FIX FOR ANDROID AUTO:
            // Android Auto (Gearhead) forbids file:/// URIs. 
            // We must ALWAYS use our ContentProvider (content://) to serve icons,
            // whether they are already in cache or need to be generated.
            val iconUrl = station.IconUrl
            if (!iconUrl.isNullOrEmpty() && iconUrl != "null" && !iconUrl.startsWith("http")) {
                // If it's a local path but not our provider, wrap it
                metadataBuilder.setArtworkUri(com.ounben.amaradio.utils.StationIconProvider.getIconUri(station.StationUuid, station.Name))
            } else if (!iconUrl.isNullOrEmpty() && iconUrl != "null") {
                // Remote URLs are fine, Gearhead can load them
                metadataBuilder.setArtworkUri(Uri.parse(iconUrl))
            } else {
                // Use our provider for everything else (Cache or Placeholder)
                metadataBuilder.setArtworkUri(com.ounben.amaradio.utils.StationIconProvider.getIconUri(station.StationUuid, station.Name))
            }
            
            mediaItems.add(MediaItem.Builder()
                .setMediaId(LEAF_PREFIX + station.StationUuid)
                .setMediaMetadata(metadataBuilder.build())
                .build())
        }
        return ImmutableList.copyOf(mediaItems)
    }

    private suspend fun fetchStationsForFilter(filterId: String): List<DataRadioStation> {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(app)
        val jsonStr = sharedPref.getString("filter_tabs_json", null) ?: return emptyList()
        val tabs = try { json.decodeFromString<List<FilterTabItem>>(jsonStr) } catch (e: Exception) { emptyList() }
        val tab = tabs.find { it.id == filterId } ?: return emptyList()

        // Sync with Smartphone App: Use local SQL database instead of remote JSON API
        val results = stationDao.getStationsFiltered(
            name = tab.name.ifEmpty { null },
            countryCode = tab.countryCode.ifEmpty { null },
            language = tab.languageCode.ifEmpty { null },
            tag = tab.tag.ifEmpty { null },
            orderBy = tab.sortBy.lowercase()
        )
        
        return results.map { it.toDataStation() }
    }

    private suspend fun fetchLocalStations(): List<DataRadioStation> {
        val countryCode = app.resources.configuration.locales[0].country.uppercase()
        if (countryCode.isEmpty()) return emptyList()
        
        // Sync with Smartphone App: Use local SQL database
        val results = stationDao.getStationsByCountryCode(countryCode)
        return results.map { it.toDataStation() }
    }

    fun onGetItem(browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        val stationId = stationIdFromMediaId(mediaId)
        val station = stationIdToStation[stationId] ?: app.favouriteManager.getById(stationId) ?: app.historyManager.getById(stationId)
        
        if (station != null) {
            val iconUrl = station.IconUrl
            val artworkUri = if (!iconUrl.isNullOrEmpty() && iconUrl != "null" && iconUrl.startsWith("http")) {
                Uri.parse(iconUrl)
            } else {
                com.ounben.amaradio.utils.StationIconProvider.getIconUri(station.StationUuid, station.Name)
            }

            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(station.Name)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setArtworkUri(artworkUri)
                    .build())
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }
        return Futures.immediateFuture(LibraryResult.ofError<MediaItem>(SessionError.ERROR_BAD_VALUE))
    }

    fun getStationById(stationId: String): DataRadioStation? = stationIdToStation[stationId] ?: app.favouriteManager.getById(stationId) ?: app.historyManager.getById(stationId)

    private fun createBrowsableMediaItemsForRoot(): List<MediaItem> {
        val resources = app.resources
        val mediaItems = ArrayList<MediaItem>()
        val packageName = app.packageName
        
        // 1. Stationen (Alle Tabs)
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_STATIONS_GROUP)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_stations))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setArtworkUri(Uri.parse("android.resource://$packageName/drawable/ic_radio_24dp"))
                .build())
            .build())

        // 2. Favorites
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_starred))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setArtworkUri(Uri.parse("android.resource://$packageName/drawable/ic_star_black_24dp"))
                .build())
            .build())
            
        // 3. History
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_HISTORY)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_history))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setArtworkUri(Uri.parse("android.resource://$packageName/drawable/ic_restore_black_24dp"))
                .build())
            .build())
        
        return mediaItems
    }

    private fun createStationTabMediaItems(): List<MediaItem> {
        val mediaItems = ArrayList<MediaItem>()
        
        // 1. Lokal (Tab Name)
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_FILTER_PREFIX + "local")
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(app.getString(R.string.action_local))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build())

        // 2. Saved Filter Tabs
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
        private const val MEDIA_ID_ROOT = "root_id"
        private const val MEDIA_ID_STATIONS_GROUP = "stations_group_id"
        private const val MEDIA_ID_MUSICS_FAVORITE = "favorites_id"
        private const val MEDIA_ID_MUSICS_HISTORY = "history_id"
        private const val MEDIA_ID_FILTER_PREFIX = "filter_"
        private const val LEAF_PREFIX = "station_"

        @JvmStatic
        fun stationIdFromMediaId(mediaId: String?): String {
            if (mediaId == null) return ""
            return if (mediaId.startsWith(LEAF_PREFIX)) {
                mediaId.substring(LEAF_PREFIX.length)
            } else if (mediaId.contains("_")) {
                mediaId.substring(mediaId.lastIndexOf("_") + 1)
            } else {
                mediaId
            }
        }
    }
}
