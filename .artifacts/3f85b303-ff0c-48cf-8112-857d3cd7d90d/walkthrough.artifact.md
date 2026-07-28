# Android Auto Support für AMARadio

AMARadio wurde erfolgreich für Android Auto optimiert. Die Implementierung folgt dem "Dumb Client" Prinzip, bei dem das Smartphone die Datenhoheit behält und Android Auto lediglich als Interface dient.

## Wichtigste Änderungen

### 1. Android Auto Registrierung
Die App ist nun offiziell als Media-App im System registriert.
- **Manifest**: Deklaration der Automotive-Kompatibilität und des `mediaPlayback` Foreground-Service Typs.
- **Service**: Der `PlayerService` nutzt `MediaLibraryService` (Media3) für eine nahtlose Integration in das Fahrzeug-System.

### 2. Optimierte Navigationsstruktur
Die Menüführung im Auto wurde abgeflacht, um Ablenkungen zu minimieren:
- **Favoriten & Verlauf**: Direkt auf der obersten Ebene zugänglich.
- **Lokal**: Basiert auf dem Standort-Code des Smartphones (keine eigenständige Ortung im Auto).
- **Filter-Tabs**: Deine persönlichen Filter-Einstellungen werden 1:1 vom Smartphone übernommen.

### 3. Intelligente Sprachsteuerung (Barrierefreiheit)
Die Sprachsuche wurde massiv verbessert und funktioniert auch ohne Android Auto (z.B. für blinde Nutzer):
- **Keyword-Suche**: Befehle wie *"Spiele 3 SWR"* werden intelligent zu "SWR3" aufgelöst.
- **UUID-Centric**: Die Identifikation erfolgt strikt über die `StationUuid`, um Fehler durch Namensgleichheiten zu vermeiden.
- **Popularität**: Bei ungenauen Befehlen (z.B. nur *"SRF"*) wird automatisch der populärste Sender gewählt.

### 4. Stabile Senderbilder (Cache-First)
- **StationUuid als Key**: Bilder werden im Cache unter `[StationUuid].jpg` gespeichert.
- **Coil-Integration**: Die App nutzt Coils effizientes Caching und exportiert Bilder bei Bedarf für Android Auto, um Datenvolumen zu sparen und Ladezeiten zu verkürzen.
- **Platzhalter**: Falls kein Logo existiert, werden konsistente farbige Platzhalter mit den Initialen des Senders angezeigt.

## Verifizierung
- [x] Browsing-Struktur verifiziert (Root-Tabs korrekt abgeflacht).
- [x] Sprachauflösung getestet (Keyword-Suche).
- [x] UUID-basierter Bild-Cache implementiert und mit Coil synchronisiert.
- [x] Driver Distraction Regeln eingehalten (keine Animationen, flache Listen).

## Bedienung im Auto
Einfach AMARadio in der App-Liste deines Autos auswählen oder per Sprache starten:
> *"Hey Google, spiele SWR3 auf AMARadio"*
