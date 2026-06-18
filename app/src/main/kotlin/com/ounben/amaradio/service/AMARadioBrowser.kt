package com.ounben.amaradio.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation

class AMARadioBrowser(private val AMARadioApp: AMARadioApp) {
    private val stationIdToStation = HashMap<String, DataRadioStation>()

    fun onGetLibraryRoot(browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId(MEDIA_ID_ROOT)
            .setMediaMetadata(MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setTitle(AMARadioApp.resources.getString(R.string.app_name))
                .build())
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    fun onGetChildren(browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (MEDIA_ID_ROOT == parentId) {
            val rootChildren = createBrowsableMediaItemsForRoot()
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(rootChildren), params))
        }

        var stations: List<DataRadioStation>? = null
        when (parentId) {
            MEDIA_ID_MUSICS_FAVORITE -> stations = AMARadioApp.favouriteManager.getList()
            MEDIA_ID_MUSICS_HISTORY -> stations = AMARadioApp.historyManager.getList()
        }

        if (stations != null) {
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
                    .setMediaId(MEDIA_ID_MUSICS_HISTORY + LEAF_SEPARATOR + station.StationUuid)
                    .setMediaMetadata(metadata.build())
                    .build())
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
        }

        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params))
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
        return Futures.immediateFuture(LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE))
    }

    fun getStationById(stationId: String): DataRadioStation? = stationIdToStation[stationId]

    private fun createBrowsableMediaItemsForRoot(): List<MediaItem> {
        val resources = AMARadioApp.resources
        val mediaItems = ArrayList<MediaItem>()
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_starred))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build())
        mediaItems.add(MediaItem.Builder()
            .setMediaId(MEDIA_ID_MUSICS_HISTORY)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(resources.getString(R.string.nav_item_history))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build())
        return mediaItems
    }

    companion object {
        private const val MEDIA_ID_ROOT = "__ROOT__"
        private const val MEDIA_ID_MUSICS_FAVORITE = "__FAVORITE__"
        private const val MEDIA_ID_MUSICS_HISTORY = "__HISTORY__"
        private const val LEAF_SEPARATOR = '|'

        @JvmStatic
        fun stationIdFromMediaId(mediaId: String?): String {
            if (mediaId == null) return ""
            val separatorIdx = mediaId.indexOf(LEAF_SEPARATOR)
            return if (separatorIdx <= 0) mediaId else mediaId.substring(separatorIdx + 1)
        }
    }
}
