package com.ounben.amaradio.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM Station WHERE LastCheckOK = 1 ORDER BY clickcount DESC")
    fun getAllStations(): Flow<List<StationEntity>>

    @Query("SELECT * FROM Station WHERE CountryCode = :code AND LastCheckOK = 1 ORDER BY clickcount DESC")
    fun getStationsByCountry(code: String): Flow<List<StationEntity>>

    @Query("SELECT * FROM Station WHERE StationUuid = :uuid LIMIT 1")
    suspend fun getStationByUuid(uuid: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInternal(stations: List<StationEntity>)

    @Transaction
    suspend fun syncBatch(stations: List<StationEntity>) {
        insertAllInternal(stations)
    }

    @Query("SELECT MAX(LastChangeTime) FROM Station WHERE LastChangeTime NOT LIKE '1970%' AND LastCheckOK = 1")
    suspend fun getLastSyncTime(): String?

    @Query("SELECT * FROM Station WHERE LastCheckOK = 1 ORDER BY LastChangeTime DESC LIMIT 200")
    suspend fun getRecentlyChangedStations(): List<StationEntity>

    @Query("SELECT COUNT(*) FROM Station WHERE LastCheckOK = 1")
    suspend fun getStationCount(): Int

    @Query("SELECT * FROM Station WHERE CountryCode = :countryCode AND LastCheckOK = 1 ORDER BY clickcount DESC LIMIT 300")
    suspend fun getStationsByCountryCode(countryCode: String): List<StationEntity>

    // Advanced search with various sort options - only functional stations
    @Query("""
        SELECT * FROM Station 
        WHERE LastCheckOK = 1
        AND (:w1 IS NULL OR (Name LIKE '%' || :w1 || '%' OR Tags LIKE '%' || :w1 || '%'))
        AND (:w2 IS NULL OR (Name LIKE '%' || :w2 || '%' OR Tags LIKE '%' || :w2 || '%'))
        AND (:w3 IS NULL OR (Name LIKE '%' || :w3 || '%' OR Tags LIKE '%' || :w3 || '%'))
        AND (:countryCode IS NULL OR CountryCode = :countryCode)
        AND (:language IS NULL OR Language LIKE '%' || :language || '%')
        AND (:tag IS NULL OR Tags LIKE '%' || :tag || '%')
        ORDER BY 
            CASE WHEN :orderBy = 'clickcount' AND :reverse = 1 THEN clickcount END DESC,
            CASE WHEN :orderBy = 'clickcount' AND :reverse = 0 THEN clickcount END ASC,
            CASE WHEN :orderBy = 'Name' AND :reverse = 1 THEN Name END COLLATE NOCASE DESC,
            CASE WHEN :orderBy = 'Name' AND :reverse = 0 THEN Name END COLLATE NOCASE ASC,
            CASE WHEN :orderBy = 'Votes' AND :reverse = 1 THEN Votes END DESC,
            CASE WHEN :orderBy = 'Votes' AND :reverse = 0 THEN Votes END ASC,
            CASE WHEN :orderBy = 'LastChangeTime' AND :reverse = 1 THEN LastChangeTime END DESC,
            CASE WHEN :orderBy = 'LastChangeTime' AND :reverse = 0 THEN LastChangeTime END ASC,
            clickcount DESC, Name COLLATE NOCASE ASC
        LIMIT 500
    """)
    suspend fun getStationsFiltered(
        w1: String?, w2: String?, w3: String?, 
        countryCode: String?, language: String?, tag: String?, 
        orderBy: String, reverse: Int
    ): List<StationEntity>

    @Query("""
        SELECT * FROM Station 
        WHERE LastCheckOK = 1
        AND (:w1 IS NULL OR (Name LIKE '%' || :w1 || '%' OR Tags LIKE '%' || :w1 || '%'))
        AND (:w2 IS NULL OR (Name LIKE '%' || :w2 || '%' OR Tags LIKE '%' || :w2 || '%'))
        AND (:w3 IS NULL OR (Name LIKE '%' || :w3 || '%' OR Tags LIKE '%' || :w3 || '%'))
        ORDER BY clickcount DESC LIMIT 400
    """)
    suspend fun searchStationsMulti(w1: String?, w2: String?, w3: String?): List<StationEntity>

    @Query("""
        SELECT * FROM Station 
        WHERE LastCheckOK = 1 
        AND (Name LIKE '%' || :query || '%' OR Tags LIKE '%' || :query || '%')
        ORDER BY clickcount DESC LIMIT 300
    """)
    suspend fun searchStations(query: String): List<StationEntity>

    @Query("DELETE FROM Station")
    suspend fun deleteAll()

    @Query("SELECT * FROM Station WHERE Url = :url OR UrlCache = :url LIMIT 1")
    suspend fun getStationByUrl(url: String): StationEntity?
}
