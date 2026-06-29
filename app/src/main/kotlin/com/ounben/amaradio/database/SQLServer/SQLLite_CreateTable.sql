-- 1. Tabelle anlegen
CREATE TABLE `Station` (
    `StationID` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `Name` TEXT, `Url` TEXT, `Homepage` TEXT, `Favicon` TEXT,
    `Creation` INTEGER, `Country` TEXT, `Language` TEXT, `Tags` TEXT,
    `Votes` INTEGER, `Subcountry` TEXT, `clickcount` INTEGER NOT NULL,
    `ClickTrend` INTEGER, `ClickTimestamp` INTEGER, `Codec` TEXT,
    `LastCheckOK` INTEGER NOT NULL, `LastCheckTime` INTEGER,
    `Bitrate` INTEGER NOT NULL, `UrlCache` TEXT NOT NULL,
    `LastCheckOkTime` INTEGER, `Hls` INTEGER NOT NULL,
    `ChangeUuid` TEXT, `StationUuid` TEXT, `CountryCode` TEXT,
    `LastLocalCheckTime` INTEGER, `CountrySubdivisionCode` TEXT,
    `GeoLat` REAL, `GeoLong` REAL, `SslError` INTEGER NOT NULL,
    `LanguageCodes` TEXT, `ExtendedInfo` INTEGER NOT NULL, `ServerUuid` TEXT
);

-- 2. Indizes mit Room-spezifischen Namen (WICHTIG)
CREATE UNIQUE INDEX `index_Station_StationUuid` ON `Station` (`StationUuid`);
CREATE UNIQUE INDEX `index_Station_ChangeUuid` ON `Station` (`ChangeUuid`);
CREATE INDEX `index_Station_CountryCode` ON `Station` (`CountryCode`);
CREATE INDEX `index_Station_Language` ON `Station` (`Language`);
CREATE INDEX `index_Station_Votes` ON `Station` (`Votes`);
CREATE INDEX `index_Station_clickcount` ON `Station` (`clickcount`);
CREATE INDEX `index_Station_LastCheckOkTime` ON `Station` (`LastCheckOkTime`);
CREATE INDEX `index_Station_Bitrate` ON `Station` (`Bitrate`);
CREATE INDEX `index_Station_Hls` ON `Station` (`Hls`);

-- 3. FTS-Tabelle für die Suche
CREATE VIRTUAL TABLE `StationFTS` USING FTS4(content=`Station`, `Name`, `Tags`);