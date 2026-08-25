package com.ounben.amaradio.database.user

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM station_favourite ORDER BY addedAt DESC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM station_favourite ORDER BY addedAt DESC")
    suspend fun getAllFavorites(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Delete
    suspend fun delete(favorite: FavoriteEntity)

    @Query("DELETE FROM station_favourite WHERE stationUuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM station_favourite WHERE stationUuid = :uuid)")
    suspend fun isFavorite(uuid: String): Boolean

    @Query("SELECT * FROM station_favourite WHERE stationUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): FavoriteEntity?
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM station_history ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM station_history ORDER BY lastPlayedAt DESC LIMIT 50")
    suspend fun getAllHistory(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity)

    @Query("DELETE FROM station_history WHERE stationUuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("DELETE FROM station_history")
    suspend fun clearAll()

    @Query("SELECT * FROM station_history WHERE stationUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): HistoryEntity?

    @Transaction
    suspend fun addStation(history: HistoryEntity) {
        insert(history)
        // Keep only top 25 (Match AMARadio legacy limit)
        trimHistory(25)
    }

    @Query("DELETE FROM station_history WHERE stationUuid NOT IN (SELECT stationUuid FROM station_history ORDER BY lastPlayedAt DESC LIMIT :limit)")
    suspend fun trimHistory(limit: Int)
}

@Dao
interface FilterTabDao {
    @Query("SELECT * FROM filter_tab ORDER BY position ASC")
    fun getAllTabsFlow(): Flow<List<FilterTabEntity>>

    @Query("SELECT * FROM filter_tab ORDER BY position ASC")
    suspend fun getAllTabs(): List<FilterTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: FilterTabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<FilterTabEntity>)

    @Query("DELETE FROM filter_tab WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM filter_tab")
    suspend fun deleteAll()

    @Transaction
    suspend fun updateAllTabs(tabs: List<FilterTabEntity>) {
        deleteAll()
        insertAll(tabs)
    }
}

@Dao
interface CustomStationDao {
    @Query("SELECT * FROM custom_station ORDER BY displayOrder ASC")
    fun getAllCustomStationsFlow(): Flow<List<CustomStationEntity>>

    @Query("SELECT * FROM custom_station ORDER BY displayOrder ASC")
    suspend fun getAllCustomStations(): List<CustomStationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: CustomStationEntity)

    @Update
    suspend fun update(station: CustomStationEntity)

    @Delete
    suspend fun delete(station: CustomStationEntity)

    @Query("DELETE FROM custom_station WHERE stationUuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT MAX(displayOrder) FROM custom_station")
    suspend fun getMaxOrder(): Int?

    @Transaction
    suspend fun updateAll(stations: List<CustomStationEntity>) {
        deleteAll()
        insertAll(stations)
    }

    @Query("DELETE FROM custom_station")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<CustomStationEntity>)
}
