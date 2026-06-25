package com.ounben.amaradio.history

import android.app.Application
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.ounben.amaradio.database.AMARadioDatabase
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.Executor

class TrackHistoryRepository(application: Application) {
    fun interface GetItemCallback {
        fun onItemFetched(trackHistoryEntry: TrackHistoryEntry?, dao: TrackHistoryDao)
    }

    private val dao: TrackHistoryDao
    private val queryExecutor: Executor
    val allHistoryPagedFlow: Flow<PagingData<TrackHistoryEntry>>

    private var insertsToTruncateLeft = 0

    init {
        val db = AMARadioDatabase.getDatabase(application)
        dao = db.songHistoryDao()
        queryExecutor = db.databaseExecutor
        allHistoryPagedFlow = Pager(
            config = PagingConfig(
                pageSize = HISTORY_PAGE_SIZE,
                enablePlaceholders = true
            ),
            pagingSourceFactory = { dao.getAllHistoryPaged() }
        ).flow
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
