-- Erstelle die Indizes mit den von Room erwarteten Namen
CREATE UNIQUE INDEX IF NOT EXISTS `index_Station_StationUuid` ON `Station` (`StationUuid`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_Station_ChangeUuid` ON `Station` (`ChangeUuid`);
CREATE INDEX IF NOT EXISTS `index_Station_CountryCode` ON `Station` (`CountryCode`);
CREATE INDEX IF NOT EXISTS `index_Station_Language` ON `Station` (`Language`);
CREATE INDEX IF NOT EXISTS `index_Station_Votes` ON `Station` (`Votes`);
CREATE INDEX IF NOT EXISTS `index_Station_clickcount` ON `Station` (`clickcount`);
CREATE INDEX IF NOT EXISTS `index_Station_LastCheckOkTime` ON `Station` (`LastCheckOkTime`);
CREATE INDEX IF NOT EXISTS `index_Station_Bitrate` ON `Station` (`Bitrate`);
CREATE INDEX IF NOT EXISTS `index_Station_Hls` ON `Station` (`Hls`);