SELECT
    `StationID`, `Name`, `Url`, `Homepage`, `Favicon`,
    UNIX_TIMESTAMP(`Creation`) * 1000 AS `Creation`,
    `Country`, `Language`, `Tags`, `Votes`, `Subcountry`, `clickcount`,
    `ClickTrend`, UNIX_TIMESTAMP(`ClickTimestamp`) * 1000 AS `ClickTimestamp`,
    `Codec`, `LastCheckOK`, UNIX_TIMESTAMP(`LastCheckTime`) * 1000 AS `LastCheckTime`,
    `Bitrate`, `UrlCache`, UNIX_TIMESTAMP(`LastCheckOkTime`) * 1000 AS `LastCheckOkTime`,
    `Hls`, `ChangeUuid`, `StationUuid`, `CountryCode`,
    UNIX_TIMESTAMP(`LastLocalCheckTime`) * 1000 AS `LastLocalCheckTime`,
    `CountrySubdivisionCode`, `GeoLat`, `GeoLong`, `SslError`,
    `LanguageCodes`, `ExtendedInfo`, `ServerUuid`
FROM `Station`;