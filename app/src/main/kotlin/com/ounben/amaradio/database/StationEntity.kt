package com.ounben.amaradio.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Station",
    indices = [
        Index(value = ["StationUuid"], unique = true),
        Index(value = ["ChangeUuid"], unique = true),
        Index(value = ["CountryCode"]),
        Index(value = ["Language"]),
        Index(value = ["Votes"]),
        Index(value = ["clickcount"]),
        Index(value = ["LastCheckOkTime"]),
        Index(value = ["Bitrate"]),
        Index(value = ["Hls"])
    ]
)
data class StationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "StationID")
    val stationId: Long = 0,

    @ColumnInfo(name = "Name")
    val name: String?,

    @ColumnInfo(name = "Url")
    val url: String?,

    @ColumnInfo(name = "Homepage")
    val homepage: String?,

    @ColumnInfo(name = "Favicon")
    val favicon: String?,

    @ColumnInfo(name = "Creation")
    val creation: String, // NOT NULL in DB

    @ColumnInfo(name = "Country")
    val country: String?,

    @ColumnInfo(name = "Language")
    val language: String?,

    @ColumnInfo(name = "Tags")
    val tags: String?,

    @ColumnInfo(name = "Votes", defaultValue = "0")
    val votes: Int? = 0,

    @ColumnInfo(name = "Subcountry")
    val subcountry: String?,

    @ColumnInfo(name = "clickcount", defaultValue = "0")
    val clickCount: Int = 0,

    @ColumnInfo(name = "ClickTrend", defaultValue = "0")
    val clickTrend: Int? = 0,

    @ColumnInfo(name = "ClickTimestamp")
    val clickTimestamp: String?,

    @ColumnInfo(name = "Codec")
    val codec: String?,

    @ColumnInfo(name = "LastCheckOK", defaultValue = "1")
    val lastCheckOk: Int = 1,

    @ColumnInfo(name = "LastCheckTime")
    val lastCheckTime: String?,

    @ColumnInfo(name = "Bitrate", defaultValue = "0")
    val bitrate: Int = 0,

    @ColumnInfo(name = "UrlCache")
    val urlCache: String, // NOT NULL in DB

    @ColumnInfo(name = "LastCheckOkTime")
    val lastCheckOkTime: String?,

    @ColumnInfo(name = "Hls", defaultValue = "0")
    val hls: Int = 0,

    @ColumnInfo(name = "ChangeUuid")
    val changeUuid: String?,

    @ColumnInfo(name = "StationUuid")
    val stationUuid: String?,

    @ColumnInfo(name = "CountryCode")
    val countryCode: String?,

    @ColumnInfo(name = "LastLocalCheckTime")
    val lastLocalCheckTime: String?,

    @ColumnInfo(name = "CountrySubdivisionCode")
    val countrySubdivisionCode: String?,

    @ColumnInfo(name = "GeoLat")
    val geoLat: Double?,

    @ColumnInfo(name = "GeoLong")
    val geoLong: Double?,

    @ColumnInfo(name = "SslError", defaultValue = "0")
    val sslError: Int = 0,

    @ColumnInfo(name = "LanguageCodes")
    val languageCodes: String?,

    @ColumnInfo(name = "ExtendedInfo", defaultValue = "0")
    val extendedInfo: Int = 0,

    @ColumnInfo(name = "ServerUuid")
    val serverUuid: String?
)
