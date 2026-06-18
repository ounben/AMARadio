package com.ounben.amaradio.history

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.ounben.amaradio.database.AMARadioDatabase
import java.util.Date
import java.util.concurrent.Executor

class TrackHistoryRepository(application: Application) {
    fun interface GetItemCallback {
        fun onItemFetched(trackHistoryEntry: TrackHistoryEntry?, dao: TrackHistoryDao)
    }

    private val dao: TrackHistoryDao
    private val queryExecutor: Executor
    val allHistoryPaged: LiveData<PagedList<TrackHistoryEntry>>

    private var insertsToTruncateLeft = 0

    init {
        val db = AMARadioDatabase.getDatabase(application)
        dao = db.songHistoryDao()
        queryExecutor = db.databaseExecutor
        allHistoryPaged = LivePagedListBuilder(
            dao.getAllHistoryPositional(),
            PagedList.Config.Builder()
                .setPageSize(HISTORY_PAGE_SIZE)
                .setEnablePlaceholders(true)
                .build()
        ).build()
    }

    fun insert(historyEntry: TrackHistoryEntry) {
        queryExecutor.execute {
            dao.insert(historyEntry)
            if (insertsToTruncateLeft == 0) {
                insertsToTruncateLeft = TRUNCATE_FREQUENCY
                dao.truncateHistory(TrackHistoryEntry.MAX_HISTORY_ITEMS_IN_TABLE)
            } else {
                insertsToTruncateLeft--
            }
        }
    }

    fun setCurrentPlayingTrackEndTime(time: Date) {
        queryExecutor.execute { dao.setCurrentPlayingTrackEndTime(time) }
    }

    fun setLastHistoryItemEndTimeRelative(deltaSeconds: Int) {
        queryExecutor.execute { dao.setLastHistoryItemEndTimeRelative(deltaSeconds) }
    }

    fun setTrackArtUrl(id: Int, artUrl: String) {
        queryExecutor.execute { dao.setTrackArtUrl(id, artUrl) }
    }

    fun getLastInsertedHistoryItem(callback: GetItemCallback) {
        queryExecutor.execute {
            val item = dao.getLastInsertedHistoryItem()
            callback.onItemFetched(item, dao)
        }
    }

    fun deleteHistory() {
        queryExecutor.execute { dao.deleteHistory() }
    }

    companion object {
        private const val HISTORY_PAGE_SIZE = 15
        private const val TRUNCATE_FREQUENCY = 20
    }
}
