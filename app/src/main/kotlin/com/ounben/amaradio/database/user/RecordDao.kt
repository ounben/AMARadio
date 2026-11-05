package com.ounben.amaradio.database.user

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordingsFlow(): Flow<List<RecordEntity>>

    @Query("SELECT DISTINCT Name FROM recordings ORDER BY Name ASC")
    fun getRecordedStationNamesFlow(): Flow<List<String>>

    @Query("""
        SELECT * FROM recordings 
        WHERE (:stationName IS NULL OR :stationName = '' OR Name = :stationName)
        AND (:startTimestamp <= 0 OR timestamp >= :startTimestamp)
        AND (:endTimestamp <= 0 OR timestamp <= :endTimestamp)
        ORDER BY timestamp DESC
    """)
    fun getFilteredRecordingsFlow(
        stationName: String?,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<RecordEntity>>

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    suspend fun getAllRecordings(): List<RecordEntity>

    @Query("SELECT * FROM recording_tracks WHERE recordId = :recordId ORDER BY offsetMs ASC")
    fun getTracksForRecordingFlow(recordId: Long): Flow<List<RecordTrackEntity>>

    @Query("SELECT * FROM recording_tracks WHERE recordId = :recordId ORDER BY offsetMs ASC")
    suspend fun getTracksForRecording(recordId: Long): List<RecordTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<RecordTrackEntity>)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM recordings")
    suspend fun deleteAll()
}
