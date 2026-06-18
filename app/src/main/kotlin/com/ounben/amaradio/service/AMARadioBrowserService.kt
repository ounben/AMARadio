package com.ounben.amaradio.service

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.IPlayerService
import com.ounben.amaradio.utils.GetRealLinkAndPlayTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AMARadioBrowserService : MediaBrowserServiceCompat() {
    private lateinit var AMARadioBrowser: AMARadioBrowser
    private var playerServiceConnection: ServiceConnection? = null
    private var playerService: IPlayerService? = null
    private var playTask: GetRealLinkAndPlayTask? = null
    private var eventJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        AMARadioBrowser = AMARadioBrowser(application as AMARadioApp)
        val anIntent = Intent(this, PlayerService::class.java)
        anIntent.putExtra(PlayerService.PLAYER_SERVICE_NO_NOTIFICATION_EXTRA, true)
        startService(anIntent)
        playerServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                playerService = IPlayerService.Stub.asInterface(iBinder)
                try {
                    setSessionToken(playerService!!.mediaSessionToken)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
            override fun onServiceDisconnected(componentName: ComponentName) {
                playerService = null
            }
        }
        bindService(anIntent, playerServiceConnection!!, BIND_AUTO_CREATE)

        eventJob = CoroutineScope(Dispatchers.Main).launch {
            AppEventManager.events.collect { intent ->
                if (MediaSessionCallback.BROADCAST_PLAY_STATION_BY_ID == intent.action) {
                    val stationId = intent.getStringExtra(MediaSessionCallback.EXTRA_STATION_ID)
                    val station = stationId?.let { AMARadioBrowser.getStationById(it) }
                    if (station != null) {
                        playTask?.cancel(false)
                        playTask = GetRealLinkAndPlayTask(this@AMARadioBrowserService, station, playerService!!)
                        playTask!!.execute()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerServiceConnection?.let { unbindService(it) }
        eventJob?.cancel()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return AMARadioBrowser.onGetRoot(clientPackageName, clientUid, rootHints)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        AMARadioBrowser.onLoadChildren(parentId, result)
    }
}
