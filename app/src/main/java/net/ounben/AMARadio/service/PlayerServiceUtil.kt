package net.ounben.AMARadio.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.util.TypedValue
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import net.ounben.AMARadio.BuildConfig
import net.ounben.AMARadio.IPlayerService
import net.ounben.AMARadio.R
import net.ounben.AMARadio.players.PlayState
import net.ounben.AMARadio.players.selector.PlayerType
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.station.live.ShoutcastInfo
import net.ounben.AMARadio.station.live.StreamLiveInfo

object PlayerServiceUtil {
    private var mainContext: Context? = null
    private var mBound = false
    private var serviceConnection: ServiceConnection? = null
    private var itsPlayerService: net.ounben.AMARadio.IPlayerService? = null

    @JvmStatic
    fun startService(context: Context) {
        if (mBound) return
        val anIntent = Intent(context, PlayerService::class.java)
        anIntent.putExtra(PlayerService.PLAYER_SERVICE_NO_NOTIFICATION_EXTRA, true)
        mainContext = context
        serviceConnection = getServiceConnection()
        context.bindService(anIntent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        mBound = true
    }

    @JvmStatic
    fun bindService(context: Context) {
        if (mBound) return
        mainContext = context
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
                if (BuildConfig.DEBUG) {
                    Log.d("PlayerServiceUtil", "PlayerServiceUtil: shutdownService")
                }
                val anIntent = Intent(context, PlayerService::class.java)
                unBind(context)
                context.stopService(anIntent)
                itsPlayerService = null
                serviceConnection = null
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d("PlayerServiceUtil", "PlayerServiceUtil: shutdownService E001:${e.message}")
                }
            }
        }
    }

    private fun getServiceConnection(): ServiceConnection {
        return object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, binder: IBinder) {
                if (BuildConfig.DEBUG) {
                    Log.d("PLAYER", "Service came online")
                }
                itsPlayerService = net.ounben.AMARadio.IPlayerService.Stub.asInterface(binder)
                val local = Intent()
                local.action = PlayerService.PLAYER_SERVICE_BOUND
                mainContext?.let { LocalBroadcastManager.getInstance(it).sendBroadcast(local) }
            }

            override fun onServiceDisconnected(className: ComponentName) {
                if (BuildConfig.DEBUG) {
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
                it.Play(false)
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
        if (fromUrl.isNullOrBlank()) {
            holder.setImageResource(R.drawable.ic_radio_24dp)
            return
        }
        val context = mainContext ?: return
        val r = context.resources
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, r.displayMetrics)

        val imageLoadCallback = object : Callback {
            override fun onSuccess() {}
            override fun onError(e: Exception) {
                Picasso.get()
                    .load(fromUrl)
                    .placeholder(R.drawable.ic_radio_24dp)
                    .error(R.drawable.ic_radio_24dp)
                    .resize(px.toInt(), 0)
                    .networkPolicy(NetworkPolicy.NO_CACHE)
                    .into(holder)
            }
        }

        Picasso.get()
            .load(fromUrl)
            .placeholder(R.drawable.ic_radio_24dp)
            .error(R.drawable.ic_radio_24dp)
            .resize(px.toInt(), 0)
            .networkPolicy(NetworkPolicy.OFFLINE)
            .into(holder, imageLoadCallback)
    }

    @JvmStatic
    fun getShoutcastInfo(): ShoutcastInfo? {
        return try {
            itsPlayerService?.getShoutcastInfo()
        } catch (e: RemoteException) {
            Log.e("", "$e")
            null
        }
    }

    @JvmStatic
    fun startRecording() {
        try {
            itsPlayerService?.startRecording()
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun stopRecording() {
        try {
            itsPlayerService?.stopRecording()
        } catch (e: RemoteException) {
            Log.e("", "$e")
        }
    }

    @JvmStatic
    fun isRecording(): Boolean {
        return try {
            itsPlayerService?.isRecording() ?: false
        } catch (e: RemoteException) {
            Log.e("", "$e")
            false
        }
    }

    @JvmStatic
    fun getCurrentRecordFileName(): String? {
        return try {
            itsPlayerService?.getCurrentRecordFileName()
        } catch (e: RemoteException) {
            Log.e("", "$e")
            null
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
