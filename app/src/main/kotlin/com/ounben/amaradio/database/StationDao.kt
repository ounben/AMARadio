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

    @Query("SELECT * FROM Station WHERE CountryCode = :countryCode ORDER BY clickcount DESC LIMIT 100")
    suspend fun getStationsByCountryCode(countryCode: String): List<StationEntity>

    @Query("""
        SELECT * FROM Station 
        WHERE (:name IS NULL OR name LIKE '%' || :name || '%')
        AND (:countryCode IS NULL OR CountryCode = :countryCode)
        AND (:language IS NULL OR language LIKE '%' || :language || '%')
        AND (:tag IS NULL OR tags LIKE '%' || :tag || '%')
        ORDER BY clickcount DESC LIMIT 100
    """)
    suspend fun getStationsFiltered(name: String?, countryCode: String?, language: String?, tag: String?): List<StationEntity>

    @Query("""
        SELECT * FROM Station 
        WHERE Name LIKE '%' || :query || '%' 
        OR Tags LIKE '%' || :query || '%'
        ORDER BY clickcount DESC LIMIT 100
    """)
    suspend fun searchStations(query: String): List<StationEntity>

    @Query("DELETE FROM Station")
    suspend fun deleteAll()
}
