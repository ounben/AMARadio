package com.ounben.amaradio.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.ounben.amaradio.IPlayerService
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.utils.GetRealLinkAndPlayTask
import com.ounben.amaradio.service.PauseReason
import com.ounben.amaradio.service.AMARadioBrowser

class MediaSessionCallback(private val context: Context, private val playerService: IPlayerService) : MediaSessionCompat.Callback() {

    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        val event = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (event?.keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isLongPress) {
                try {
                    if (playerService.isPlaying) {
                        playerService.Pause(PauseReason.USER)
                    } else {
                        playerService.Resume()
                    }
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
            return true
        }
        return super.onMediaButtonEvent(mediaButtonEvent)
    }

    override fun onPause() {
        try {
            playerService.Pause(PauseReason.USER)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun onPlay() {
        try {
            playerService.Resume()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun onSkipToNext() {
        try {
            playerService.SkipToNext()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun onSkipToPrevious() {
        try {
            playerService.SkipToPrevious()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        try {
            playerService.Stop()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
        val stationId = AMARadioBrowser.stationIdFromMediaId(mediaId)
        if (stationId.isNotEmpty()) {
            val intent = Intent(BROADCAST_PLAY_STATION_BY_ID)
            intent.putExtra(EXTRA_STATION_ID, stationId)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }

    override fun onPlayFromSearch(query: String, extras: Bundle?) {
        var q = query
        // remove voice search residues like " with AMARadio"
        q = q.replace("(?i) \\w+ radio\\s*droid.*".toRegex(), "")
        val app = context.applicationContext as AMARadioApp
        var station = app.favouriteManager.getBestNameMatch(q)
        if (station == null) station = app.historyManager.getBestNameMatch(q)
        if (station == null) station = app.fallbackStationsManager.getBestNameMatch(q)
        station?.let {
            val playTask = GetRealLinkAndPlayTask(context, it, playerService)
            playTask.execute()
        }
    }

    companion object {
        const val BROADCAST_PLAY_STATION_BY_ID = "PLAY_STATION_BY_ID"
        const val EXTRA_STATION_ID = "STATION_ID"
        const val ACTION_PLAY_STATION_BY_UUID = "PLAY_STATION_BY_UUID"
        const val EXTRA_STATION_UUID = "STATION_UUID"
    }
}
