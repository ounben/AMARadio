package com.ounben.amaradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.AppEventManager
import com.ounben.amaradio.players.PlayState
import com.ounben.amaradio.service.PauseReason
import com.ounben.amaradio.service.PlayerService
import com.ounben.amaradio.service.PlayerServiceUtil
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.station.live.StreamLiveInfo
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
            }
        }
    }

    private fun updateState() {
        val station = PlayerServiceUtil.getCurrentStation()
        _uiState.update {
            it.copy(
                currentStation = station,
                playState = PlayerServiceUtil.getPlayerState(),
                liveInfo = PlayerServiceUtil.getMetadataLive(),
                isFavorite = station?.let { s -> (getApplication<AMARadioApp>()).favouriteManager.has(s.StationUuid) } ?: false
            )
        }
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
            val station = _uiState.value.currentStation ?: (getApplication<AMARadioApp>()).historyManager.first
            if (station != null) {
                PlayerServiceUtil.play(station)
            }
        }
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
