package com.ounben.amaradio.service

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.ounben.amaradio.AppEventManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

class MediaSessionCallback(
    private val context: Context,
    private val amaradioBrowser: AMARadioBrowser
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)
            .build()

        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_PLAY_PAUSE)
            .build()

        return MediaSession.ConnectionResult.accept(
            sessionCommands,
            playerCommands
        )
    }

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        return amaradioBrowser.onGetLibraryRoot(browser, params)
    }

    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return amaradioBrowser.onGetChildren(browser, parentId, page, pageSize, params)
    }

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        return amaradioBrowser.onGetItem(browser, mediaId)
    }

    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, 10, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return scope.future {
            val resolvedItems = mediaItems.map { item ->
                var finalItem = item
                val searchQuery = item.requestMetadata.searchQuery
                
                // 1. Resolve Search Query to Station ID
                val stationId = if (!searchQuery.isNullOrEmpty()) {
                    amaradioBrowser.resolveStationByKeywords(searchQuery)
                } else {
                    AMARadioBrowser.stationIdFromMediaId(item.mediaId)
                }

                if (!stationId.isNullOrEmpty()) {
                    // 2. Notify Service to start playback with proper warnings/history
                    val intent = Intent(BROADCAST_PLAY_STATION_BY_ID)
                    intent.putExtra(EXTRA_STATION_ID, stationId)
                    AppEventManager.sendEvent(intent)

                    // 3. Build valid MediaItem for immediate player preparation
                    val service = context as? PlayerService
                    val station = service?.amaradioBrowser?.getStationById(stationId)
                    if (station != null) {
                        val streamUrl = (if (!station.playableUrl.isNullOrEmpty()) station.playableUrl else station.StreamUrl) ?: ""
                        if (streamUrl.isNotEmpty()) {
                            val fullMetadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station)
                            finalItem = com.ounben.amaradio.players.exoplayer.Media3Utils.buildLiveMediaItem(
                                streamUrl.toUri(),
                                fullMetadata,
                                station.StationUuid
                            )
                        }
                    }
                }
                finalItem
            }
            MediaSession.MediaItemsWithStartPosition(resolvedItems, startIndex, startPositionMs)
        }
    }

    override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
        val resolvedItems = mediaItems.map { item ->
            val stationId = AMARadioBrowser.stationIdFromMediaId(item.mediaId)
            val service = context as? PlayerService
            val station = service?.amaradioBrowser?.getStationById(stationId)
            
            if (station != null) {
                val streamUrl = (if (!station.playableUrl.isNullOrEmpty()) station.playableUrl else station.StreamUrl) ?: ""
                if (streamUrl.isNotEmpty()) {
                    val fullMetadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station)
                    return@map com.ounben.amaradio.players.exoplayer.Media3Utils.buildLiveMediaItem(
                        android.net.Uri.parse(streamUrl),
                        fullMetadata,
                        item.mediaId
                    )
                }
            }
            item
        }.toMutableList()
        
        return Futures.immediateFuture(resolvedItems)
    }

    @Deprecated("Deprecated in Media3")
    override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val player = session.player
        val currentItem = player.currentMediaItem
        
        return if (currentItem != null) {
            Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(listOf(currentItem), 0, 0))
        } else {
            // Fallback: If no item is set, try to get the last played station from the service
            val service = context as? PlayerService
            val station = service?.itsCurrentStation
            if (station != null) {
                val streamUrl = (if (!station.playableUrl.isNullOrEmpty()) station.playableUrl else station.StreamUrl) ?: ""
                val fullMetadata = com.ounben.amaradio.players.exoplayer.Media3Utils.buildMetadata(station)
                val item = com.ounben.amaradio.players.exoplayer.Media3Utils.buildLiveMediaItem(
                    android.net.Uri.parse(streamUrl),
                    fullMetadata,
                    station.StationUuid
                )
                Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0))
            } else {
                Futures.immediateFailedFuture(UnsupportedOperationException("No media item to resume"))
            }
        }
    }

    companion object {
        const val BROADCAST_PLAY_STATION_BY_ID = "PLAY_STATION_BY_ID"
        const val EXTRA_STATION_ID = "STATION_ID"
        const val ACTION_PLAY_STATION_BY_UUID = "PLAY_STATION_BY_UUID"
        const val EXTRA_STATION_UUID = "STATION_UUID"
    }
}
