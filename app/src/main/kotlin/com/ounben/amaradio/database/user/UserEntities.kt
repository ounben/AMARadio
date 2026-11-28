package com.ounben.amaradio.database.user

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.Date

@Entity(tableName = "station_favourite")
data class FavoriteEntity(
    @PrimaryKey val StationUuid: String,
    val Name: String,
    val Url: String,
    val Homepage: String = "",
    val Favicon: String,
    val Country: String,
    val CountryCode: String,
    val Tags: String,
    val Language: String,
    val Votes: Int = 0,
    val Subcountry: String = "",
    val clickcount: Int = 0,
    val ClickTrend: Int = 0,
    val Codec: String,
    val Bitrate: Int,
    val LastChangeTime: String = "",
    val Creation: String = "",
    val ChangeUuid: String = "",
    val LastCheckOkTime: String = "",
    val addedAt: Date = Date(),
    val displayOrder: Int = 0
)

@Entity(tableName = "station_history")
data class HistoryEntity(
    @PrimaryKey val StationUuid: String,
    val Name: String,
    val Url: String,
    val Homepage: String = "",
    val Favicon: String,
    val Country: String,
    val CountryCode: String,
    val Tags: String,
    val Language: String,
    val Votes: Int = 0,
    val Subcountry: String = "",
    val clickcount: Int = 0,
    val ClickTrend: Int = 0,
    val Codec: String,
    val Bitrate: Int,
    val LastChangeTime: String = "",
    val Creation: String = "",
    val ChangeUuid: String = "",
    val LastCheckOkTime: String = "",
    val lastPlayedAt: Date = Date()
)

@Serializable
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
    val tag2: String = "",
    val tag3: String = "",
    val tagOperator: String = "AND",
    val codec: String = "",
    val bitrate: Int = 0,
    val bitrateOperator: String = ">=",
    val sortBy: String,
    val reverse: Boolean,
    val position: Int // To maintain user order
)

@Serializable
@Entity(tableName = "favorite_list")
data class FavoriteListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "favorite_list_item",
    primaryKeys = ["listId", "stationUuid"],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["listId"]),
        Index(value = ["stationUuid"])
    ]
)
data class FavoriteListItemEntity(
    val listId: String,
    val stationUuid: String,
    val displayOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "station_audio_settings")
data class StationAudioEntity(
    @PrimaryKey val stationUuid: String,
    val manualGainDb: Float = 0f,
    val learnedGainDb: Float? = null,
    val eqEnabled: Boolean? = null,
    val eqPresetIndex: Int? = null,
    val eqBands: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
