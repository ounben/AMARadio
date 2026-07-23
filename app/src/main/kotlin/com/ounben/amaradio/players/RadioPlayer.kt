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
    
    @Volatile
    private var userWantPlaying = false

    @Volatile
    private var isPausing = false

    private var lastPlayingStateTime: Long = 0
    private val MIN_PLAYING_DURATION_BEFORE_REBUFFER_MS = 1500L

    private var lastStationURL: String? = null
    private var lastStreamName: String? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3

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
            // Pulse logic: Only run while actually playing.
            if (playState != PlayState.Playing) return
            
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

    fun play(stationURL: String?, streamName: String?, metadata: androidx.media3.common.MediaMetadata? = null) {
        userWantPlaying = true
        isPausing = false
        playerThreadHandler.post {
            playInternal(stationURL, streamName, false, metadata, stationURL)
        }
    }

    private fun playInternal(stationURL: String?, streamName: String?, isReconnect: Boolean, metadata: androidx.media3.common.MediaMetadata? = null, stationUuid: String? = null) {
        if (stationURL == null) return
        
        // Guard: Check if the user paused while we were resolving or about to play.
        if (!userWantPlaying || isPausing) {
            if (Utils.isDebug) Log.d(tag, "Guard: playInternal suppressed (userWantPlaying=$userWantPlaying, isPausing=$isPausing)")
            return
        }

        if (!isReconnect) {
            reconnectAttempts = 0
            lastStationURL = stationURL
            lastStreamName = streamName
            currentTargetMediaItem = Media3Utils.buildLiveMediaItem(stationURL.toUri(), metadata, stationUuid)
            
            pendingPlayRunnable?.let { playerThreadHandler.removeCallbacks(it) }
            prepareNewStream()
            
            val playTask = Runnable {
                val activeTarget = currentTargetMediaItem?.localConfiguration?.uri.toString()
                if (activeTarget == stationURL && (stationUuid == null || stationUuid == currentStationUuid)) {
                    executeActualPlayRemote(stationURL, streamName, metadata)
                }
            }
            pendingPlayRunnable = playTask
            playerThreadHandler.postDelayed(playTask, 180)
        } else {
            executeActualPlayRemote(stationURL, streamName, metadata)
        }
    }

    private fun executeActualPlayRemote(stationURL: String, streamName: String?, metadata: androidx.media3.common.MediaMetadata?) {
        if (!userWantPlaying || isPausing) return
        setState(PlayState.PrePlaying, -1)
        this.streamName = streamName
        val app = mainContext.applicationContext as AMARadioApp
        val customizedHttpClient = app.newHttpClient().build()
        currentPlayer.playRemote(customizedHttpClient, stationURL, mainContext, metadata)
    }

    fun play(station: DataRadioStation, metadata: androidx.media3.common.MediaMetadata? = null) {
        val uuid = station.StationUuid
        userWantPlaying = true
        isPausing = false
        
        if (uuid == currentStationUuid && playState == PlayState.Playing) return

        currentStationUuid = uuid
        playerThreadHandler.post {
            currentPlayer.stop()
            reconnectAttempts = 0
            stationLoadAttempts = 0
            currentStation = station
            executePlayStationTask(station, metadata)
        }
    }

    private fun executePlayStationTask(station: DataRadioStation, metadata: androidx.media3.common.MediaMetadata? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            if (!userWantPlaying || isPausing) return@launch
            setState(PlayState.PrePlaying, -1)
            val task = PlayStationTask(station, mainContext,
                { url -> 
                    playerThreadHandler.post {
                        if (userWantPlaying && !isPausing && currentStationUuid == station.StationUuid) {
                            this@RadioPlayer.playInternal(url, station.Name, false, metadata, station.StationUuid) 
                        }
                    }
                },
                { executionResult ->
                    if (executionResult == PlayStationTask.ExecutionResult.FAILURE) {
                        stationLoadAttempts++
                        if (stationLoadAttempts < 3 && userWantPlaying) {
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
        userWantPlaying = false
        isPausing = true
        
        // Immediate clean up on all relevant threads
        playerThreadHandler.removeCallbacks(bufferCheckRunnable)
        pendingPlayRunnable?.let { playerThreadHandler.removeCallbacks(it) }
        
        playerThreadHandler.post {
            cancelStationLinkRetrieval()
            val audioSessionId = audioSessionId
            currentPlayer.pause() // Use wrapper pause which stops the engine but manages state
            // We report Paused state, but the engine is IDLE (stopped).
            setState(PlayState.Paused, audioSessionId)
        }
    }

    fun stop() {
        userWantPlaying = false
        isPausing = false
        playerThreadHandler.removeCallbacks(bufferCheckRunnable)
        pendingPlayRunnable?.let { playerThreadHandler.removeCallbacks(it) }
        
        playerThreadHandler.post {
            currentStationUuid = null
            cancelStationLinkRetrieval()
            currentStation = null
            currentTargetMediaItem = null

            val audioSessionId = audioSessionId
            setState(PlayState.Idle, audioSessionId)
            currentPlayer.stop()
        }
    }

    fun destroy() {
        userWantPlaying = false
        isPausing = false
        playerThreadHandler.removeCallbacks(bufferCheckRunnable)
        playerThreadHandler.post {
            currentStationUuid = null
            pendingPlayRunnable?.let { playerThreadHandler.removeCallbacks(it) }
            cancelStationLinkRetrieval()
            currentStation = null
            currentTargetMediaItem = null
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
        
        val oldState = playState
        playState = state

        if (state == PlayState.Playing) {
            isPausing = false
            reconnectAttempts = 0
            cachedAudioSessionId = currentPlayer.audioSessionId
            lastPlayingStateTime = System.currentTimeMillis()
            
            playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            playerThreadHandler.post(bufferCheckRunnable)
        } else {
            playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            // Critical: Only reset isPausing if we are logically stopping, not during a user-requested pause
            if (state == PlayState.Idle && userWantPlaying) isPausing = false
        }

        if (oldState != state) {
            playerListener?.onStateChanged(state, audioSessionId)
        }
    }

    val currentPlaybackTransferredBytes: Long
        get() = currentPlayer.currentPlaybackTransferredBytes

    val bufferedSeconds: Long
        get() = currentPlayer.bufferedMs / 1000

    val isLocal: Boolean
        get() = currentPlayer.isLocal

    override fun onStateChanged(state: PlayState) {
        // Aggressive Guard: Block any automated state update that contradicts user intent
        if (!userWantPlaying && (state == PlayState.Playing || state == PlayState.PrePlaying)) {
            if (Utils.isDebug) Log.d(tag, "Blocking unexpected engine state $state while in user-paused mode.")
            return
        }

        // Hysteresis
        if (state == PlayState.PrePlaying && playState == PlayState.Playing) {
            val now = System.currentTimeMillis()
            val timeInPlaying = now - lastPlayingStateTime
            if (timeInPlaying < MIN_PLAYING_DURATION_BEFORE_REBUFFER_MS || currentPlayer.bufferedMs > 800) {
                return
            }
        }

        // Fake Pause Guard: Ignore engine IDLE if we are logically pausing.
        // We want to stay in PlayState.Paused for the UI/Notification/MediaSession.
        if (state == PlayState.Idle && (isPausing || userWantPlaying == false)) {
            if (Utils.isDebug) Log.d(tag, "Suppressing engine IDLE while logically paused.")
            return
        }

        setState(state, audioSessionId)
    }

    override fun onPlayerWarning(messageId: Int) {
        playerThreadHandler.post { playerListener?.onPlayerWarning(messageId) }
    }

    override fun onPlayerError(messageId: Int) {
        playerThreadHandler.post { 
            currentPlayer.stop()
            playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            
            if (reconnectAttempts < maxReconnectAttempts && userWantPlaying) {
                reconnectAttempts++
                val recoveryUuid = currentStationUuid
                playerThreadHandler.postDelayed({
                    if (currentStationUuid == recoveryUuid && userWantPlaying) {
                        prepareNewStream()
                        currentTargetMediaItem?.let { targetItem ->
                            val app = mainContext.applicationContext as AMARadioApp
                            currentPlayer.playRemote(app.newHttpClient().build(), targetItem.localConfiguration?.uri.toString(), mainContext, targetItem.mediaMetadata)
                        }
                    }
                }, 2000)
                setState(PlayState.PrePlaying, audioSessionId)
            } else {
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
