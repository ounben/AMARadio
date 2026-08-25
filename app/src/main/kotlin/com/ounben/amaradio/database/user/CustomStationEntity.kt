package com.ounben.amaradio.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "custom_station")
data class CustomStationEntity(
    @PrimaryKey
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val iconUrl: String,
    val country: String = "",
    val countryCode: String = "",
    val tags: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val displayOrder: Int = 0,
    val addedAt: Date = Date()
)
