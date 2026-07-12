package com.ounben.amaradio.players

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.RadioBrowserServerManager
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.exoplayer.ExoPlayerWrapper
import com.ounben.amaradio.players.exoplayer.Media3Utils
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioPlayer(private val mainContext: Context) : PlayerWrapper.PlayListener {
    private val tag = "RadioPlayer"

    interface PlayerListener {
        fun onStateChanged(status: PlayState, audioSessionId: Int)
        fun onPlayerWarning(messageId: Int)
        fun onPlayerError(messageId: Int)
        fun onBufferedTimeUpdate(bufferedMs: Long)
        fun foundShoutcastStream(bitrate: ShoutcastInfo?, isHls: Boolean)
        fun foundLiveStreamInfo(liveInfo: StreamLiveInfo)
        fun onPlayerCreated(player: androidx.media3.common.Player)
    }

    private var currentPlayer: PlayerWrapper
    private var currentTargetMediaItem: androidx.media3.common.MediaItem? = null
    
    @Volatile
    private var currentStationUuid: String? = null
    
    private var streamName: String? = null
    private val playerThreadHandler: Handler
    private var playerListener: PlayerListener? = null
    
    private var pendingPlayRunnable: Runnable? = null
    
    @Volatile
    private var cachedAudioSessionId: Int = 0

    @Volatile
    var playState = PlayState.Idle
        private set
    
    private var lastStationURL: String? = null
    private var lastStreamName: String? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3

    /**
     * Priority 2: Prepare new stream WITHOUT releasing the player instance.
     * Keeps MediaSession and Android Auto connection stable.
     */
    private fun prepareNewStream() {
        if (Utils.isDebug) Log.d(tag, "Preparing fresh stream state (instance remains)")
        currentPlayer.stop()
    }

    private var lastLiveInfo: StreamLiveInfo? = null
    private var playStationTask: PlayStationTask? = null
    private var stationLoadAttempts = 0
    private var currentStation: DataRadioStation? = null

    private val bufferCheckRunnable = object : Runnable {
        override fun run() {
            val bufferTimeMs = currentPlayer.bufferedMs
            playerListener?.onBufferedTimeUpdate(bufferTimeMs)
            if (Utils.isDebug) Log.d(tag, "buffered $bufferTimeMs ms.")
            playerThreadHandler.postDelayed(this, 2000)
        }
    }

    init {
        val app = mainContext.applicationContext as AMARadioApp
        playerThreadHandler = Handler(app.audioLooper)
        currentPlayer = ExoPlayerWrapper(mainContext, app.audioLooper)
        currentPlayer.setStateListener(this)
    }

    // Manual URL play (direct calls)
    fun play(stationURL: String?, streamName: String?, metadata: androidx.media3.common.MediaMetadata? = null) {
        playerThreadHandler.post {
            // Use URL as temporary fallback UUID for manual plays
            playInternal(stationURL, streamName, false, metadata, stationURL)
        }
    }

    private fun playInternal(stationURL: String?, streamName: String?, isReconnect: Boolean, metadata: androidx.media3.common.MediaMetadata? = null, stationUuid: String? = null) {
        if (stationURL == null) return
        
        // This is always running on the AudioThread handler
        if (!isReconnect) {
            reconnectAttempts = 0
            lastStationURL = stationURL
            lastStreamName = streamName
            
            // Track target MediaItem for stable reconnects
            currentTargetMediaItem = Media3Utils.buildLiveMediaItem(stationURL.toUri(), metadata, stationUuid)

            // 1. Storniere alte hängende Play-Tasks
            pendingPlayRunnable?.let { playerThreadHandler.removeCallbacks(it) }

            // 2. Harter Abbruch & Reset
            prepareNewStream()
            
            // 3. Künstliche Atempause (180ms) via Handler (FIFO) für die Hardware
            val playTask = Runnable {
                // Secondary Guard: Verify that we are still supposed to play this station/URL
                val activeTarget = currentTargetMediaItem?.localConfiguration?.uri.toString()
                if (activeTarget == stationURL && (stationUuid == null || stationUuid == currentStationUuid)) {
                    executeActualPlayRemote(stationURL, streamName, metadata)
                } else {
                    if (Utils.isDebug) Log.d(tag, "Guard: Station changed during delay, canceling playInternal for $stationURL")
                }
            }
            pendingPlayRunnable = playTask
            playerThreadHandler.postDelayed(playTask, 180)
        } else {
            executeActualPlayRemote(stationURL, streamName, metadata)
        }
    }

    private fun executeActualPlayRemote(stationURL: String, streamName: String?, metadata: androidx.media3.common.MediaMetadata?) {
        setState(PlayState.PrePlaying, -1)
        this.streamName = streamName
        val app = mainContext.applicationContext as AMARadioApp
        val customizedHttpClient = app.newHttpClient().build()
        currentPlayer.playRemote(customizedHttpClient, stationURL, mainContext, metadata)
    }

    // Primary entry point for station objects (UI/Browser)
    fun play(station: DataRadioStation, metadata: androidx.media3.common.MediaMetadata? = null) {
        val uuid = station.StationUuid
        
        // Revised Guard: Only block if it's the SAME station AND it's already playing.
        // This allows re-triggering (restarting) if it's stuck in Buffering (PrePlaying) or Error.
        if (uuid == currentStationUuid && playState == PlayState.Playing) {
            if (Utils.isDebug) Log.d(tag, "Guard: Station $uuid is already playing, ignoring play call.")
            return
        }

        // Set UUID immediately on the calling thread (Main/UI) to block redundant events.
        currentStationUuid = uuid

        playerThreadHandler.post {
            // Interner Stop des Wrappers (ExoPlayer) zur Bereinigung der Pipeline.
            // Die logische UUID bleibt erhalten, damit PlayStationTask die URL zuweisen kann.
            currentPlayer.stop()
            
            reconnectAttempts = 0
            stationLoadAttempts = 0
            currentStation = station
            executePlayStationTask(station, metadata)
        }
    }

    private fun executePlayStationTask(station: DataRadioStation, metadata: androidx.media3.common.MediaMetadata? = null) {
        // Run retrieval on IO/Main
        CoroutineScope(Dispatchers.Main).launch {
            setState(PlayState.PrePlaying, -1)
            val task = PlayStationTask(station, mainContext,
                { url -> 
                    // Post back to AudioThread
                    playerThreadHandler.post {
                        // Guard: Check if the user changed the station while resolving the link
                        if (currentStationUuid == station.StationUuid) {
                            this@RadioPlayer.playInternal(url, station.Name, false, metadata, station.StationUuid) 
                        } else {
                            if (Utils.isDebug) Log.d(tag, "Guard: Target station changed during link resolution.")
                        }
                    }
                },
                { executionResult ->
                    if (executionResult == PlayStationTask.ExecutionResult.FAILURE) {
                        stationLoadAttempts++
                        if (stationLoadAttempts < 3) {
                            Log.w(tag, "Station load failed, retrying (attempt $stationLoadAttempts)")
                            CoroutineScope(Dispatchers.Main).launch {
                                RadioBrowserServerManager.rotateServer()
                                executePlayStationTask(station, metadata)
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
            playStationTask = task
            task.execute()
        }
    }

    private fun cancelStationLinkRetrieval() {
        stationLoadAttempts = 0
        playStationTask?.let {
            it.cancel()
            playStationTask = null
        }
    }

    fun pause() {
        playerThreadHandler.post {
            // For Radio, Pause is a full Stop to disconnect from network.
            currentStationUuid = null 
            pendingPlayRunnable?.let { playerThreadHandler.removeCallbacks(it) }
            cancelStationLinkRetrieval()
            if (playState == PlayState.Idle || playState == PlayState.Paused) return@post
            val audioSessionId = audioSessionId
            currentPlayer.stop()
            if (Utils.isDebug) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            setState(PlayState.Paused, audioSessionId)
        }
    }

    fun stop() {
        playerThreadHandler.post {
            currentStationUuid = null
            pendingPlayRunnable?.let { playerThreadHandler.removeCallbacks(it) }
            cancelStationLinkRetrieval()
            currentStation = null
            currentTargetMediaItem = null

            if (playState == PlayState.Idle) return@post

            val audioSessionId = audioSessionId
            setState(PlayState.Idle, audioSessionId)
            currentPlayer.stop()
            if (Utils.isDebug) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
        }
    }

    fun destroy() {
        playerThreadHandler.post {
            currentStationUuid = null
            pendingPlayRunnable?.let { playerThreadHandler.removeCallbacks(it) }
            cancelStationLinkRetrieval()
            currentStation = null
            currentTargetMediaItem = null
            
            val audioSessionId = audioSessionId
            setState(PlayState.Idle, audioSessionId)
            
            currentPlayer.setStateListener(null)
            currentPlayer.stop()
            currentPlayer.release()
        }
    }

    fun isPlaying(): Boolean = playState == PlayState.PrePlaying || playState == PlayState.Playing

    val player: androidx.media3.common.Player?
        get() = currentPlayer.player

    val playerLooper: Looper
        get() = playerThreadHandler.looper

    val audioSessionId: Int
        get() = cachedAudioSessionId

    fun setVolume(volume: Float) {
        playerThreadHandler.post {
            currentPlayer.setVolume(volume)
        }
    }

    fun runInPlayerThread(runnable: Runnable) {
        playerThreadHandler.post(runnable)
    }

    fun setPlayerListener(listener: PlayerListener?) {
        playerListener = listener
        playerListener?.onPlayerCreated(currentPlayer.player!!)
    }

    private fun setState(state: PlayState, audioSessionId: Int) {
        if (Utils.isDebug) Log.d(tag, "set state '${state.name}'")
        if (playState == state) return
        
        if (state == PlayState.Playing) {
            reconnectAttempts = 0
            cachedAudioSessionId = currentPlayer.audioSessionId
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
            
            if (reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++
                Log.w(tag, "Player error. Reconnecting ($reconnectAttempts/$maxReconnectAttempts) in 2s...")
                
                val recoveryUuid = currentStationUuid
                playerThreadHandler.postDelayed({
                    // Only reconnect if the user hasn't switched to another station in the meantime
                    if (currentStationUuid == recoveryUuid && recoveryUuid != null) {
                        prepareNewStream()
                        
                        val targetItem = currentTargetMediaItem
                        if (targetItem != null) {
                            Log.i(tag, "Reconnecting using target MediaItem: ${targetItem.mediaMetadata.title}")
                            val app = mainContext.applicationContext as AMARadioApp
                            val customizedHttpClient = app.newHttpClient().build()
                            currentPlayer.playRemote(
                                customizedHttpClient, 
                                targetItem.localConfiguration?.uri.toString(), 
                                mainContext, 
                                targetItem.mediaMetadata
                            )
                        } else if (currentStation != null) {
                            CoroutineScope(Dispatchers.Main).launch {
                                RadioBrowserServerManager.rotateServer()
                                executePlayStationTask(currentStation!!)
                            }
                        } else if (lastStationURL != null) {
                            playInternal(lastStationURL, lastStreamName, true)
                        }
                    }
                }, 2000)
                
                setState(PlayState.PrePlaying, audioSessionId)
            } else {
                Log.e(tag, "Max reconnect attempts reached.")
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

    override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {
        lastLiveInfo = streamLiveInfo
        playerListener?.foundLiveStreamInfo(streamLiveInfo)
    }
}
