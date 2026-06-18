package com.ounben.amaradio.history

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.util.Date

@Dao
interface TrackHistoryDao {
    @Insert
    fun insert(historyEntry: TrackHistoryEntry)

    @Update
    fun update(historyEntry: TrackHistoryEntry)

    @Query("SELECT * from track_history ORDER BY uid DESC")
    fun getAllHistory(): LiveData<List<TrackHistoryEntry>>

    @Query("SELECT * FROM track_history ORDER BY uid DESC")
    fun getAllHistoryPositional(): DataSource.Factory<Int, TrackHistoryEntry>

    @Query("SELECT * FROM track_history ORDER BY uid DESC LIMIT 1")
    fun getLastInsertedHistoryItem(): TrackHistoryEntry?

    @Query("UPDATE track_history SET end_time = :time WHERE end_time = 0")
    fun setCurrentPlayingTrackEndTime(time: Date)

    @Query("UPDATE track_history SET end_time = start_time + :deltaSeconds WHERE end_time = 0")
    fun setLastHistoryItemEndTimeRelative(deltaSeconds: Int)

    @Query("UPDATE track_history SET art_url = :artUrl WHERE uid = :id")
    fun setTrackArtUrl(id: Int, artUrl: String)

    @Query("DELETE FROM track_history WHERE uid < (SELECT MIN(uid) FROM (SELECT uid FROM track_history ORDER BY uid DESC LIMIT :limit))")
    fun truncateHistory(limit: Int)

    @Query("DELETE FROM track_history")
    fun deleteHistory()
}
