package com.ounben.amaradio.players.exoplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
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
    private var forwardingPlayer: Player
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
    
    private var playbackStartTime: Long = 0
    private var stationName: String? = null

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
                if (fullStopTask != null && audioSource != null && Utils.hasAnyConnection(context)) {
                    Log.i("ExoPlayerWrapper", "Regained connection. Resuming playback.")
                    cancelStopTask()
                    playerThreadHandler.post {
                        internalPlayer.setMediaSource(audioSource!!, false)
                        internalPlayer.prepare()
                        internalPlayer.playWhenReady = true
                    }
                }
            }
        }
    }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5000,  // Min buffer: 5s
                15000, // Max buffer: 15s
                1000,  // Buffer for playback: 1s (Fix for AA USB latency)
                2000   // Buffer for playback after rebuffer: 2s
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 1. Wurzel-Datenquelle erzwingen: IcyDataSource ist die Basis für ALLES
        val app = context.applicationContext as AMARadioApp
        val icyFactory = RadioDataSourceFactory(app.httpClient, this, this, false)
        
        // 2. Weiche einketten: Setzt Header, fordert Metadaten an
        val resolvingFactory = ResolvingDataSource.Factory(icyFactory, IcyMetadataResolver())

        // 3. Strikte Factory-Verdrahtung: Kein Fallback auf Standard-HTTP erlaubt
        val strictMediaSourceFactory = DefaultMediaSourceFactory(context, Media3Utils.getRadioExtractorsFactory())
            .setDataSourceFactory(resolvingFactory)

        internalPlayer = ExoPlayer.Builder(context)
            .setLooper(looper)
            .setAudioAttributes(audioAttributes, false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(strictMediaSourceFactory)
            .build()
        
        // 4. Position-Fix: Fortlaufende Zeit für Live-Streams (verhindert Bluetooth/AA Timeouts)
        forwardingPlayer = object : ForwardingPlayer(internalPlayer) {
            override fun getCurrentPosition(): Long {
                return if (playbackStartTime > 0 && isPlaying) {
                    SystemClock.elapsedRealtime() - playbackStartTime
                } else {
                    super.getCurrentPosition()
                }
            }
        }
        
        internalPlayer.addListener(PlayerEventListener())
    }

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, metadata: MediaMetadata?) {
        this.streamUrl = streamUrl
        this.stationName = metadata?.station?.toString() ?: metadata?.title?.toString()
        isHls = Utils.urlIndicatesHlsStream(streamUrl)
        bytesTransferred = 0
        cancelStopTask()

        if (bandwidthMeter == null) {
            bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        }

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val connectTimeout = try { sharedPref.getInt("stream_connect_timeout", 10).toLong() } catch (_: Exception) { 10L }
        val readTimeout = try { sharedPref.getInt("stream_read_timeout", 15).toLong() } catch (_: Exception) { 15L }

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

        // Auch hier: Strikte Kette für manuelle Aufrufe
        val baseFactory = RadioDataSourceFactory(dedicatedClient, this, this, isHls)
        val resolvingFactory = ResolvingDataSource.Factory(baseFactory, IcyMetadataResolver())

        val mediaItem = Media3Utils.buildLiveMediaItem(streamUrl.toUri(), metadata)

        val errorHandlingPolicy = CustomLoadErrorHandlingPolicy()
        audioSource = if (isHls) {
            HlsMediaSource.Factory(resolvingFactory)
                .setLoadErrorHandlingPolicy(errorHandlingPolicy)
                .createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(resolvingFactory, Media3Utils.getRadioExtractorsFactory())
                .setLoadErrorHandlingPolicy(errorHandlingPolicy)
                .createMediaSource(mediaItem)
        }

        playerThreadHandler.post {
            playbackStartTime = SystemClock.elapsedRealtime()
            // Strikter Abbruch vor Neu-Vorbereitung
            internalPlayer.stop()
            internalPlayer.clearMediaItems()
            
            internalPlayer.volume = currentVolume
            internalPlayer.setMediaSource(audioSource!!, true)
            // Priority 3: Set playWhenReady BEFORE prepare for live streams
            internalPlayer.playWhenReady = true
            internalPlayer.prepare()
        }
        
        @Suppress("DEPRECATION")
        try { context.unregisterReceiver(networkChangedReceiver) } catch (_: Exception) {}
        try { context.registerReceiver(networkChangedReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)) } catch (_: Exception) {}
    }

    override fun pause() {
        cancelStopTask()
        playerThreadHandler.post {
            try { context.unregisterReceiver(networkChangedReceiver) } catch (_: Exception) {}
            // Radio-Pause: Hard stop the engine and clear the media items 
            // to release network resources and flush the buffer completely.
            internalPlayer.playWhenReady = false
            internalPlayer.stop()
            internalPlayer.clearMediaItems()
            isPlayingFlag = false
        }
    }

    override fun stop() {
        cancelStopTask()
        isPlayingFlag = false
        playerThreadHandler.post {
            try { context.unregisterReceiver(networkChangedReceiver) } catch (_: Exception) {}
            // Strikter Abbruch und Playlist-Löschung
            internalPlayer.stop()
            internalPlayer.clearMediaItems()
            playbackStartTime = 0
        }
    }

    override fun release() {
        playerThreadHandler.post {
            internalPlayer.release()
        }
    }

    override fun isPlaying(): Boolean = isPlayingFlag

    override val player: Player
        get() = forwardingPlayer

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
        shoutcastInfo?.let { info ->
            if (!info.audioName.isNullOrEmpty()) stationName = info.audioName
            stateListener?.onDataSourceShoutcastInfo(info, isHls)
        }
    }

    override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {
        playerThreadHandler.post {
            // Metadaten Sanitizing für MediaSession
            val rawTitle = streamLiveInfo.title
            val parts = rawTitle.split(" - ", limit = 2)
            
            val (displayTitle, displayArtist) = if (parts.size == 2) {
                Pair(parts[1].trim(), parts[0].trim())
            } else {
                Pair(rawTitle.ifEmpty { "Live Stream" }, stationName ?: "AMARadio")
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(displayTitle)
                .setArtist(displayArtist)
                .setStation(stationName)
                .build()
            
            // Sofortiges Update triggert MediaSession Refresh
            internalPlayer.playlistMetadata = metadata
        }
        
        stateListener?.onDataSourceStreamLiveInfo(streamLiveInfo)
    }

    override fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int) {}

    private fun resumeWhenNetworkConnected() {
        playerThreadHandler.post {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val resumeWithin = try { sharedPrefs.getInt("settings_resume_within", 60) } catch (_: Exception) { 60 }
            if (resumeWithin > 0) {
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

    override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        this.bytesTransferred += bytesTransferred.toLong()
    }
    override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

    private inner class CustomLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
        private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        private fun getSanitizedRetryDelaySettingsMs(): Int = try { sharedPrefs.getInt("settings_retry_delay", 100).coerceAtLeast(10) } catch (_: Exception) { 100 }

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
            if (playbackState == Player.STATE_READY && internalPlayer.playWhenReady && playbackStartTime == 0L) {
                playbackStartTime = SystemClock.elapsedRealtime()
            }
            when (playbackState) {
                Player.STATE_READY -> {
                    cancelStopTask()
                    stateListener?.onStateChanged(if (internalPlayer.playWhenReady) PlayState.Playing else PlayState.Paused)
                }
                Player.STATE_BUFFERING -> stateListener?.onStateChanged(PlayState.PrePlaying)
                Player.STATE_IDLE -> stateListener?.onStateChanged(PlayState.Idle)
                Player.STATE_ENDED -> stop()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // CRITICAL: Notify state change immediately when play/pause is toggled
            stateListener?.onStateChanged(if (playWhenReady) PlayState.Playing else PlayState.Paused)
        }

        override fun onPlayerError(error: PlaybackException) {
            val messageId = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> R.string.error_station_load
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.error_stream_reconnect_timeout
                else -> R.string.error_play_stream
            }
            if (fullStopTask != null) stop()
            stateListener?.onPlayerError(messageId)
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is IcyInfo) {
                    val liveInfo = StreamLiveInfo(null)
                    liveInfo.addMetadata("StreamTitle", entry.title)
                    onDataSourceStreamLiveInfo(liveInfo)
                } else if (entry is IcyHeaders) {
                    val shoutcastInfo = ShoutcastInfo()
                    shoutcastInfo.audioName = entry.name
                    shoutcastInfo.bitrate = entry.bitrate / 1000
                    onDataSourceShoutcastInfo(shoutcastInfo)
                }
            }
        }
    }
}

@UnstableApi
private class IcyMetadataResolver : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val newHeaders = dataSpec.httpRequestHeaders.toMutableMap()
        newHeaders["Icy-MetaData"] = "1"
        return dataSpec.buildUpon()
            .setHttpRequestHeaders(newHeaders)
            .build()
    }
}
