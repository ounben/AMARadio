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
class ExoPlayerWrapper(private val context: Context, looper: Looper) : PlayerWrapper, TransferListener, IcyDataSource.IcyDataSourceListener {
    private var internalPlayer: ExoPlayer
    private var stateListener: PlayerWrapper.PlayListener? = null
    private var streamUrl: String? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    
    private var bytesTransferred: Long = 0
    override val currentPlaybackTransferredBytes: Long
        get() = bytesTransferred
    
    private var isHls = false
    private var isPlayingFlag = false
    private val playerThreadHandler = Handler(looper)
    private var audioSource: MediaSource? = null
    private var currentVolume = 1.0f
    private var fullStopTask: Runnable? = null

    private fun cancelStopTask() {
        fullStopTask?.let {
            playerThreadHandler.removeCallbacks(it)
            fullStopTask = null
        }
    }

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
            .setAudioAttributes(audioAttributes, false) 
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        
        internalPlayer.addListener(PlayerEventListener())
    }

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, metadata: androidx.media3.common.MediaMetadata?) {
        this.streamUrl = streamUrl
        isHls = Utils.urlIndicatesHlsStream(streamUrl)
        bytesTransferred = 0
        cancelStopTask()

        if (bandwidthMeter == null) {
            bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        }

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val connectTimeout = try { sharedPref.getInt("stream_connect_timeout", 10).toLong() } catch (_: Exception) { 10L }
        val readTimeout = try { sharedPref.getInt("stream_read_timeout", 15).toLong() } catch (_: Exception) { 15L }

        // Dedicated OkHttpClient with requested timeouts and HEAD to GET interceptor
        val dedicatedClient = httpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.method == "HEAD") {
                    chain.proceed(request.newBuilder().method("GET", null).build())
                } else {
                    chain.proceed(request)
                }
            }
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .build()

        val dataSourceFactory = RadioDataSourceFactory(dedicatedClient, bandwidthMeter!!, this)

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl.toUri())
            .apply { if (metadata != null) setMediaMetadata(metadata) }
            .build()

        val errorHandlingPolicy = CustomLoadErrorHandlingPolicy()
        audioSource = if (isHls) {
            HlsMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(errorHandlingPolicy)
                .createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(errorHandlingPolicy)
                .createMediaSource(mediaItem)
        }

        playerThreadHandler.post {
            internalPlayer.stop()
            internalPlayer.volume = currentVolume
            internalPlayer.setMediaSource(audioSource!!, true)
            internalPlayer.prepare()
            internalPlayer.playWhenReady = true
            Log.d("ExoPlayerWrapper", "Player starting stream: $streamUrl")
        }
        @Suppress("DEPRECATION")
        try { context.unregisterReceiver(networkChangedReceiver) } catch (_: Exception) {}
        try { context.registerReceiver(networkChangedReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)) } catch (_: Exception) {}
    }

    override fun pause() {
        cancelStopTask()
        playerThreadHandler.post {
            try { context.unregisterReceiver(networkChangedReceiver) } catch (_: Exception) {}
            internalPlayer.playWhenReady = false
            isPlayingFlag = false
        }
    }

    override fun stop() {
        cancelStopTask()
        isPlayingFlag = false
        playerThreadHandler.post {
            try { context.unregisterReceiver(networkChangedReceiver) } catch (_: Exception) {}
            internalPlayer.stop()
        }
    }

    override fun release() {
        playerThreadHandler.post {
            internalPlayer.release()
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

    override fun onDataSourceConnected() {
        bytesTransferred = 0
    }

    override fun onDataSourceConnectionLost() {
        resumeWhenNetworkConnected()
    }

    override fun onDataSourceConnectionLostIrrecoverably() {
        stateListener?.onPlayerError(R.string.error_station_load)
    }

    override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?) {
        shoutcastInfo?.let { stateListener?.onDataSourceShoutcastInfo(it, isHls) }
    }

    override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {
        Log.d("ExoPlayerWrapper", "New Live Info: ${streamLiveInfo.title}")
        stateListener?.onDataSourceStreamLiveInfo(streamLiveInfo)
    }

    override fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int) {
        // Handled by onBytesTransferred
    }

    private fun resumeWhenNetworkConnected() {
        if (!isPlayingFlag) return
        if (Utils.hasAnyConnection(context)) {
            cancelStopTask()
            playerThreadHandler.post {
                audioSource?.let { internalPlayer.setMediaSource(it, false) }
                internalPlayer.prepare()
                internalPlayer.playWhenReady = true
            }
        } else {
            playerThreadHandler.post {
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
                val resumeWithin = try { sharedPrefs.getInt("settings_resume_within", 60) } catch (_: Exception) { 60 }
                if (resumeWithin > 0) {
                    Log.d("ExoPlayerWrapper", "Trying to resume playback within ${resumeWithin}s.")
                    cancelStopTask()
                    fullStopTask = Runnable {
                        stop()
                        stateListener?.onPlayerError(R.string.giving_up_resume)
                        fullStopTask = null
                    }
                    playerThreadHandler.postDelayed(fullStopTask!!, resumeWithin * 1000L)
                    stateListener?.onPlayerWarning(R.string.warning_no_network_trying_resume)
                } else {
                    stop()
                    stateListener?.onPlayerError(R.string.error_stream_reconnect_timeout)
                }
            }
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

        private fun getSanitizedRetryDelaySettingsMs(): Int {
            return try {
                sharedPrefs.getInt("settings_retry_delay", 100).coerceAtLeast(10)
            } catch (_: Exception) {
                100
            }
        }

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val exception = loadErrorInfo.exception

            if (exception is HttpDataSource.InvalidContentTypeException) {
                stateListener?.onPlayerError(R.string.error_play_stream)
                return C.TIME_UNSET
            }

            if (!Utils.hasAnyConnection(context)) {
                val resumeWithinS = try { sharedPrefs.getInt("settings_resume_within", 60) } catch (_: Exception) { 60 }
                if (resumeWithinS > 0) {
                    resumeWhenNetworkConnected()
                    return (resumeWithinS * 1000 + getSanitizedRetryDelaySettingsMs()).toLong()
                }
            }

            if (exception is HttpDataSource.HttpDataSourceException &&
                exception.type == HttpDataSource.HttpDataSourceException.TYPE_OPEN) {
                return 1000
            }

            return getSanitizedRetryDelaySettingsMs().toLong()
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int {
            val retryTimeout = try { sharedPrefs.getInt("settings_retry_timeout", 10) } catch (_: Exception) { 10 }
            return (retryTimeout * 1000 / getSanitizedRetryDelaySettingsMs()) + 1
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            isPlayingFlag = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
            when (playbackState) {
                Player.STATE_READY -> {
                    cancelStopTask()
                    if (internalPlayer.playWhenReady) {
                        stateListener?.onStateChanged(PlayState.Playing)
                    } else {
                        stateListener?.onStateChanged(PlayState.Paused)
                    }
                }
                Player.STATE_BUFFERING -> stateListener?.onStateChanged(PlayState.PrePlaying)
                Player.STATE_IDLE -> stateListener?.onStateChanged(PlayState.Idle)
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
            
            if (fullStopTask != null) {
                stop()
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
