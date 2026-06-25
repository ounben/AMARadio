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
import androidx.media3.datasource.HttpDataSource
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

@UnstableApi
class ExoPlayerWrapper(private val context: Context, looper: Looper) : PlayerWrapper, IcyDataSource.IcyDataSourceListener {
    private var internalPlayer: ExoPlayer
    private var stateListener: PlayerWrapper.PlayListener? = null
    private var streamUrl: String? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    override var currentPlaybackTransferredBytes: Long = 0
        private set
    private var isHls = false
    private var isPlayingFlag = false
    private val playerThreadHandler = Handler(looper)
    private var audioSource: MediaSource? = null
    private var fullStopTask: Runnable? = null
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
                5_000, // Buffer for playback after rebuffer 5s
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
            .setAudioAttributes(audioAttributes, true) // handles audio focus
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        
        internalPlayer.addListener(PlayerEventListener())
    }

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context) {
        this.streamUrl = streamUrl
        isHls = Utils.urlIndicatesHlsStream(streamUrl)
        if (bandwidthMeter == null) {
            bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        }
        val dataSourceFactory = RadioDataSourceFactory(httpClient, bandwidthMeter!!, this, 20, 2)
        audioSource = if (isHls) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(streamUrl.toUri()))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(CustomLoadErrorHandlingPolicy(context))
                .createMediaSource(MediaItem.fromUri(streamUrl.toUri()))
        }
        playerThreadHandler.post {
            cancelStopTask()
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            internalPlayer.setAudioAttributes(audioAttributes, false)
            internalPlayer.volume = currentVolume
            internalPlayer.setMediaSource(audioSource!!)
            internalPlayer.prepare()
            internalPlayer.playWhenReady = true
            Log.d("ExoPlayerWrapper", "Player starting. Volume: ${internalPlayer.volume}, AudioAttributes usage: ${audioAttributes.usage}")
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
        get() {
            val id = internalPlayer.audioSessionId
            Log.d("ExoPlayerWrapper", "get audioSessionId: $id")
            return id
        }

    override val isLocal: Boolean = true

    override fun setVolume(newVolume: Float) {
        currentVolume = newVolume / 100f
        playerThreadHandler.post { internalPlayer.volume = currentVolume }
    }

    override fun setStateListener(listener: PlayerWrapper.PlayListener?) {
        stateListener = listener
    }

    override fun onDataSourceConnected() {
        currentPlaybackTransferredBytes = 0
    }

    override fun onDataSourceConnectionLost() {
        Log.w("ExoPlayerWrapper", "Data source connection lost, attempting silent recovery...")
        resumeWhenNetworkConnected()
    }

    override fun onDataSourceConnectionLostIrrecoverably() {
        stateListener?.onPlayerError(R.string.error_stream_url)
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

    override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?) {
        shoutcastInfo?.let {
            stateListener?.onDataSourceShoutcastInfo(it, isHls)
        }
    }

    override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {
        stateListener?.onDataSourceStreamLiveInfo(streamLiveInfo)
    }

    override fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int) {
        currentPlaybackTransferredBytes += length.toLong()
    }

    private fun cancelStopTask() {
        fullStopTask?.let {
            playerThreadHandler.removeCallbacks(it)
            fullStopTask = null
        }
    }

    private class CustomLoadErrorHandlingPolicy(context: Context) : DefaultLoadErrorHandlingPolicy() {
        private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            if (loadErrorInfo.exception is HttpDataSource.InvalidResponseCodeException) {
                val responseCode = (loadErrorInfo.exception as HttpDataSource.InvalidResponseCodeException).responseCode
                if (responseCode in (400..499)) return C.TIME_UNSET
            }
            val timeout = try { sharedPrefs.getString("settings_retry_timeout", "20")?.toInt() ?: 20 } catch (_: Exception) { 20 }
            if (loadErrorInfo.errorCount > timeout) return C.TIME_UNSET
            val delay = try { sharedPrefs.getString("settings_retry_delay", "2")?.toInt() ?: 2 } catch (_: Exception) { 2 }
            return (delay * 1000).toLong()
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
             when (playbackState) {
                Player.STATE_READY -> {
                    if (internalPlayer.playWhenReady) {
                        isPlayingFlag = true
                        stateListener?.onStateChanged(PlayState.Playing)
                        if (Utils.isDebug) Log.d("ExoPlayerWrapper", "Playback started, volume: ${internalPlayer.volume}")
                    } else {
                        stateListener?.onStateChanged(PlayState.Paused)
                    }
                }
                Player.STATE_BUFFERING -> stateListener?.onStateChanged(PlayState.PrePlaying)
                Player.STATE_IDLE -> {
                    // Do not reset resources on IDLE to prevent hardware re-init
                    isPlayingFlag = false
                    stateListener?.onStateChanged(PlayState.Idle)
                }
                Player.STATE_ENDED -> {
                    // Avoid full stop on live streams, wait for data instead
                    if (isHls || (streamUrl != null)) {
                        Log.d("ExoPlayerWrapper", "STATE_ENDED reached on stream, keeping resources warm.")
                        internalPlayer.prepare() // Keep resources warm and stay in buffering
                    } else {
                        stop()
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stateListener?.onPlayerError(R.string.error_play_stream)
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
