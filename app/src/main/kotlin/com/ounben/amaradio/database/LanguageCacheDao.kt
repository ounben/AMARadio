package com.ounben.amaradio.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageCacheDao {
    @Query("SELECT * FROM LanguageCache ORDER BY StationCount DESC")
    fun getAllLanguagesFlow(): Flow<List<LanguageCacheEntity>>

    @Query("SELECT * FROM LanguageCache ORDER BY StationCount DESC")
    suspend fun getAllLanguages(): List<LanguageCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(languages: List<LanguageCacheEntity>)

    @Query("DELETE FROM LanguageCache")
    suspend fun deleteAll()
}
