package com.ounben.amaradio.players.exoplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.preference.PreferenceManager
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.players.PlayerWrapper
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@UnstableApi
class ExoPlayerWrapper(private val context: Context, looper: Looper) : PlayerWrapper, TransferListener {
    private var internalPlayer: ExoPlayer
    private var stateListener: PlayerWrapper.PlayListener? = null
    private var streamUrl: String? = null
    
    private var bytesTransferred: Long = 0
    override val currentPlaybackTransferredBytes: Long
        get() = bytesTransferred
    
    private var isHls = false
    private var isPlayingFlag = false
    private val playerThreadHandler = Handler(looper)
    private var audioSource: MediaSource? = null
    private var currentVolume = 1.0f

    private val networkChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
                if (Utils.hasAnyConnection(context)) {
                    context.unregisterReceiver(this)
                    resumeWhenNetworkConnected()
                }
            }
        }
    }

    init {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50_000, // Min buffer 50s
                100_000, // Max buffer 100s
                2_500, // Buffer for playback 2.5s
                5_000 // Buffer for playback after rebuffer 5s
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        internalPlayer = ExoPlayer.Builder(context)
            .setLooper(looper)
            .setLoadControl(loadControl)
            // IMPORTANT: set handleAudioFocus to FALSE because PlayerService manages focus manually.
            // This prevents a conflict where focus is requested twice and then revoked from the service.
            .setAudioAttributes(audioAttributes, false) 
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        
        internalPlayer.addListener(PlayerEventListener())
    }

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context) {
        this.streamUrl = streamUrl
        isHls = Utils.urlIndicatesHlsStream(streamUrl)
        bytesTransferred = 0

        // Dedicated OkHttpClient with requested timeouts
        val dedicatedClient = httpClient.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        // We don't send Icy-MetaData: 1 here to avoid interleaved stream chunks that OkHttpDataSource can't handle.
        // ExoPlayer will still extract what it can from HTTP headers.
        val dataSourceFactory = OkHttpDataSource.Factory(dedicatedClient)
            .setUserAgent("AMARadio/0.99.2")
            .setTransferListener(this)

        audioSource = if (isHls) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(streamUrl.toUri()))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(CustomLoadErrorHandlingPolicy())
                .createMediaSource(MediaItem.fromUri(streamUrl.toUri()))
        }

        playerThreadHandler.post {
            internalPlayer.volume = currentVolume
            internalPlayer.setMediaSource(audioSource!!, true)
            internalPlayer.prepare()
            internalPlayer.playWhenReady = true
            Log.d("ExoPlayerWrapper", "Player preparing with OkHttpDataSource. Focus handling: OFF")
        }
    }

    override fun pause() {
        playerThreadHandler.post {
            internalPlayer.playWhenReady = false
            isPlayingFlag = false
        }
    }

    override fun stop() {
        isPlayingFlag = false
        playerThreadHandler.post {
            internalPlayer.stop()
        }
    }

    override fun isPlaying(): Boolean = isPlayingFlag

    override val player: Player
        get() = internalPlayer

    override val bufferedMs: Long
        get() {
            val pos = internalPlayer.bufferedPosition
            val current = internalPlayer.currentPosition
            return if (pos > current) pos - current else 0
        }

    override val audioSessionId: Int
        get() = internalPlayer.audioSessionId

    override val isLocal: Boolean = true

    override fun setVolume(newVolume: Float) {
        currentVolume = newVolume / 100f
        playerThreadHandler.post { internalPlayer.volume = currentVolume }
    }

    override fun setStateListener(listener: PlayerWrapper.PlayListener?) {
        stateListener = listener
    }

    private fun resumeWhenNetworkConnected() {
        if (!isPlayingFlag) return
        if (Utils.hasAnyConnection(context)) {
            playerThreadHandler.post {
                audioSource?.let { internalPlayer.setMediaSource(it, false) }
                internalPlayer.prepare()
                internalPlayer.playWhenReady = true
            }
        } else {
            stateListener?.onPlayerWarning(R.string.warning_no_network_trying_resume)
            @Suppress("DEPRECATION")
            context.registerReceiver(networkChangedReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }
    }

    // TransferListener implementation
    override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        this.bytesTransferred += bytesTransferred.toLong()
    }
    override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

    private inner class CustomLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
        private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            // Fail fast on network errors during opening (initial connect)
            val exception = loadErrorInfo.exception
            if (exception is HttpDataSource.HttpDataSourceException && 
                exception.type == HttpDataSource.HttpDataSourceException.TYPE_OPEN) {
                return C.TIME_UNSET 
            }
            
            val delay = try { sharedPrefs.getString("settings_retry_delay", "2")?.toInt() ?: 2 } catch (_: Exception) { 2 }
            return (delay * 1000).toLong()
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 3
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
             when (playbackState) {
                Player.STATE_READY -> {
                    if (internalPlayer.playWhenReady) {
                        isPlayingFlag = true
                        stateListener?.onStateChanged(PlayState.Playing)
                    } else {
                        stateListener?.onStateChanged(PlayState.Paused)
                    }
                }
                Player.STATE_BUFFERING -> stateListener?.onStateChanged(PlayState.PrePlaying)
                Player.STATE_IDLE -> {
                    isPlayingFlag = false
                    stateListener?.onStateChanged(PlayState.Idle)
                }
                Player.STATE_ENDED -> stop()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("ExoPlayerWrapper", "onPlayerError: ${error.errorCodeName} (${error.errorCode})", error)
            
            val messageId = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> R.string.error_station_load
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.error_stream_reconnect_timeout
                else -> R.string.error_play_stream
            }
            stateListener?.onPlayerError(messageId)
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is IcyInfo) {
                    val liveInfo = StreamLiveInfo(null)
                    liveInfo.addMetadata("StreamTitle", entry.title)
                    stateListener?.onDataSourceStreamLiveInfo(liveInfo)
                } else if (entry is IcyHeaders) {
                    val shoutcastInfo = ShoutcastInfo()
                    shoutcastInfo.audioName = entry.name
                    shoutcastInfo.bitrate = entry.bitrate / 1000
                    stateListener?.onDataSourceShoutcastInfo(shoutcastInfo, isHls)
                }
            }
        }
    }
}
