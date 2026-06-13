package net.ounben.AMARadio.players

import android.content.Context
import android.os.*
import android.util.Log
import androidx.preference.PreferenceManager
import net.ounben.AMARadio.BuildConfig
import net.ounben.AMARadio.R
import net.ounben.AMARadio.AMARadioApp
import net.ounben.AMARadio.Utils
import net.ounben.AMARadio.players.exoplayer.ExoPlayerWrapper
import net.ounben.AMARadio.station.DataRadioStation
import net.ounben.AMARadio.station.live.ShoutcastInfo
import net.ounben.AMARadio.station.live.StreamLiveInfo
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioPlayer(private val mainContext: Context) : PlayerWrapper.PlayListener {
    private val TAG = "RadioPlayer"

    interface PlayerListener {
        fun onStateChanged(status: PlayState, audioSessionId: Int)
        fun onPlayerWarning(messageId: Int)
        fun onPlayerError(messageId: Int)
        fun onBufferedTimeUpdate(bufferedMs: Long)
        fun foundShoutcastStream(bitrate: ShoutcastInfo?, isHls: Boolean)
        fun foundLiveStreamInfo(liveInfo: StreamLiveInfo)
    }

    private val currentPlayer: PlayerWrapper
    private var streamName: String? = null
    private val playerThreadHandler: Handler
    private var playerListener: PlayerListener? = null
    var playState = PlayState.Idle
        private set
    private var lastLiveInfo: StreamLiveInfo? = null
    private var playStationTask: PlayStationTask? = null

    private val bufferCheckRunnable = object : Runnable {
        override fun run() {
            val bufferTimeMs = currentPlayer.bufferedMs
            playerListener?.onBufferedTimeUpdate(bufferTimeMs)
            if (BuildConfig.DEBUG) Log.d(TAG, "buffered $bufferTimeMs ms.")
            playerThreadHandler.postDelayed(this, 2000)
        }
    }

    init {
        playerThreadHandler = Handler(Looper.getMainLooper())
        currentPlayer = ExoPlayerWrapper()
        currentPlayer.setStateListener(this)
    }

    fun play(stationURL: String?, streamName: String?, isAlarm: Boolean) {
        if (stationURL == null) return
        setState(PlayState.PrePlaying, -1)
        this.streamName = streamName
        val prefs = PreferenceManager.getDefaultSharedPreferences(mainContext.applicationContext)
        val connectTimeout = prefs.getInt("stream_connect_timeout", 4)
        val readTimeout = prefs.getInt("stream_read_timeout", 10)
        val AMARadioApp = mainContext.applicationContext as AMARadioApp
        val customizedHttpClient = AMARadioApp.newHttpClient()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.SECONDS)
            .build()
        playerThreadHandler.post { currentPlayer.playRemote(customizedHttpClient, stationURL, mainContext, isAlarm) }
    }

    fun play(station: DataRadioStation, isAlarm: Boolean) {
        setState(PlayState.PrePlaying, -1)
        playStationTask = PlayStationTask(station, mainContext,
            { url -> this@RadioPlayer.play(url, station.Name, isAlarm) },
            { executionResult ->
                playStationTask = null
                if (executionResult == PlayStationTask.ExecutionResult.FAILURE) {
                    this@RadioPlayer.onPlayerError(R.string.error_station_load)
                }
            })
        playStationTask!!.execute()
    }

    private fun cancelStationLinkRetrieval() {
        playStationTask?.let {
            it.cancel(true)
            playStationTask = null
        }
    }

    fun pause() {
        cancelStationLinkRetrieval()
        playerThreadHandler.post {
            if (playState == PlayState.Idle || playState == PlayState.Paused) return@post
            val audioSessionId = audioSessionId
            currentPlayer.pause()
            if (BuildConfig.DEBUG) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            setState(PlayState.Paused, audioSessionId)
        }
    }

    fun stop() {
        if (playState == PlayState.Idle) return
        cancelStationLinkRetrieval()
        playerThreadHandler.post {
            val audioSessionId = audioSessionId
            currentPlayer.stop()
            if (BuildConfig.DEBUG) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            setState(PlayState.Idle, audioSessionId)
        }
    }

    fun destroy() {
        stop()
    }

    fun isPlaying(): Boolean = playState == PlayState.PrePlaying || playState == PlayState.Playing

    val audioSessionId: Int
        get() = currentPlayer.audioSessionId

    fun setVolume(volume: Float) {
        currentPlayer.setVolume(volume)
    }

    fun runInPlayerThread(runnable: Runnable) {
        playerThreadHandler.post(runnable)
    }

    fun setPlayerListener(listener: PlayerListener?) {
        playerListener = listener
    }

    private fun setState(state: PlayState, audioSessionId: Int) {
        if (BuildConfig.DEBUG) Log.d(TAG, "set state '${state.name}'")
        if (playState == state) return
        if (BuildConfig.DEBUG) {
            if (state == PlayState.Playing) {
                playerThreadHandler.removeCallbacks(bufferCheckRunnable)
                playerThreadHandler.post(bufferCheckRunnable)
            } else {
                playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            }
        }
        playState = state
        playerListener?.onStateChanged(state, audioSessionId)
    }

    val totalTransferredBytes: Long
        get() = currentPlayer.totalTransferredBytes

    val currentPlaybackTransferredBytes: Long
        get() = currentPlayer.currentPlaybackTransferredBytes

    val bufferedSeconds: Long
        get() = currentPlayer.bufferedMs / 1000

    val isLocal: Boolean
        get() = currentPlayer.isLocal

    override fun onStateChanged(state: PlayState) {
        setState(state, audioSessionId)
    }

    override fun onPlayerWarning(messageId: Int) {
        playerThreadHandler.post { playerListener?.onPlayerWarning(messageId) }
    }

    override fun onPlayerError(messageId: Int) {
        pause()
        playerThreadHandler.post { playerListener?.onPlayerError(messageId) }
    }

    override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo, isHls: Boolean) {
        playerListener?.foundShoutcastStream(shoutcastInfo, isHls)
    }

    override fun onDataSourceStreamLiveInfo(liveInfo: StreamLiveInfo) {
        lastLiveInfo = liveInfo
        playerListener?.foundLiveStreamInfo(liveInfo)
    }
}
