package com.ounben.amaradio.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "LanguageCache",
    indices = [
        Index(value = ["StationCount"], name = "index_LanguageCache_StationCount", orders = [Index.Order.DESC])
    ]
)
data class LanguageCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "LanguageName")
    val languageName: String,

    @ColumnInfo(name = "StationCount", defaultValue = "0")
    val stationCount: Int? = 0,

    @ColumnInfo(name = "StationCountWorking", defaultValue = "0")
    val stationCountWorking: Int? = 0
)
