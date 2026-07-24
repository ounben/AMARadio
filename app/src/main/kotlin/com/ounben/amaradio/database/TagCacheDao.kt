package com.ounben.amaradio.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagCacheDao {
    @Query("SELECT * FROM TagCache ORDER BY StationCount DESC")
    fun getAllTagsFlow(): Flow<List<TagCacheEntity>>

    @Query("SELECT * FROM TagCache ORDER BY StationCount DESC")
    suspend fun getAllTags(): List<TagCacheEntity>

    @Query("SELECT * FROM TagCache WHERE TagName LIKE :query || '%' ORDER BY StationCount DESC LIMIT 100")
    suspend fun searchTags(query: String): List<TagCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagCacheEntity>)

    @Query("DELETE FROM TagCache")
    suspend fun deleteAll()
}
