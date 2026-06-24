package com.ounben.amaradio.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ounben.amaradio.AMARadioApp
import kotlinx.coroutines.flow.Flow

class TrackHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TrackHistoryRepository

    init {
        val AMARadioApp = getApplication<AMARadioApp>()
        repository = AMARadioApp.trackHistoryRepository
    }

    val allHistoryPaged: Flow<PagingData<TrackHistoryEntry>>
        get() = repository.allHistoryPagedFlow.cachedIn(viewModelScope)
}
