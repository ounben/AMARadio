-- 1. Tabelle anlegen
CREATE TABLE `Station` (
    `StationID` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `Name` TEXT,
    `Url` TEXT,
    `Homepage` TEXT,
    `Favicon` TEXT,
    `Creation` TEXT NOT NULL,
    `Country` TEXT,
    `Language` TEXT,
    `Tags` TEXT,
    `Votes` INTEGER DEFAULT 0,
    `Subcountry` TEXT,
    `clickcount` INTEGER NOT NULL DEFAULT 0,
    `ClickTrend` INTEGER DEFAULT 0,
    `ClickTimestamp` TEXT,
    `Codec` TEXT,
    `LastCheckOK` INTEGER NOT NULL DEFAULT 1,
    `LastCheckTime` TEXT,
    `Bitrate` INTEGER NOT NULL DEFAULT 0,
    `UrlCache` TEXT NOT NULL,
    `LastCheckOkTime` TEXT,
    `Hls` INTEGER NOT NULL DEFAULT 0,
    `ChangeUuid` TEXT,
    `StationUuid` TEXT,
    `CountryCode` TEXT,
    `LastLocalCheckTime` TEXT,
    `CountrySubdivisionCode` TEXT,
    `GeoLat` REAL,
    `GeoLong` REAL,
    `SslError` INTEGER NOT NULL DEFAULT 0,
    `LanguageCodes` TEXT,
    `ExtendedInfo` INTEGER NOT NULL DEFAULT 0,
    `ServerUuid` TEXT
);

-- 2. Indizes mit Room-spezifischen Namen
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
