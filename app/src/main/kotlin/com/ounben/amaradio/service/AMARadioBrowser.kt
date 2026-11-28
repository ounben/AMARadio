package com.ounben.amaradio.service

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
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
import com.ounben.amaradio.favorites.FavoriteListHelper
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

    @androidx.annotation.OptIn(UnstableApi::class)
    fun onGetLibraryRoot(browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val rootExtras = Bundle(params?.extras ?: Bundle.EMPTY).apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            )
        }
        val rootParams = LibraryParams.Builder()
            .setRecent(params?.isRecent ?: false)
            .setOffline(params?.isOffline ?: false)
            .setSuggested(params?.isSuggested ?: false)
            .setExtras(rootExtras)
            .build()

        val rootItem = MediaItem.Builder()
            .setMediaId(MEDIA_ID_ROOT)
            .setMediaMetadata(MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setTitle(app.resources.getString(R.string.app_name))
                .setExtras(Bundle().apply {
                    putInt("androidx.media.utils.extras.CONTENT_TYPE", 1) // Music
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
                    )
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build())
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootParams))
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
                    val mediaItems = createFavoriteAndPlaylistMediaItems()
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
                if (parentId.startsWith(MEDIA_ID_PLAYLIST_PREFIX)) {
                    val playlistId = parentId.substring(MEDIA_ID_PLAYLIST_PREFIX.length)
                    scope.future {
                        val stations = fetchStationsForPlaylist(playlistId)
                        val mediaItems = createMediaItemsFromStations(stations, playlistId = playlistId)
                        LibraryResult.ofItemList(paginate(mediaItems, page, pageSize), params)
                    }
                } else if (parentId.startsWith(MEDIA_ID_FILTER_PREFIX)) {
                    val filterId = parentId.substring(MEDIA_ID_FILTER_PREFIX.length)
                    scope.future {
                        val stations = if (filterId == "local") fetchLocalStations() else fetchStationsForFilter(filterId)
                        val mediaItems = createMediaItemsFromStations(stations)
                        LibraryResult.ofItemList(paginate(mediaItems, page, pageSize), params)
                    }
                } else {
                    scope.future {
                        val words = parentId.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                        val results = stationDao.getStationsFiltered(
                            w1 = words.getOrNull(0), 
                            w2 = words.getOrNull(1), 
                            w3 = words.getOrNull(2),
                            countryCode = null,
                            language = null,
                            tag1 = null,
                            tag2 = null,
                            tag3 = null,
                            tagOperator = "AND",
                            codec = null,
                            bitrate = 0,
                            bitrateOperator = ">=",
                            orderBy = "Name",
                            reverse = 0
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

    private fun createMediaItemsFromStations(
        stations: List<DataRadioStation>,
        playlistId: String? = null,
        groupTitle: String? = null
    ): List<MediaItem> {
        val mediaItems = ArrayList<MediaItem>(stations.size)
        for (station in stations) {
            stationIdToStation[station.StationUuid] = station
            val metadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(
                station = station,
                groupTitle = groupTitle
            )
            val mediaId = if (!playlistId.isNullOrEmpty()) {
                buildPlaylistStationMediaId(playlistId, station.StationUuid)
            } else {
                LEAF_PREFIX + station.StationUuid
            }
            mediaItems.add(MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(metadata)
                .build())
        }
        return mediaItems
    }

    private suspend fun createFavoriteAndPlaylistMediaItems(): List<MediaItem> {
        app.userDbMigrationJob?.join()
        app.favouriteManager.awaitLoaded()

        val allFavorites = app.favouriteManager.getList()
        val lists = userDb.favoriteListDao().getAllLists()
        if (lists.isEmpty()) {
            return createMediaItemsFromStations(allFavorites)
        }

        val allItems = userDb.favoriteListDao().getAllItems()
        val counts = FavoriteListHelper.computeListCounts(allFavorites, allItems)
        val playlistIconUri = Uri.parse("android.resource://${app.packageName}/drawable/ic_playlist_white_24dp")

        val mediaItems = ArrayList<MediaItem>(lists.size + allFavorites.size)
        for (list in lists) {
            val count = counts[list.id] ?: 0
            mediaItems.add(
                MediaItem.Builder()
                    .setMediaId(MEDIA_ID_PLAYLIST_PREFIX + list.id)
                    .setMediaMetadata(
                        com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                            title = "${list.name} ($count)",
                            iconUri = playlistIconUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    )
                    .build()
            )
        }

        val allSectionTitle = app.getString(R.string.favorite_list_all)
        mediaItems.addAll(createMediaItemsFromStations(allFavorites, groupTitle = allSectionTitle))
        return mediaItems
    }

    private suspend fun fetchStationsForPlaylist(playlistId: String): List<DataRadioStation> {
        app.userDbMigrationJob?.join()
        app.favouriteManager.awaitLoaded()

        val allFavorites = app.favouriteManager.getList()
        val allItems = userDb.favoriteListDao().getAllItems()
        return FavoriteListHelper.filterStationsForList(allFavorites, allItems, playlistId)
    }

    private suspend fun fetchStationsForFilter(filterId: String): List<DataRadioStation> {
        val tab = userDb.filterTabDao().getAllTabs().find { it.id == filterId } ?: return emptyList()
        val words = tab.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = stationDao.getStationsFiltered(
            w1 = words.getOrNull(0),
            w2 = words.getOrNull(1),
            w3 = words.getOrNull(2),
            countryCode = tab.countryCode.ifEmpty { null },
            language = tab.languageCode.ifEmpty { null },
            tag1 = tab.tag.ifEmpty { null },
            tag2 = tab.tag2.ifEmpty { null },
            tag3 = tab.tag3.ifEmpty { null },
            tagOperator = tab.tagOperator,
            codec = tab.codec.ifEmpty { null },
            bitrate = tab.bitrate,
            bitrateOperator = tab.bitrateOperator,
            orderBy = tab.sortBy,
            reverse = if (tab.reverse) 1 else 0
        )
        return results.map { it.toDataStation() }
    }

    private suspend fun fetchLocalStations(): List<DataRadioStation> {
        val countryCode = com.ounben.amaradio.Utils.getCountryCode(app)?.uppercase() ?: return emptyList()
        val results = stationDao.getStationsByCountryCode(countryCode)
        return results.map { it.toDataStation() }
    }

    fun onGetItem(browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        if (mediaId.startsWith(MEDIA_ID_PLAYLIST_PREFIX)) {
            val playlistId = mediaId.substring(MEDIA_ID_PLAYLIST_PREFIX.length)
            return scope.future {
                val list = userDb.favoriteListDao().getAllLists().find { it.id == playlistId }
                    ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                val allFavorites = app.favouriteManager.getList()
                val allItems = userDb.favoriteListDao().getAllItems()
                val count = FavoriteListHelper.computeListCounts(allFavorites, allItems)[list.id] ?: 0
                val playlistIconUri = Uri.parse("android.resource://${app.packageName}/drawable/ic_playlist_white_24dp")
                val item = MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(
                        com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                            title = "${list.name} ($count)",
                            iconUri = playlistIconUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    )
                    .build()
                LibraryResult.ofItem(item, null)
            }
        }

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
            val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val results = stationDao.getStationsFiltered(
                w1 = words.getOrNull(0),
                w2 = words.getOrNull(1),
                w3 = words.getOrNull(2),
                countryCode = null,
                language = null,
                tag1 = null,
                tag2 = null,
                tag3 = null,
                tagOperator = "AND",
                codec = null,
                bitrate = 0,
                bitrateOperator = ">=",
                orderBy = "Name",
                reverse = 0
            ).map { it.toDataStation() }
            LibraryResult.ofItemList(ImmutableList.copyOf(createMediaItemsFromStations(results)), params)
        }
    }

    data class ResolvedMedia(val stationId: String, val playlistId: String? = null)

    suspend fun resolveStationByKeywords(query: String): String? = resolvePlaybackByKeywords(query)?.stationId

    suspend fun resolvePlaybackByKeywords(query: String): ResolvedMedia? {
        val trimmed = query.trim()
        val keywords = trimmed.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (keywords.isEmpty()) return null

        // 1. Exact favorite playlist name match (e.g. "Play Jazz on AMARadio")
        val favoriteLists = userDb.favoriteListDao().getAllLists()
        val exactPlaylist = favoriteLists.find { it.name.equals(trimmed, ignoreCase = true) }
        if (exactPlaylist != null) {
            val playlistStations = fetchStationsForPlaylist(exactPlaylist.id)
            playlistStations.firstOrNull()?.let { first ->
                stationIdToStation[first.StationUuid] = first
                return ResolvedMedia(first.StationUuid, exactPlaylist.id)
            }
        }
        
        val favorites = app.favouriteManager.getList()
        favorites.find { station ->
            val name = station.Name.lowercase()
            keywords.all { name.contains(it) }
        }?.let { return ResolvedMedia(it.StationUuid) }
        
        val history = app.historyManager.getList()
        history.find { station ->
            val name = station.Name.lowercase()
            keywords.all { name.contains(it) }
        }?.let { return ResolvedMedia(it.StationUuid) }

        val customs = app.customStationManager.getList()
        customs.find { station ->
            val name = station.Name.lowercase()
            keywords.all { name.contains(it) }
        }?.let { return ResolvedMedia(it.StationUuid) }

        // 2. Partial favorite playlist keyword match before global station DB fallback
        val keywordPlaylist = favoriteLists.find { list ->
            val name = list.name.lowercase()
            keywords.all { name.contains(it) }
        }
        if (keywordPlaylist != null) {
            val playlistStations = fetchStationsForPlaylist(keywordPlaylist.id)
            playlistStations.firstOrNull()?.let { first ->
                stationIdToStation[first.StationUuid] = first
                return ResolvedMedia(first.StationUuid, keywordPlaylist.id)
            }
        }
        
        val results = stationDao.getStationsFiltered(
            w1 = keywords.getOrNull(0), 
            w2 = keywords.getOrNull(1), 
            w3 = keywords.getOrNull(2), 
            countryCode = null, 
            language = null, 
            tag1 = null,
            tag2 = null,
            tag3 = null,
            tagOperator = "AND", 
            codec = null,
            bitrate = 0,
            bitrateOperator = ">=",
            orderBy = "Name", 
            reverse = 0
        )
        results.find { entity ->
            val name = entity.name?.lowercase() ?: ""
            keywords.all { name.contains(it) }
        }?.let { return ResolvedMedia(it.stationUuid) }
        
        return results.firstOrNull()?.stationUuid?.let { ResolvedMedia(it) }
    }

    fun getStationById(stationId: String): DataRadioStation? = stationIdToStation[stationId] ?: app.favouriteManager.getById(stationId) ?: app.historyManager.getById(stationId) ?: app.customStationManager.getById(stationId)

    private fun createBrowsableMediaItemsForRoot(): List<MediaItem> {
        val resources = app.resources
        val mediaItems = ArrayList<MediaItem>()
        val packageName = app.packageName

        // 1. Lokal (Matches the Local tab in AMARadio station list)
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
        val manualCountry = sp.getString("settings_manual_country_code", "SYSTEM")
        val localTitle = if (!manualCountry.isNullOrEmpty() && manualCountry != "SYSTEM") {
            val flag = com.ounben.amaradio.utils.EmojiUtils.getFlagEmoji(manualCountry.uppercase()) ?: ""
            if (flag.isNotEmpty()) "$flag ${manualCountry.uppercase()}" else manualCountry.uppercase()
        } else {
            resources.getString(R.string.action_local)
        }

        val localIconUri = Uri.parse("android.resource://$packageName/drawable/ic_radio_white_24dp")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_FILTER_PREFIX + "local")
            .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                localTitle,
                localIconUri
            ))
            .build())

        // 2. Favorit
        val favoriteIconUri = Uri.parse("android.resource://$packageName/drawable/ic_star_white_24")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
            .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                resources.getString(R.string.nav_item_starred),
                favoriteIconUri
            ))
            .build())

        // 3. History
        val historyIconUri = Uri.parse("android.resource://$packageName/drawable/ic_restore_white_24")
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_HISTORY)
            .setMediaMetadata(com.ounben.amaradio.players.exoplayer.Media3Utils.buildFolderMetadata(
                resources.getString(R.string.nav_item_history),
                historyIconUri
            ))
            .build())

        // 4. Filter
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
        const val MEDIA_ID_PLAYLIST_PREFIX = "playlist_"
        const val LEAF_PREFIX = "station_"
        const val PLAYLIST_LEAF_PREFIX = "plstation_"
        const val PLAYLIST_MEDIA_ID_SEPARATOR = "|"

        @JvmStatic
        fun buildPlaylistStationMediaId(playlistId: String, stationUuid: String): String {
            return "$PLAYLIST_LEAF_PREFIX$playlistId$PLAYLIST_MEDIA_ID_SEPARATOR$stationUuid"
        }

        @JvmStatic
        fun playlistIdFromMediaId(mediaId: String?): String? {
            if (mediaId == null || !mediaId.startsWith(PLAYLIST_LEAF_PREFIX)) return null
            val payload = mediaId.substring(PLAYLIST_LEAF_PREFIX.length)
            val sepIndex = payload.indexOf(PLAYLIST_MEDIA_ID_SEPARATOR)
            if (sepIndex <= 0) return null
            return payload.substring(0, sepIndex)
        }

        @JvmStatic
        fun stationIdFromMediaId(mediaId: String?): String {
            if (mediaId == null) return ""
            if (mediaId.startsWith(PLAYLIST_LEAF_PREFIX)) {
                val sepIndex = mediaId.indexOf(PLAYLIST_MEDIA_ID_SEPARATOR, PLAYLIST_LEAF_PREFIX.length)
                if (sepIndex >= 0 && sepIndex + 1 < mediaId.length) {
                    return mediaId.substring(sepIndex + 1)
                }
            }
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
