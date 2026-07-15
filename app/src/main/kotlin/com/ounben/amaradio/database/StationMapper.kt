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
        creation = "2024-01-01 00:00:00",
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
