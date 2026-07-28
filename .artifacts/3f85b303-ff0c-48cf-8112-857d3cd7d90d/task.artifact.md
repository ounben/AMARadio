# Task-Liste: Entfernung JSON-Fallback & Umstellung auf SQL

- `[x]` **1. Bereinigung Infrastruktur**
    - `[x]` `FallbackStationsManager.kt` löschen
    - `[x]` `fallback_stations.json` löschen
    - `[x]` `AMARadioApp.kt` bereinigen (Entfernung Member & Init)
- `[x]` **2. Programmlogik Anpassung**
    - `[x]` `PlayerViewModel.kt` Fallback-Logik auf SQL umstellen
    - `[x]` `PlayerService.kt` Initial-Station auf SQL umstellen (bei leerer Historie)
- `[x]` **3. Verifizierung**
    - `[x]` Kompilierung prüfen
    - `[x]` Erststart-Szenario testen
