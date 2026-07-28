# Entfernung des JSON-Fallbacks und Umstellung auf SQL-basierte Initialstation

Dieses Dokument beschreibt den Plan zur Entfernung des alten JSON-basierten Fallback-Systems (`fallback_stations.json`) und die Umstellung auf eine rein SQL-basierte Lösung. Zukünftig wird bei einer leeren Historie/Favoritenliste automatisch der populärste Sender aus der lokalen Region (basierend auf der Datenbank) als Startstation gewählt.

## Benutzerprüfung erforderlich

> [!IMPORTANT]
> - Das gesamte `FallbackStationsManager` System wird gelöscht.
> - Die Datei `fallback_stations.json` wird aus den Ressourcen entfernt.
> - Der Start-Sender wird nun dynamisch aus der lokalen SQL-Datenbank ermittelt (basierend auf dem `countryCode` und dem `clickcount`).

## Geplante Änderungen

### Infrastruktur & Bereinigung

#### [DELETE] [FallbackStationsManager.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/FallbackStationsManager.kt)
- Entfernung der Klasse, die den JSON-Fallback verwaltet.

#### [DELETE] [fallback_stations.json](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/res/raw/fallback_stations.json)
- Löschen der JSON-Datei mit den fest kodierten Sendern.

#### [MODIFY] [AMARadioApp.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/AMARadioApp.kt)
- Entfernung der Initialisierung des `FallbackStationsManager`.

### Programmlogik (SQL-basierter Fallback)

#### [MODIFY] [PlayerService.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/service/PlayerService.kt)
- Implementierung einer asynchronen Initialisierung des Start-Senders.
- Falls Historie und Favoriten leer sind, wird der oberste Sender der lokalen Liste aus der SQL-Datenbank geladen.

#### [MODIFY] [PlayerViewModel.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/ui/PlayerViewModel.kt)
- Anpassung der Fallback-Kette: `History -> Favorites -> SQL Local Top`.
- Entfernung der Abhängigkeit zum `fallbackStationsManager`.

#### [MODIFY] [StationDao.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/database/StationDao.kt)
- Sicherstellen, dass eine effiziente Methode existiert, um den Top-Sender für einen bestimmten Länder-Code abzufragen (bereits vorhanden: `getStationsByCountryCode`).

## Verifizierungsplan

### Automatisierte Tests
- Build-Check nach der Entfernung der Klassen und Ressourcen.

### Manuelle Verifizierung
- **Erststart-Szenario**: App-Daten löschen und prüfen, ob nach dem Start ein lokaler Sender (z.B. aus Deutschland/Schweiz) in der Player-Leiste erscheint, anstatt des alten polnischen Fallback-Senders.
- **Android Auto**: Überprüfung, ob beim ersten Verbinden (ohne Historie) ein gültiger lokaler Sender angezeigt wird.
- **Datenintegrität**: Sicherstellen, dass keine JSON-Abfragen mehr für den Fallback-Sender erfolgen.
