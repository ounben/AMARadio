package com.ounben.amaradio.service

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.IPlayerService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture

class MediaSessionCallback(
    private val context: Context,
    private val playerService: IPlayerService,
    private val amaradioBrowser: AMARadioBrowser
) : MediaLibrarySession.Callback {

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        return MediaSession.ConnectionResult.accept(
            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
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

    override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (mediaItems.isNotEmpty()) {
            val mediaItem = mediaItems[0]
            val stationId = AMARadioBrowser.stationIdFromMediaId(mediaItem.mediaId)
            if (stationId.isNotEmpty()) {
                 val intent = Intent(BROADCAST_PLAY_STATION_BY_ID)
                 intent.putExtra(EXTRA_STATION_ID, stationId)
                 AppEventManager.sendEvent(intent)
            }
        }
        return super.onSetMediaItems(session, controller, mediaItems, startIndex, startPositionMs)
    }

    companion object {
        const val BROADCAST_PLAY_STATION_BY_ID = "PLAY_STATION_BY_ID"
        const val EXTRA_STATION_ID = "STATION_ID"
        const val ACTION_PLAY_STATION_BY_UUID = "PLAY_STATION_BY_UUID"
        const val EXTRA_STATION_UUID = "STATION_UUID"
    }
}
