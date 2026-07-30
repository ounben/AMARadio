package com.ounben.amaradio.database.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "station_favourite")
data class FavoriteEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val iconUrl: String,
    val country: String,
    val countryCode: String,
    val tags: String,
    val language: String,
    val codec: String,
    val bitrate: Int,
    val addedAt: Date = Date()
)

@Entity(tableName = "station_history")
data class HistoryEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val iconUrl: String,
    val country: String,
    val countryCode: String,
    val tags: String,
    val language: String,
    val codec: String,
    val bitrate: Int,
    val lastPlayedAt: Date = Date()
)

@Entity(tableName = "filter_tab")
data class FilterTabEntity(
    @PrimaryKey val id: String,
    val label: String,
    val name: String,
    val countryCode: String,
    val countryLabel: String,
    val countryEmoji: String,
    val languageCode: String,
    val languageLabel: String,
    val tag: String,
    val sortBy: String,
    val reverse: Boolean,
    val position: Int // To maintain user order
)
