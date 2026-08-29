package com.ounben.amaradio.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "custom_station")
data class CustomStationEntity(
    @PrimaryKey val StationUuid: String,
    val Name: String,
    val Url: String,
    val Homepage: String = "",
    val Favicon: String,
    val Country: String = "",
    val CountryCode: String = "",
    val Tags: String = "",
    val Language: String = "",
    val Votes: Int = 0,
    val Subcountry: String = "",
    val clickcount: Int = 0,
    val ClickTrend: Int = 0,
    val Codec: String = "",
    val Bitrate: Int = 0,
    val LastChangeTime: String = "",
    val Creation: String = "",
    val ChangeUuid: String = "",
    val LastCheckOkTime: String = "",
    val displayOrder: Int = 0,
    val addedAt: Date = Date()
)
