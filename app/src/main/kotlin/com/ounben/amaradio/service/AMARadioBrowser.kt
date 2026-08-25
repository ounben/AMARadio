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
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext

class AMARadioBrowser(private val app: AMARadioApp) {
    private val stationIdToStation = java.util.concurrent.ConcurrentHashMap<String, DataRadioStation>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Lazy init databases to prevent blocking service startup
    private val stationDao by lazy { AMARadioDatabase.getDatabase(app).stationDao() }
    private val userDb by lazy { AMARadioUserDatabase.getDatabase(app) }

    fun onGetLibraryRoot(browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId(MEDIA_ID_ROOT)
            .setMediaMetadata(MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setTitle(app.resources.getString(R.string.app_name))
                .setExtras(Bundle().apply {
                    putInt("androidx.media.utils.extras.CONTENT_TYPE", 1) // Music
                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // LIST style
                })
                .build())
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    fun onGetChildren(browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Log.d("BROWSER", "onGetChildren for: $parentId, page: $page, size: $pageSize")
        
        return when (parentId) {
            MEDIA_ID_ROOT -> {
                val rootChildren = createBrowsableMediaItemsForRoot()
                Futures.immediateFuture(LibraryResult.ofItemList(paginate(rootChildren, page, pageSize), params))
            }
            MEDIA_ID_FILTERS_GROUP -> {
                scope.future {
                    val filterGroups = createFilterGroupMediaItems()
                    LibraryResult.ofItemList(paginate(filterGroups, page, pageSize), params)
                }
            }
            MEDIA_ID_MUSICS_FAVORITE -> {
                scope.future {
                    val stations = app.favouriteManager.getList()
                    val mediaItems = createMediaItemsFromStations(stations)
                    LibraryResult.ofItemList(paginate(mediaItems, page, pageSize), params)
                }
            }
            MEDIA_ID_MUSICS_HISTORY -> {
                scope.future {
                    val stations = app.historyManager.getList()
                    val mediaItems = createMediaItemsFromStations(stations)
                    LibraryResult.ofItemList(paginate(mediaItems, page, pageSize), params)
                }
            }
            MEDIA_ID_MUSICS_CUSTOM -> {
                scope.future {
                    val stations = app.customStationManager.getList()
                    val mediaItems = createMediaItemsFromStations(stations)
                    LibraryResult.ofItemList(paginate(mediaItems, page, pageSize), params)
                }
            }
            else -> {
                if (parentId.startsWith(MEDIA_ID_FILTER_PREFIX)) {
                    val filterId = parentId.substring(MEDIA_ID_FILTER_PREFIX.length)
                    scope.future {
                        val stations = if (filterId == "local") fetchLocalStations() else fetchStationsForFilter(filterId)
                        val mediaItems = createMediaItemsFromStations(stations)
                        LibraryResult.ofItemList(paginate(mediaItems, page, pageSize), params)
                    }
                } else {
                    scope.future {
                        val results = stationDao.getStationsFiltered(
                            name = parentId.ifEmpty { null },
                            countryCode = null,
                            language = null,
                            tag = null,
                            orderBy = "clickcount"
                        ).map { it.toDataStation() }
                        val mediaItems = createMediaItemsFromStations(results)
                        LibraryResult.ofItemList(paginate(mediaItems, page, pageSize), params)
                    }
                }
            }
        }
    }

    private fun paginate(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return ImmutableList.copyOf(items)
        val startIndex = (page * pageSize).coerceAtMost(items.size)
        val endIndex = (startIndex + pageSize).coerceAtMost(items.size)
        return if (startIndex < endIndex) {
            ImmutableList.copyOf(items.subList(startIndex, endIndex))
        } else {
            ImmutableList.of()
        }
    }

    private fun createMediaItemsFromStations(stations: List<DataRadioStation>): List<MediaItem> {
        val mediaItems = ArrayList<MediaItem>()
        for (station in stations) {
            stationIdToStation[station.StationUuid] = station
            val metadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station)
            mediaItems.add(MediaItem.Builder()
                .setMediaId(LEAF_PREFIX + station.StationUuid)
                .setMediaMetadata(metadata)
                .build())
        }
        return mediaItems
    }

