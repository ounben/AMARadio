-- 1. Tabelle anlegen (Exakte MariaDB-Reihenfolge)
CREATE TABLE `Station` (
    `StationID` INTEGER,                     -- 1
    `Name` TEXT,                             -- 2
    `Url` TEXT,                              -- 3
    `Homepage` TEXT,                         -- 4
    `Favicon` TEXT,                          -- 5
    `Creation` TEXT NOT NULL,                -- 6
    `Country` TEXT,                          -- 7
    `Language` TEXT,                         -- 8
    `Tags` TEXT,                             -- 9
    `Votes` INTEGER DEFAULT 0,               -- 10
    `Subcountry` TEXT,                       -- 11
    `clickcount` INTEGER NOT NULL DEFAULT 0, -- 12
    `ClickTrend` INTEGER DEFAULT 0,          -- 13
    `ClickTimestamp` TEXT,                   -- 14
    `Codec` TEXT,                            -- 15
    `LastCheckOK` INTEGER NOT NULL DEFAULT 1,-- 16
    `LastCheckTime` TEXT,                    -- 17
    `Bitrate` INTEGER NOT NULL DEFAULT 0,    -- 18
    `UrlCache` TEXT NOT NULL,                -- 19
    `LastCheckOkTime` TEXT,                  -- 20
    `Hls` INTEGER NOT NULL DEFAULT 0,        -- 21
    `ChangeUuid` TEXT,                       -- 22
    `StationUuid` TEXT PRIMARY KEY NOT NULL, -- 23
    `CountryCode` TEXT,                      -- 24
    `LastLocalCheckTime` TEXT,               -- 25
    `CountrySubdivisionCode` TEXT,           -- 26
    `GeoLat` REAL,                           -- 27
    `GeoLong` REAL,                          -- 28
    `SslError` INTEGER NOT NULL DEFAULT 0,   -- 29
    `LanguageCodes` TEXT,                    -- 30
    `ExtendedInfo` INTEGER NOT NULL DEFAULT 0,-- 31
    `ServerUuid` TEXT,                       -- 32
    `LastChangeTime` TEXT                    -- 33
);

-- 2. Performance Indizes
CREATE UNIQUE INDEX `index_Station_ChangeUuid` ON `Station` (`ChangeUuid`);
CREATE INDEX `index_Station_LastChangeTime` ON `Station` (`LastChangeTime`);
CREATE INDEX `index_Station_clickcount` ON `Station` (`clickcount`);

-- 3. FTS fuer die Suche
CREATE VIRTUAL TABLE `StationFTS` USING FTS4(content=`Station`, `Name`, `Tags`);


-- Tabelle für den Tag-Cache erstellen
CREATE TABLE IF NOT EXISTS TagCache (
    TagName TEXT PRIMARY KEY NOT NULL COLLATE BINARY,
    StationCount INTEGER DEFAULT 0,
    StationCountWorking INTEGER DEFAULT 0
);

-- Index für schnelles Sortieren nach Beliebtheit (wichtig für die UI-Liste)
CREATE INDEX IF NOT EXISTS index_TagCache_StationCount ON TagCache (StationCount DESC);

-- Tabelle für den Sprachen-Cache erstellen
CREATE TABLE IF NOT EXISTS LanguageCache (
    LanguageName TEXT PRIMARY KEY NOT NULL COLLATE BINARY,
    StationCount INTEGER DEFAULT 0,
    StationCountWorking INTEGER DEFAULT 0
);

-- Index für schnelles Sortieren nach Beliebtheit
CREATE INDEX IF NOT EXISTS index_LanguageCache_StationCount ON LanguageCache (StationCount DESC);