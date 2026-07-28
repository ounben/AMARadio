package com.ounben.amaradio.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.service.PauseReason
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.live.StreamLiveInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    data class PlayerUiState(
        val currentStation: DataRadioStation? = null,
        val playState: PlayState = PlayState.Idle,
        val liveInfo: StreamLiveInfo = StreamLiveInfo(null),
        val isFavorite: Boolean = false
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _bandwidth = MutableStateFlow("")
    val bandwidth: StateFlow<String> = _bandwidth.asStateFlow()

    private val _warningMessage = MutableStateFlow<Int?>(null)
    val warningMessage: StateFlow<Int?> = _warningMessage.asStateFlow()

    private var bandwidthJob: Job? = null
    private var lastBytes: Long = 0

    init {
        updateState()
        viewModelScope.launch {
            AppEventManager.events.collect { intent ->
                when (intent.action) {
                    PlayerService.PLAYER_SERVICE_STATE_CHANGE,
                    PlayerService.PLAYER_SERVICE_META_UPDATE,
                    PlayerService.PLAYER_SERVICE_BOUND -> updateState()
                }
            }
        }
        
        val app = application as AMARadioApp
        viewModelScope.launch {
            app.favouriteManager.stationsFlow.collect {
                updateFavoriteState()
                if (_uiState.value.currentStation == null) updateState()
            }
        }

        viewModelScope.launch {
            app.historyManager.stationsFlow.collect {
                if (_uiState.value.currentStation == null) updateState()
            }
        }
    }

    private fun updateState() {
        var state = PlayerServiceUtil.getPlayerState()
        var station = PlayerServiceUtil.getCurrentStation()
        
        // Initial fallback chain: History -> Favorites
        if (station == null) {
            val app = getApplication<AMARadioApp>()
            station = app.historyManager.first ?: app.favouriteManager.first
            state = PlayState.Idle
        }

        _uiState.update {
            it.copy(
                currentStation = station,
                playState = state,
                liveInfo = PlayerServiceUtil.getMetadataLive(),
                isFavorite = station?.let { s -> (getApplication<AMARadioApp>()).favouriteManager.has(s.StationUuid) } ?: false
            )
        }
        
        if (state == PlayState.Playing || state == PlayState.PrePlaying) {
            startBandwidthTracking()
        } else {
            stopBandwidthTracking()
        }
    }

    private fun startBandwidthTracking() {
        if (bandwidthJob?.isActive == true) return
        lastBytes = PlayerServiceUtil.getTransferredBytes()
        bandwidthJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val currentBytes = PlayerServiceUtil.getTransferredBytes()
                val diff = currentBytes - lastBytes
                lastBytes = currentBytes
                
                val speedKBs = if (diff > 0) diff / 1024.0 else 0.0
                val speedString = if (speedKBs > 0) "%.1f kB/s".format(speedKBs) else "0.0 kB/s"
                
                _bandwidth.value = speedString
            }
        }
    }

    private fun stopBandwidthTracking() {
        bandwidthJob?.cancel()
        bandwidthJob = null
        _bandwidth.value = ""
    }

    private fun updateFavoriteState() {
        val station = _uiState.value.currentStation
        val fav = station?.let { s -> (getApplication<AMARadioApp>()).favouriteManager.has(s.StationUuid) } ?: false
        _uiState.update { it.copy(isFavorite = fav) }
    }

    fun togglePlayPause() {
        if (PlayerServiceUtil.isPlaying()) {
            PlayerServiceUtil.pause(PauseReason.USER)
        } else {
            val station = _uiState.value.currentStation ?: getApplication<AMARadioApp>().historyManager.first
            if (station != null) {
                play(station)
            }
        }
    }

    fun play(station: DataRadioStation) {
        val app = getApplication<AMARadioApp>()
        
        // 1. Check Internet Connection
        if (!Utils.hasAnyConnection(app)) {
            _warningMessage.value = R.string.error_no_connection
            return
        }

        // 2. Check Volume
        val attributedContext = Utils.getAttributedContext(app)
        val audioManager = attributedContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume == 0) {
            _warningMessage.value = R.string.warning_volume_zero
        }

        PlayerServiceUtil.play(station)
    }

    fun clearWarning() {
        _warningMessage.value = null
    }

    fun skipToNext() = PlayerServiceUtil.skipToNext()
    fun skipToPrevious() = PlayerServiceUtil.skipToPrevious()
    
    fun toggleFavorite() {
        val station = _uiState.value.currentStation ?: return
        val app = getApplication<AMARadioApp>()
        if (app.favouriteManager.has(station.StationUuid)) {
            app.favouriteManager.remove(station.StationUuid)
        } else {
            app.favouriteManager.add(station)
        }
    }
}

