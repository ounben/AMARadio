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

    private var currentPlayer: PlayerWrapper
    private var streamName: String? = null
    private val playerThreadHandler: Handler
    private var playerListener: PlayerListener? = null
    var playState = PlayState.Idle
        private set
    
    private var lastStationURL: String? = null
    private var lastStreamName: String? = null
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 3

    private fun reinit() {
        if (Utils.isDebug) Log.d(TAG, "Re-initializing Player to ensure fresh native state")
        // Führe release() aus, um native Ressourcen (Codecs, Threads) sauber abzubauen
        currentPlayer.release()
        
        // Wir bauen den Player-Wrapper neu auf
        val app = mainContext.applicationContext as AMARadioApp
        val newPlayer = ExoPlayerWrapper(mainContext, app.audioLooper)
        newPlayer.setStateListener(this)
        currentPlayer = newPlayer
    }

    private var lastLiveInfo: StreamLiveInfo? = null
    private var playStationTask: PlayStationTask? = null
    private var stationLoadAttempts = 0
    private var currentStation: DataRadioStation? = null

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
        playInternal(stationURL, streamName, false)
    }

    private fun playInternal(stationURL: String?, streamName: String?, isReconnect: Boolean) {
        if (stationURL == null) return
        
        if (!isReconnect) {
            reconnectAttempts = 0
            lastStationURL = stationURL
            lastStreamName = streamName
            
            // Erzwinge bei manuellem Wechsel immer ein reinit(). 
            // Das stellt sicher, dass hängende Netzwerk-Threads des vorherigen
            // (vielleicht noch puffernden) Senders hart via release() beendet werden.
            reinit()
        }

        setState(PlayState.PrePlaying, -1)
        this.streamName = streamName
        val prefs = PreferenceManager.getDefaultSharedPreferences(mainContext.applicationContext)
        val connectTimeout = prefs.getInt("stream_connect_timeout", 4)
        val readTimeout = prefs.getInt("stream_read_timeout", 10)
        val AMARadioApp = mainContext.applicationContext as AMARadioApp
        val customizedHttpClient = AMARadioApp.httpClient.newBuilder()
            .connectTimeout(connectTimeout.seconds)
            .readTimeout(readTimeout.seconds)
            .build()
        playerThreadHandler.post { currentPlayer.playRemote(customizedHttpClient, stationURL, mainContext) }
    }

    fun play(station: DataRadioStation) {
        // Breche alle laufenden Aktivitäten (Tasks und aktuelles Playback) sofort ab,
        // um den hängenden Puffer-Vorgang des vorherigen Senders zu beenden.
        stop()

        reconnectAttempts = 0
        stationLoadAttempts = 0
        currentStation = station
        executePlayStationTask(station)
    }

    private fun executePlayStationTask(station: DataRadioStation) {
        // Zeige sofort im UI an, dass wir zum neuen Sender wechseln (PrePlaying)
        setState(PlayState.PrePlaying, -1)
        playStationTask = PlayStationTask(station, mainContext,
            { url -> this@RadioPlayer.playInternal(url, station.Name, false) },
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
            it.cancel()
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
        currentPlayer.release()
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
        
        if (state == PlayState.Playing) {
            reconnectAttempts = 0 // Reset on successful play
        }

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
        playerThreadHandler.post { 
            currentPlayer.stop()
            if (Utils.isDebug) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++
                Log.w(TAG, "Player error encountered. Attempting automatic reconnect ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS) in 2s...")
                
                playerThreadHandler.postDelayed({
                    reinit()
                    
                    // Falls wir eine Station-Task hatten, rotieren wir sicherheitshalber den Server
                    if (currentStation != null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            RadioBrowserServerManager.rotateServer()
                            executePlayStationTask(currentStation!!)
                        }
                    } else if (lastStationURL != null) {
                        playInternal(lastStationURL, lastStreamName, true)
                    }
                }, 2000)
                
                // Wir senden noch keinen finalen Fehler an die UI, da wir es erneut versuchen
                setState(PlayState.PrePlaying, audioSessionId)
            } else {
                Log.e(TAG, "Max reconnect attempts reached. Notifying UI.")
                reconnectAttempts = 0
                playState = PlayState.Error
                playerListener?.onStateChanged(PlayState.Error, audioSessionId)
                playerListener?.onPlayerError(messageId) 
            }
        }
    }

    override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo, isHls: Boolean) {
        playerListener?.foundShoutcastStream(shoutcastInfo, isHls)
    }

    override fun onDataSourceStreamLiveInfo(liveInfo: StreamLiveInfo) {
        lastLiveInfo = liveInfo
        playerListener?.foundLiveStreamInfo(liveInfo)
    }
}
