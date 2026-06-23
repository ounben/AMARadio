package com.ounben.amaradio.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.util.TypedValue
import android.widget.ImageView
import coil.load
import coil.request.CachePolicy
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.IPlayerService
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.players.selector.PlayerType
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.live.StreamLiveInfo

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlayerServiceUtil {
    private var mainContext: Context? = null
    private var mBound = false
    private var serviceConnection: ServiceConnection? = null
    private var itsPlayerService: IPlayerService? = null

    @JvmStatic
    fun startService(context: Context) {
        mainContext = context
        val anIntent = Intent(context, PlayerService::class.java)
        
        // Use regular startService to avoid background-start restrictions during app launch.
        // The service will promote itself to foreground only when playback starts.
        context.startService(anIntent)

        if (!mBound) {
            serviceConnection = getServiceConnection()
            context.bindService(anIntent, serviceConnection!!, Context.BIND_AUTO_CREATE)
            mBound = true
        }
    }

    @JvmStatic
    fun bindService(context: Context) {
        mainContext = context
        if (mBound) return
        serviceConnection = getServiceConnection()
        val anIntent = Intent(context, PlayerService::class.java)
        context.bindService(anIntent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        mBound = true
    }

    private fun unBind(context: Context) {
        try {
            serviceConnection?.let { context.unbindService(it) }
        } catch (e: Exception) {
        }
        serviceConnection = null
        mBound = false
    }

    @JvmStatic
    fun shutdownService() {
        mainContext?.let { context ->
            try {
                if (Utils.isDebug) {
                    Log.d("PlayerServiceUtil", "PlayerServiceUtil: shutdownService")
                }
                val anIntent = Intent(context, PlayerService::class.java)
                unBind(context)
                context.stopService(anIntent)
                itsPlayerService = null
                serviceConnection = null
            } catch (e: Exception) {
                if (Utils.isDebug) {
                    Log.d("PlayerServiceUtil", "PlayerServiceUtil: shutdownService E001:${e.message}")
                }
            }
        }
    }

    private fun getServiceConnection(): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, binder: IBinder) {
                if (Utils.isDebug) {
                    Log.d("PLAYER", "Service came online")
                }
                itsPlayerService = IPlayerService.Stub.asInterface(binder)
                val local = Intent()
                local.action = PlayerService.PLAYER_SERVICE_BOUND
                AppEventManager.sendEvent(local)
            }

            override fun onServiceDisconnected(className: ComponentName) {
                if (Utils.isDebug) {
                    Log.d("PLAYER", "Service offline")
                }
                mainContext?.let { unBind(it) }
                itsPlayerService = null
            }
        }
    }

    @JvmStatic
    fun isServiceBound(): Boolean = itsPlayerService != null

    @JvmStatic
    fun isPlaying(): Boolean {
        return try {
            itsPlayerService?.isPlaying ?: false
        } catch (e: RemoteException) {
            false
        }
    }

    @JvmStatic
    fun getPlayerState(): PlayState {
        return try {
            itsPlayerService?.playerState ?: PlayState.Idle
        } catch (e: RemoteException) {
            PlayState.Idle
        }
    }

    @JvmStatic
    fun stop() {
        try {
            itsPlayerService?.Stop()
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun play(station: DataRadioStation) {
        try {
            itsPlayerService?.let {
                it.SetStation(station)
                it.Play()
            }
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun setStation(station: DataRadioStation) {
        try {
            itsPlayerService?.SetStation(station)
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun skipToNext() {
        try {
            itsPlayerService?.SkipToNext()
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun skipToPrevious() {
        try {
            itsPlayerService?.SkipToPrevious()
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun pause(pauseReason: PauseReason) {
        try {
            itsPlayerService?.Pause(pauseReason)
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun resume() {
        try {
            itsPlayerService?.Resume()
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun clearTimer() {
        try {
            itsPlayerService?.clearTimer()
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun addTimer(secondsAdd: Int) {
        try {
            itsPlayerService?.addTimer(secondsAdd)
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun getTimerSeconds(): Long {
        return try {
            itsPlayerService?.timerSeconds ?: 0
        } catch (e: RemoteException) {
            Log.e("", "$e")
            0
        }
    }

    @JvmStatic
    fun getMetadataLive(): StreamLiveInfo {
        return try {
            itsPlayerService?.metadataLive ?: StreamLiveInfo(null)
        } catch (e: RemoteException) {
            Log.e("", "$e")
            StreamLiveInfo(null)
        }
    }

    @JvmStatic
    fun getStationId(): String? {
        return try {
            itsPlayerService?.currentStationID
        } catch (e: RemoteException) {
            Log.e("", "$e")
            null
        }
    }

    @JvmStatic
    fun getStationInCurrentStation(): DataRadioStation? {
        return try {
            itsPlayerService?.currentStation
        } catch (e: RemoteException) {
            Log.e("", "$e")
            null
        }
    }

    @JvmStatic
    fun getCurrentStation(): DataRadioStation? {
        return try {
            itsPlayerService?.currentStation
        } catch (e: RemoteException) {
            Log.e("", "$e")
            null
        }
    }

    @JvmStatic
    fun getStationIcon(holder: ImageView, fromUrl: String?) {
        val context = mainContext ?: holder.context
        val r = context.resources
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, r.displayMetrics)

        if (fromUrl.isNullOrBlank() || fromUrl == "null") {
            holder.load(R.drawable.ic_radio_24dp) {
                size(px.toInt(), px.toInt())
                crossfade(true)
            }
            return
        }

        holder.load(fromUrl) {
            placeholder(R.drawable.ic_radio_24dp)
            error(R.drawable.ic_radio_24dp)
            if (px > 0) {
                size(px.toInt(), px.toInt())
            }
            crossfade(true)
            diskCachePolicy(CachePolicy.ENABLED)
            networkCachePolicy(CachePolicy.ENABLED)
        }
    }

    @JvmStatic
    fun getIsHls(): Boolean {
        return try {
            itsPlayerService?.getIsHls() ?: false
        } catch (e: RemoteException) {
            Log.e("", "$e")
            false
        }
    }

    @JvmStatic
    fun getTransferredBytes(): Long {
        return try {
            itsPlayerService?.getTransferredBytes() ?: 0
        } catch (e: RemoteException) {
            Log.e("", "$e")
            0
        }
    }

    @JvmStatic
    fun getBufferedSeconds(): Long {
        return try {
            itsPlayerService?.getBufferedSeconds() ?: 0
        } catch (e: RemoteException) {
            Log.e("", "$e")
            0
        }
    }

    @JvmStatic
    fun getLastPlayStartTime(): Long {
        return try {
            itsPlayerService?.getLastPlayStartTime() ?: 0
        } catch (e: RemoteException) {
            Log.e("", "$e")
            0
        }
    }

    @JvmStatic
    fun getPauseReason(): PauseReason {
        return try {
            itsPlayerService?.getPauseReason() ?: PauseReason.NONE
        } catch (e: RemoteException) {
            Log.e("", "$e")
            PauseReason.NONE
        }
    }

    @JvmStatic
    fun enableMPD(hostname: String, port: Int) {
        try {
            itsPlayerService?.enableMPD(hostname, port)
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun disableMPD() {
        try {
            itsPlayerService?.disableMPD()
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun warnAboutMeteredConnection(playerType: PlayerType) {
        try {
            itsPlayerService?.warnAboutMeteredConnection(playerType)
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun isNotificationActive(): Boolean {
        return try {
            itsPlayerService?.isNotificationActive() ?: false
        } catch (e: RemoteException) {
            Log.e("", "$e")
            false
        }
    }
}
