package com.ounben.amaradio.players

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.RadioBrowserServerManager
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.exoplayer.ExoPlayerWrapper
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

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
    private var stationLoadAttempts = 0

    private val bufferCheckRunnable = object : Runnable {
        override fun run() {
            val bufferTimeMs = currentPlayer.bufferedMs
            playerListener?.onBufferedTimeUpdate(bufferTimeMs)
            if (Utils.isDebug) Log.d(TAG, "buffered $bufferTimeMs ms.")
            playerThreadHandler.postDelayed(this, 2000)
        }
    }

    init {
        val app = mainContext.applicationContext as AMARadioApp
        playerThreadHandler = Handler(app.audioLooper)
        currentPlayer = ExoPlayerWrapper(mainContext, app.audioLooper)
        currentPlayer.setStateListener(this)
    }

    fun play(stationURL: String?, streamName: String?) {
        if (stationURL == null) return
        setState(PlayState.PrePlaying, -1)
        this.streamName = streamName
        val prefs = PreferenceManager.getDefaultSharedPreferences(mainContext.applicationContext)
        val connectTimeout = prefs.getInt("stream_connect_timeout", 4)
        val readTimeout = prefs.getInt("stream_read_timeout", 10)
        val AMARadioApp = mainContext.applicationContext as AMARadioApp
        val customizedHttpClient = AMARadioApp.newHttpClient()
            .connectTimeout(connectTimeout.seconds)
            .readTimeout(readTimeout.seconds)
            .build()
        playerThreadHandler.post { currentPlayer.playRemote(customizedHttpClient, stationURL, mainContext) }
    }

    fun play(station: DataRadioStation) {
        stationLoadAttempts = 0
        executePlayStationTask(station)
    }

    private fun executePlayStationTask(station: DataRadioStation) {
        setState(PlayState.PrePlaying, -1)
        playStationTask = PlayStationTask(station, mainContext,
            { url -> this@RadioPlayer.play(url, station.Name) },
            { executionResult ->
                if (executionResult == PlayStationTask.ExecutionResult.FAILURE) {
                    stationLoadAttempts++
                    if (stationLoadAttempts < 3) {
                        Log.w(TAG, "Station load failed, retrying with server rotation (attempt $stationLoadAttempts)")
                        playerThreadHandler.post {
                            CoroutineScope(Dispatchers.Main).launch {
                                RadioBrowserServerManager.rotateServer()
                                executePlayStationTask(station)
                            }
                        }
                    } else {
                        playStationTask = null
                        this@RadioPlayer.onPlayerError(R.string.error_station_load)
                    }
                } else {
                    playStationTask = null
                    stationLoadAttempts = 0
                }
            })
        playStationTask!!.execute()
    }

    private fun cancelStationLinkRetrieval() {
        stationLoadAttempts = 0
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
            if (Utils.isDebug) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            setState(PlayState.Paused, audioSessionId)
        }
    }

    fun stop() {
        if (playState == PlayState.Idle) return
        cancelStationLinkRetrieval()
        playerThreadHandler.post {
            val audioSessionId = audioSessionId
            currentPlayer.stop()
            if (Utils.isDebug) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            setState(PlayState.Idle, audioSessionId)
        }
    }

    fun destroy() {
        stop()
    }

    fun isPlaying(): Boolean = playState == PlayState.PrePlaying || playState == PlayState.Playing

    val player: androidx.media3.common.Player?
        get() = currentPlayer.player

    val playerLooper: Looper
        get() = playerThreadHandler.looper

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
        if (Utils.isDebug) Log.d(TAG, "set state '${state.name}'")
        if (playState == state) return
        if (Utils.isDebug) {
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
