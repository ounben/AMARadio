package com.ounben.amaradio.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM Station ORDER BY clickcount DESC")
    fun getAllStations(): Flow<List<StationEntity>>

    @Query("SELECT * FROM Station WHERE CountryCode = :code ORDER BY clickcount DESC")
    fun getStationsByCountry(code: String): Flow<List<StationEntity>>

    @Query("SELECT * FROM Station WHERE StationUuid = :uuid LIMIT 1")
    suspend fun getStationByUuid(uuid: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Query("SELECT MAX(LastCheckOkTime) FROM Station")
    suspend fun getLastSyncTime(): String?

    @Query("DELETE FROM Station")
    suspend fun deleteAll()
}
