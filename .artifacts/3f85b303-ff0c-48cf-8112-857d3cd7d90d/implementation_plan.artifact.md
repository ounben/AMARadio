# Implementation Plan: Fix Widget Visibility & Metadata

Dieses Update behebt das Problem, dass die AMARadio-Widgets nicht in der Widget-Auswahl des Launchers angezeigt werden. Wir optimieren die XML-Metadaten und die Manifest-Registrierung.

## User Review Required

> [!IMPORTANT]
> Wir reduzieren die Mindestanforderungen an den Platzbedarf (`minWidth`), damit die Widgets auf allen Launchern (auch bei großen Gittern) sicher gelistet werden.

## Proposed Changes

### 1. Metadaten-Optimierung (XML)

#### [MODIFY] [small_widget_info.xml](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/res/xml/small_widget_info.xml)
- Reduzierung von `minWidth` auf `160dp` (~2-3 Zellen).
- Reduzierung von `minHeight` auf `40dp` (1 Zelle).
- Beibehaltung von `targetCellWidth="4"`.

#### [MODIFY] [full_widget_info.xml](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/res/xml/full_widget_info.xml)
- Reduzierung von `minWidth` auf `180dp`.
- Reduzierung von `minHeight` auf `120dp` (~2-3 Zellen).

### 2. Manifest-Anpassung

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/AndroidManifest.xml)
- Hinzufügen von `android:icon="@mipmap/ic_elgato_launcher"` zu beiden `receiver`-Tags.
- Sicherstellen, dass die `label` korrekt gesetzt sind.

### 3. Widget-Code (State-Definition)

#### [MODIFY] [AMARadioSmallWidget.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/widget/AMARadioSmallWidget.kt) / [AMARadioFullWidget.kt](file:///C:/Users/bou/StudioProjects/AMARadio/app/src/main/kotlin/com/ounben/amaradio/widget/AMARadioFullWidget.kt)
- Explizites Überschreiben von `stateDefinition = PreferencesGlanceStateDefinition`.

## Verification Plan

### Manual Verification
1. App neu installieren.
2. Widget-Picker öffnen.
3. Suchen nach "AMARadio" -> Es müssen nun zwei Widgets ("AMARadio Compact" und "AMARadio Player") erscheinen.
4. Widget zum Home-Screen hinzufügen und Funktion prüfen.
