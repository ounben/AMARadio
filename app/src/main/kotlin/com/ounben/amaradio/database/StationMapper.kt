package com.ounben.amaradio.database

import com.ounben.amaradio.station.DataRadioStation

fun DataRadioStation.toEntity(): StationEntity {
    return StationEntity(
        name = this.Name,
        stationUuid = this.StationUuid,
        changeUuid = this.ChangeUuid,
        url = this.StreamUrl,
        homepage = this.HomePageUrl,
        favicon = this.IconUrl,
        country = this.Country,
        countryCode = this.CountryCode,
        subcountry = this.State,
        tags = this.TagsAll,
        language = this.Language,
        clickCount = this.ClickCount,
        clickTrend = this.ClickTrend,
        votes = this.Votes,
        bitrate = this.Bitrate,
        codec = this.Codec,
        urlCache = this.playableUrl ?: this.StreamUrl,
        creation = null, // Falls in API-Suche nicht vorhanden
        lastCheckOk = 1,
        hls = if (this.StreamUrl.contains("m3u8")) 1 else 0,
        clickTimestamp = null,
        lastCheckTime = null,
        lastCheckOkTime = null,
        lastLocalCheckTime = null,
        countrySubdivisionCode = null,
        geoLat = null,
        geoLong = null,
        sslError = 0,
        languageCodes = null,
        extendedInfo = 0,
        serverUuid = null
    )
}

fun StationEntity.toDataStation(): com.ounben.amaradio.station.DataRadioStation {
    val station = com.ounben.amaradio.station.DataRadioStation()
    station.Name = this.name ?: ""
    station.StationUuid = this.stationUuid ?: ""
    station.ChangeUuid = this.changeUuid ?: ""
    station.StreamUrl = this.url ?: ""
    station.HomePageUrl = this.homepage ?: ""
    station.IconUrl = this.favicon ?: ""
    station.Country = this.country ?: ""
    station.CountryCode = this.countryCode ?: ""
    station.State = this.subcountry ?: ""
    station.TagsAll = this.tags ?: ""
    station.Language = this.language ?: ""
    station.ClickCount = this.clickCount
    station.ClickTrend = this.clickTrend ?: 0
    station.Votes = this.votes ?: 0
    station.Bitrate = this.bitrate
    station.Codec = this.codec ?: ""
    return station
}
