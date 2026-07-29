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
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Lazy init database to prevent blocking service startup
    private val stationDao by lazy { AMARadioDatabase.getDatabase(app).stationDao() }

    fun onGetLibraryRoot(browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId(MEDIA_ID_ROOT)
            .setMediaMetadata(MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setTitle(app.resources.getString(R.string.app_name))
                .setExtras(Bundle().apply {
                    putInt("androidx.media.utils.extras.CONTENT_TYPE", 1) // Music (more stable in AA than 2)
                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
                })
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
                    // SEARCH RESULTS: Media3 browsers call onGetChildren with the query as parentId
                    // after receiving a notifySearchResultChanged from onSearch.
                    scope.future {
                        val results = stationDao.getStationsFiltered(
                            name = parentId.ifEmpty { null },
                            countryCode = null,
                            language = null,
                            tag = null,
                            orderBy = "clickcount"
                        ).map { it.toDataStation() }
                        LibraryResult.ofItemList(createMediaItemsFromStations(results), params)
                    }
                }
            }
        }
    }

    private fun createMediaItemsFromStations(stations: List<DataRadioStation>): ImmutableList<MediaItem> {
        val mediaItems = ArrayList<MediaItem>()
        
        for (station in stations) {
            stationIdToStation[station.StationUuid] = station
            
            val metadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station)
            
            mediaItems.add(MediaItem.Builder()
                .setMediaId(LEAF_PREFIX + station.StationUuid)
                .setMediaMetadata(metadata)
                .build())
        }
        return ImmutableList.copyOf(mediaItems)
    }

    private suspend fun fetchStationsForFilter(filterId: String): List<DataRadioStation> {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(app)
        val jsonStr = sharedPref.getString("filter_tabs_json", null) ?: return emptyList()
        val tabs = try { json.decodeFromString<List<FilterTabItem>>(jsonStr) } catch (e: Exception) { emptyList() }
        val tab = tabs.find { it.id == filterId } ?: return emptyList()

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
        val countryCode = com.ounben.amaradio.Utils.getCountryCode(app)?.uppercase() ?: return emptyList()
        
        val results = stationDao.getStationsByCountryCode(countryCode)
        return results.map { it.toDataStation() }
    }

    fun onGetItem(browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        val stationId = stationIdFromMediaId(mediaId)
        val station = stationIdToStation[stationId] ?: app.favouriteManager.getById(stationId) ?: app.historyManager.getById(stationId)
        
        if (station != null) {
            val metadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station)
            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(metadata)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }
        return Futures.immediateFuture(LibraryResult.ofError<MediaItem>(SessionError.ERROR_BAD_VALUE))
    }

    fun onSearch(browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Log.d("BROWSER", "onSearch: $query")
        return scope.future {
            val results = stationDao.getStationsFiltered(
                name = query.ifEmpty { null },
                countryCode = null,
                language = null,
                tag = null,
                orderBy = "clickcount"
            ).map { it.toDataStation() }
            
            LibraryResult.ofItemList(createMediaItemsFromStations(results), params)
        }
    }

    /**
     * Resolves a keyword search (like "3 swr") to a specific StationUuid.
     * Prioritizes Favorites, then History, then general clickcount.
     */
    suspend fun resolveStationByKeywords(query: String): String? {
        val keywords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (keywords.isEmpty()) return null
        
        // 1. Check Favorites
        val favorites = app.favouriteManager.getList()
        favorites.find { station ->
            val name = station.Name.lowercase()
            keywords.all { name.contains(it) }
        }?.let { return it.StationUuid }
        
        // 2. Check History
        val history = app.historyManager.getList()
        history.find { station ->
            val name = station.Name.lowercase()
            keywords.all { name.contains(it) }
        }?.let { return it.StationUuid }
        
        // 3. Search Database
        // We use the first keyword as the primary search and then filter the rest locally for performance
        val firstKeyword = keywords[0]
        val results = stationDao.getStationsFiltered(
            name = firstKeyword,
            countryCode = null,
            language = null,
            tag = null,
            orderBy = "clickcount"
        )
        
        results.find { entity ->
            val name = entity.name?.lowercase() ?: ""
            keywords.all { name.contains(it) }
        }?.let { return it.stationUuid }
        
        // 4. Final Fallback: Return the top result if we only have one word
        return results.firstOrNull()?.stationUuid
    }

    fun getStationById(stationId: String): DataRadioStation? = stationIdToStation[stationId] ?: app.favouriteManager.getById(stationId) ?: app.historyManager.getById(stationId)

    private fun createBrowsableMediaItemsForRoot(): List<MediaItem> {
        val resources = app.resources
        val mediaItems = ArrayList<MediaItem>()
        val packageName = app.packageName

        // 1. LOKAL (Wie im Smartphone an erster Stelle)
        val localIconUri = Uri.parse("android.resource://$packageName/drawable/ic_radio_white_24dp")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_FILTER_PREFIX + "local")
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(app.getString(R.string.action_local))
                .setSubtitle(app.getString(R.string.app_name))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(localIconUri)
                .setExtras(Bundle().apply {
                    putString("android.media.metadata.DISPLAY_ICON_URI", localIconUri.toString())
                    // GOOGLE AUTO HINTS:
                    putInt("androidx.media.utils.extras.CONTENT_TYPE", 1) // Music
                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2) // List style
                })
                .build())
            .build())

        // 2. FILTER-TABS (Direkt nach Lokal, wie im Smartphone-Pager)
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(app)
        val jsonStr = sharedPref.getString("filter_tabs_json", null)
        if (jsonStr != null) {
            try {
                val tabs = json.decodeFromString<List<FilterTabItem>>(jsonStr)
                for (tab in tabs) {
                    if (tab.label.isNotEmpty()) {
                        val filterIconUri = Uri.parse("android.resource://$packageName/drawable/ic_list_white_24dp")
                        mediaItems.add(MediaItem.Builder()
                            .setMediaId(MEDIA_ID_FILTER_PREFIX + tab.id)
                            .setMediaMetadata(MediaMetadata.Builder()
                                .setTitle(tab.label)
                                .setSubtitle(app.getString(R.string.app_name))
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setArtworkUri(filterIconUri)
                                .setExtras(Bundle().apply {
                                    putString("android.media.metadata.DISPLAY_ICON_URI", filterIconUri.toString())
                                    // GOOGLE AUTO HINTS:
                                    putInt("androidx.media.utils.extras.CONTENT_TYPE", 1) // Music
                                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2) // List style
                                })
                                .build())
                            .build())
                    }
                }
            } catch (e: Exception) {
                Log.e("BROWSER", "Error decoding filter tabs", e)
            }
        }

        // 3. FAVORITEN
        val favoriteIconUri = Uri.parse("android.resource://$packageName/drawable/ic_star_white_24")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_starred))
                .setSubtitle(app.getString(R.string.app_name))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(favoriteIconUri)
                .setExtras(Bundle().apply {
                    putString("android.media.metadata.DISPLAY_ICON_URI", favoriteIconUri.toString())
                    // GOOGLE AUTO HINTS:
                    putInt("androidx.media.utils.extras.CONTENT_TYPE", 1) // Music
                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2) // List style
                })
                .build())
            .build())
            
        // 4. VERLAUF
        val historyIconUri = Uri.parse("android.resource://$packageName/drawable/ic_restore_white_24")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_HISTORY)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_history))
                .setSubtitle(app.getString(R.string.app_name))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(historyIconUri)
                .setExtras(Bundle().apply {
                    putString("android.media.metadata.DISPLAY_ICON_URI", historyIconUri.toString())
                    // GOOGLE AUTO HINTS:
                    putInt("androidx.media.utils.extras.CONTENT_TYPE", 1) // Music
                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2) // List style
                })
                .build())
            .build())
        
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
