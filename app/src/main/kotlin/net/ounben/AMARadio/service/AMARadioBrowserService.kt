package net.ounben.AMARadio.service

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.MediaBrowserServiceCompat
import net.ounben.AMARadio.IPlayerService
import net.ounben.AMARadio.AMARadioApp
import net.ounben.AMARadio.utils.GetRealLinkAndPlayTask

class AMARadioBrowserService : MediaBrowserServiceCompat() {
    private lateinit var AMARadioBrowser: AMARadioBrowser
    private var playerServiceConnection: ServiceConnection? = null
    private var playerService: IPlayerService? = null
    private var playTask: GetRealLinkAndPlayTask? = null

    private val playStationFromIdReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (MediaSessionCallback.BROADCAST_PLAY_STATION_BY_ID == action) {
                val stationId = intent.getStringExtra(MediaSessionCallback.EXTRA_STATION_ID)
                val station = stationId?.let { AMARadioBrowser.getStationById(it) }
                if (station != null) {
                    playTask?.cancel(false)
                    playTask = GetRealLinkAndPlayTask(context, station, playerService!!)
                    playTask!!.execute()
                }
            }
        }
    }

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
        val filter = IntentFilter()
        filter.addAction(MediaSessionCallback.BROADCAST_PLAY_STATION_BY_ID)
        LocalBroadcastManager.getInstance(this).registerReceiver(playStationFromIdReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerServiceConnection?.let { unbindService(it) }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return AMARadioBrowser.onGetRoot(clientPackageName, clientUid, rootHints)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        AMARadioBrowser.onLoadChildren(parentId, result)
    }
}
