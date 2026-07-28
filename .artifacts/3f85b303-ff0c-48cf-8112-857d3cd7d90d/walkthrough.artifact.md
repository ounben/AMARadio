# Entfernung JSON-Fallback & Umstellung auf SQL

Ich habe den alten "JSON-Bullshit" entfernt und die App auf eine rein SQL-basierte Initialisierung umgestellt.

## Wichtigste Änderungen

### 1. Bereinigung der Infrastruktur
- **Gelöscht**: `FallbackStationsManager.kt` wurde vollständig aus dem Projekt entfernt.
- **Gelöscht**: `fallback_stations.json` (die Ressource mit den fest kodierten Sendern) wurde gelöscht.
- **Bereinigt**: `AMARadioApp.kt` wurde von allen Referenzen auf den Fallback-Manager befreit.

### 2. Dynamische Initialisierung (SQL-Power)
- **PlayerService**: Wenn die App zum ersten Mal startet (oder Verlauf/Favoriten leer sind), fragt der Service nun asynchron die lokale SQL-Datenbank ab. Er ermittelt den populärsten Sender für den aktuellen Standort (`countryCode`) und setzt diesen als Start-Station.
- **PlayerViewModel**: Die Fallback-Kette im UI wurde ebenfalls bereinigt. Sie zeigt nun konsistent den Zustand des `PlayerService` an.

### 3. Vorteile
- **Keine veralteten Daten**: Keine fest kodierten polnischen Sender mehr beim Erststart.
- **Lokalität**: Nutzer in der Schweiz erhalten einen Schweizer Top-Sender, Nutzer in Deutschland einen deutschen – automatisch und ohne JSON-Abfragen.
- **Sauberer Code**: Komplette Entfernung einer unnötigen Abstraktionsschicht.

## Verifizierung
- [x] Projekt kompiliert fehlerfrei nach der Löschung.
- [x] `PlayerService` lädt Initial-Station nun direkt via Room/SQL.
- [x] Alle Referenzen auf `fallback_stations.json` wurden entfernt.

Die App ist nun deutlich schlanker und nutzt die vorhandene lokale Datenbank für einen intelligenten Erststart.
