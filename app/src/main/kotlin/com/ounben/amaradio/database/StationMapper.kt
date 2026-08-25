package com.ounben.amaradio.database

import com.ounben.amaradio.database.user.CustomStationEntity
import com.ounben.amaradio.database.user.FavoriteEntity
import com.ounben.amaradio.database.user.HistoryEntity
import com.ounben.amaradio.station.DataRadioStation

fun DataRadioStation.toEntity(): StationEntity {
    return StationEntity(
        stationId = null,
        name = this.Name,
        url = this.StreamUrl,
        homepage = this.HomePageUrl,
        favicon = this.IconUrl,
        creation = if (this.Creation.isNullOrEmpty()) "1970-01-01 00:00:00" else this.Creation,
        country = this.Country,
        language = this.Language,
        tags = this.TagsAll,
        votes = this.Votes,
        subcountry = this.State,
        clickCount = this.ClickCount,
        clickTrend = this.ClickTrend,
        clickTimestamp = null,
        codec = this.Codec,
        lastCheckOk = 1,
        lastCheckTime = null,
        bitrate = this.Bitrate,
        urlCache = this.playableUrl ?: this.StreamUrl,
        lastCheckOkTime = this.LastCheckOkTime,
        hls = if (this.StreamUrl.contains("m3u8")) 1 else 0,
        changeUuid = this.ChangeUuid,
        stationUuid = this.StationUuid,
        countryCode = this.CountryCode,
        lastLocalCheckTime = null,
        countrySubdivisionCode = null,
        geoLat = null,
        geoLong = null,
        sslError = 0,
        languageCodes = null,
        extendedInfo = 0,
        serverUuid = null,
        lastChangeTime = if (this.LastChangeTime.isNullOrEmpty()) "1970-01-01 00:00:00" else this.LastChangeTime
    )
}

fun StationEntity.toDataStation(): DataRadioStation {
    val station = DataRadioStation()
    station.Name = this.name ?: ""
    station.StreamUrl = this.url ?: ""
    station.HomePageUrl = this.homepage ?: ""
    station.IconUrl = this.favicon ?: ""
    station.Creation = this.creation
    station.Country = this.country ?: ""
    station.Language = this.language ?: ""
    station.TagsAll = this.tags ?: ""
    station.Votes = this.votes ?: 0
    station.State = this.subcountry ?: ""
    station.ClickCount = this.clickCount
    station.ClickTrend = this.clickTrend ?: 0
    station.Codec = this.codec ?: ""
    station.Bitrate = this.bitrate
    station.LastCheckOkTime = this.lastCheckOkTime ?: ""
    station.ChangeUuid = this.changeUuid ?: ""
    station.StationUuid = this.stationUuid
    station.CountryCode = this.countryCode ?: ""
    station.LastChangeTime = this.lastChangeTime ?: ""
    return station
}

fun FavoriteEntity.toDataStation(): DataRadioStation {
    return DataRadioStation(
        Name = this.name, StationUuid = this.stationUuid, StreamUrl = this.streamUrl, IconUrl = this.iconUrl,
        Country = this.country, CountryCode = this.countryCode, TagsAll = this.tags, Language = this.language,
        Codec = this.codec, Bitrate = this.bitrate
    )
}

fun HistoryEntity.toDataStation(): DataRadioStation {
    return DataRadioStation(
        Name = this.name, StationUuid = this.stationUuid, StreamUrl = this.streamUrl, IconUrl = this.iconUrl,
        Country = this.country, CountryCode = this.countryCode, TagsAll = this.tags, Language = this.language,
        Codec = this.codec, Bitrate = this.bitrate
    )
}

fun CustomStationEntity.toDataStation(): DataRadioStation {
    return DataRadioStation(
        Name = this.name, StationUuid = this.stationUuid, StreamUrl = this.streamUrl, IconUrl = this.iconUrl,
        Country = this.country, CountryCode = this.countryCode, TagsAll = this.tags, Language = this.language,
        Codec = this.codec, Bitrate = this.bitrate
    )
}

fun DataRadioStation.toCustomEntity(displayOrder: Int = 0): CustomStationEntity {
    return CustomStationEntity(
        stationUuid = this.StationUuid,
        name = this.Name,
        streamUrl = this.StreamUrl,
        iconUrl = this.IconUrl,
        country = this.Country,
        countryCode = this.CountryCode,
        tags = this.TagsAll,
        language = this.Language,
        codec = this.Codec,
        bitrate = this.Bitrate,
        displayOrder = displayOrder
    )
}
