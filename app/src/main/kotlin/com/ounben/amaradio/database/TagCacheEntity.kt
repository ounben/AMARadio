package com.ounben.amaradio.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "TagCache",
    indices = [
        Index(value = ["StationCount"], name = "index_TagCache_StationCount", orders = [Index.Order.DESC])
    ]
)
data class TagCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "TagName")
    val tagName: String,

    @ColumnInfo(name = "StationCount", defaultValue = "0")
    val stationCount: Int? = 0,

    @ColumnInfo(name = "StationCountWorking", defaultValue = "0")
    val stationCountWorking: Int? = 0
)
