# Implementierung von Android Auto Support für AMARadio (StationUuid-Centric)

Dieses Dokument beschreibt den Plan zur Integration von Android Auto. Das System fungiert als reiner Anzeige-Client ("Dumb Client"). Die Datenhoheit liegt beim Smartphone. Zentraler Anker für alle Identifikationen und Caching-Prozesse ist die `StationUuid`.

## Zusammenfassung
Die App wird für Android Auto registriert. Die Navigationsstruktur spiegelt die Tabs des Smartphones wider. Alle Medien-Elemente und Aktionen werden über die `StationUuid` gesteuert. Bilder werden unter Berücksichtigung des bestehenden Coil-Mechanismus und des dedizierten `station_icons` Cache-Ordners geladen.

## Benutzerprüfung erforderlich
> [!IMPORTANT]
> - **StationUuid als einziger Primärschlüssel**: Alle `mediaId`s und Playback-Befehle nutzen ausschließlich das Feld `StationUuid`. Felder wie `uuid` oder `changeuuid` werden ignoriert.
> - **Cache Priorisierung (Coil aware)**: Der `StationIconProvider` prüft zwingend zuerst den Ordner `cache/station_icons/[StationUuid].jpg`. Falls nicht vorhanden, wird Coil genutzt, um das Bild zu laden, und anschließend als JPEG in `station_icons` für Android Auto exportiert.
> - **Robuste Sprachsuche**: Die Auflösung von Sprachbefehlen (z.B. "3 swr") zu einer `StationUuid` erfolgt über eine Schlagwort-basierte Suche in der lokalen SQL-Datenbank, um auch bei ungenauer Aussprache oder vertauschter Wortreihenfolge den richtigen Sender zu finden.

## Geplante Änderungen

### 1. Registrierung & Manifest
- Offizielle Deklaration der Automotive-Kompatibilität in der `AndroidManifest.xml` (erledigt).

### 2. Navigations-Logik (StationUuid-basiert)

#### [MODIFY] [AMARadioBrowser.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/service/AMARadioBrowser.kt)
- **Flattening**: Direkte Anzeige von Favoriten, Verlauf, Lokal und Filter-Tabs auf Root-Ebene.
- **StationUuid-Mapping**: Jedes `MediaItem` trägt die `StationUuid` als `mediaId` (Präfix `station_`).
- **Dumb Search**: Suchergebnisse liefern `MediaItem`s, die strikt an ihre `StationUuid` gebunden sind.

### 3. Bildstabilität (StationUuid & Coil)

#### [MODIFY] [StationIconProvider.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/utils/StationIconProvider.kt)
- **UUID-basiertes Dateisystem**: `openFile` sucht im Cache-Ordner nach `[StationUuid].jpg`.
- **Coil-Integration**: Bei Cache-Miss wird Coil verwendet, um das Bild zu laden, und das Resultat als JPEG in `station_icons` abgelegt.

### 4. Sprachsteuerung (Keyword-Resolution)

#### [MODIFY] [MediaSessionCallback.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/service/MediaSessionCallback.kt)
- **onPlayFromSearch**: Implementierung einer Schlagwort-Suche. Wenn der Nutzer "3 swr" sagt, wird die Datenbank nach Sendern durchsucht, die sowohl "3" als auch "swr" im Namen haben. Der beste Treffer liefert die `StationUuid` für den Wiedergabestart.

## Verifizierungsplan

### Automatisierte Tests
- Build-Check.

### Manuelle Verifizierung
- **DHU Test**: Überprüfen der Sender-Identifikation via `StationUuid`.
- **Icon Cache Check**: Verifizierung des UUID-basierten Caches.
- **Sprachsteuerung**: Test von "3 swr" -> Auflösung zu "SWR3" -> Start Stream via `StationUuid`.