    private suspend fun fetchStationsForFilter(filterId: String): List<DataRadioStation> {
        val tab = userDb.filterTabDao().getAllTabs().find { it.id == filterId } ?: return emptyList()
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
        val station = stationIdToStation[stationId] ?: app.favouriteManager.getById(stationId) ?: app.historyManager.getById(stationId) ?: app.customStationManager.getById(stationId)
        
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
        return scope.future {
            val results = stationDao.getStationsFiltered(
                name = query.ifEmpty { null },
                countryCode = null,
                language = null,
                tag = null,
                orderBy = "clickcount"
            ).map { it.toDataStation() }
            LibraryResult.ofItemList(ImmutableList.copyOf(createMediaItemsFromStations(results)), params)
        }
    }

    suspend fun resolveStationByKeywords(query: String): String? {
        val keywords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (keywords.isEmpty()) return null
        
        val favorites = app.favouriteManager.getList()
        favorites.find { station ->
            val name = station.Name.lowercase()
            keywords.all { name.contains(it) }
        }?.let { return it.StationUuid }
        
        val history = app.historyManager.getList()
        history.find { station ->
            val name = station.Name.lowercase()
            keywords.all { name.contains(it) }
        }?.let { return it.StationUuid }

        val customs = app.customStationManager.getList()
        customs.find { station ->
            val name = station.Name.lowercase()
            keywords.all { name.contains(it) }
        }?.let { return it.StationUuid }
        
        val firstKeyword = keywords[0]
        val results = stationDao.getStationsFiltered(
            name = firstKeyword, countryCode = null, language = null, tag = null, orderBy = "clickcount"
        )
        results.find { entity ->
            val name = entity.name?.lowercase() ?: ""
            keywords.all { name.contains(it) }
        }?.let { return it.stationUuid }
        
        return results.firstOrNull()?.stationUuid
    }

    fun getStationById(stationId: String): DataRadioStation? = stationIdToStation[stationId] ?: app.favouriteManager.getById(stationId) ?: app.historyManager.getById(stationId) ?: app.customStationManager.getById(stationId)

    private fun createBrowsableMediaItemsForRoot(): List<MediaItem> {
        val resources = app.resources
        val mediaItems = ArrayList<MediaItem>()
        val packageName = app.packageName

        val favoriteIconUri = Uri.parse("android.resource://$packageName/drawable/ic_star_white_24")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
            .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                resources.getString(R.string.nav_item_starred),
                favoriteIconUri
            ))
            .build())

        val customIconUri = Uri.parse("android.resource://$packageName/drawable/ic_list_white_24dp")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_CUSTOM)
            .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                resources.getString(R.string.tab_custom_stations),
                customIconUri
            ))
            .build())
            
        val historyIconUri = Uri.parse("android.resource://$packageName/drawable/ic_restore_white_24")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_HISTORY)
            .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                resources.getString(R.string.nav_item_history),
                historyIconUri
            ))
            .build())

        val filterIconUri = Uri.parse("android.resource://$packageName/drawable/ic_list_white_24dp")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_FILTERS_GROUP)
            .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                app.getString(R.string.action_filter),
                filterIconUri
            ))
            .build())
        
        return mediaItems
    }

    private suspend fun createFilterGroupMediaItems(): List<MediaItem> {
        val mediaItems = ArrayList<MediaItem>()
        val packageName = app.packageName

        val localIconUri = Uri.parse("android.resource://$packageName/drawable/ic_radio_white_24dp")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_FILTER_PREFIX + "local")
            .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                app.getString(R.string.action_local),
                localIconUri
            ))
            .build())

        val tabs = userDb.filterTabDao().getAllTabs()
        for (tab in tabs) {
            if (tab.label.isNotEmpty()) {
                val tabIconUri = Uri.parse("android.resource://$packageName/drawable/ic_list_white_24dp")
                mediaItems.add(MediaItem.Builder()
                    .setMediaId(MEDIA_ID_FILTER_PREFIX + tab.id)
                    .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                        tab.label,
                        tabIconUri
                    ))
                    .build())
            }
        }
        return mediaItems
    }

    companion object {
        const val MEDIA_ID_ROOT = "root_id"
        const val MEDIA_ID_FILTERS_GROUP = "filters_group_id"
        const val MEDIA_ID_MUSICS_FAVORITE = "favorites_id"
        const val MEDIA_ID_MUSICS_CUSTOM = "custom_id"
        const val MEDIA_ID_MUSICS_HISTORY = "history_id"
        const val MEDIA_ID_FILTER_PREFIX = "filter_"
        const val LEAF_PREFIX = "station_"

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
