package net.ounben.AMARadio.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import net.ounben.AMARadio.AMARadioApp

class TrackHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TrackHistoryRepository

    init {
        val AMARadioApp = getApplication<AMARadioApp>()
        repository = AMARadioApp.trackHistoryRepository
    }

    val allHistoryPaged: LiveData<PagedList<TrackHistoryEntry>>
        get() = repository.allHistoryPaged
}
