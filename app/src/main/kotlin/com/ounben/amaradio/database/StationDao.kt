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

    @Query("SELECT MAX(LastChangeTime) FROM Station")
    suspend fun getLastSyncTime(): String?

    @Query("SELECT * FROM Station ORDER BY LastChangeTime DESC LIMIT 5")
    suspend fun getRecentlyChangedStations(): List<StationEntity>

    @Query("SELECT COUNT(*) FROM Station")
    suspend fun getStationCount(): Int

    @Query("SELECT * FROM Station WHERE CountryCode = :countryCode ORDER BY clickcount DESC LIMIT 100")
    suspend fun getStationsByCountryCode(countryCode: String): List<StationEntity>

    @Query("""
        SELECT * FROM Station 
        WHERE (:name IS NULL OR Name LIKE '%' || :name || '%')
        AND (:countryCode IS NULL OR CountryCode = :countryCode)
        AND (:language IS NULL OR Language LIKE '%' || :language || '%')
        AND (:tag IS NULL OR Tags LIKE '%' || :tag || '%')
        ORDER BY 
            CASE WHEN :orderBy = 'clickcount' THEN clickcount END DESC,
            CASE WHEN :orderBy = 'name' THEN Name END ASC,
            CASE WHEN :orderBy = 'votes' THEN Votes END DESC,
            CASE WHEN :orderBy = 'lastchange' THEN LastChangeTime END DESC
        LIMIT 100
    """)
    suspend fun getStationsFiltered(name: String?, countryCode: String?, language: String?, tag: String?, orderBy: String): List<StationEntity>

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
