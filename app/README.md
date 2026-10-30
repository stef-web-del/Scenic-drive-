# Scenic Routes – Android-App

Stand 30.10.2026: Version 0.6.4 (versionCode 13) gebaut, nicht veröffentlicht. Zuletzt veröffentlicht: 0.6.3 (12). Releases: GitHub `stef-web-del/Scenic-drive-`, siehe [../docs/release.md](../docs/release.md).

Kotlin Multiplatform: `shared` enthält Modelle, Geo- und Navigationslogik, Ortsindex und Datenbank; `androidApp` die App. iOS-Ziele sind in `shared` angelegt, eine iOS-App gibt es nicht.

## Technik

| | |
|---|---|
| Build | Gradle Wrapper, AGP 9.5.0, Kotlin 2.4.21, JDK 17 |
| Android | minSdk 29, targetSdk 36, ABIs arm64-v8a, x86_64, kein R8 |
| UI | Compose Material 3 (BOM 2026.09.00), Lucide-Icons (`icons-lucide-cmp` 2.2.1); Material-Icons verbietet `checkNoMaterialIcons` |
| Karte | MapLibre Compose 0.13.0, Stile aus `design/map` (OpenFreeMap) |
| Navigation | Ferrostar 0.59.0 plus eigene Logik in `shared/nav` |
| Routing offline | valhalla-mobile 0.6.4, Routing-Paket per Download (URL/SHA-256 in `build.gradle.kts`) |
| Daten | SQLDelight 2.4.1, Schemas 1–4, `verifyMigrations` |
| Tests | JUnit, Robolectric, Roborazzi 1.77.0 |

## Funktionen

Vier Tabs: **Planen** („Runde planen“), **Entdecken** (Katalog ab Miltenberg / „Rund um dich“), **Meine Routen** (inkl. GPX-Import), **Fahrten** (Aufzeichnung, Fahrtenbuch). Dazu Einstellungen mit Unterseiten (Ansagen & Stimme, Karte & Ansicht, Fahrleiste, Fahrten & Aufzeichnung, Offline-Daten, Server, App & Info).

- **Runde ab hier:** Start über Ortssuche (Offline-Index `places.tsv`, tippfehlertolerant), Standort oder Karte. Runden vom nächsten Rasterpunkt (`grid/v1`) mit Fahrstil Sportlich, Genießer oder Entdecker.
- **Freie Runden:** Suche auf dem „Runde ab Punkt“-Server (`server/server.py`). Nur im Debug-Build vorbelegt (`http://10.0.2.2:8765`), im Release aus. Gespeicherte Runden leben bis zum App-Neustart.
- **Navigation:** Ferrostar mit eigenem Sprecher (TTS, Audio-Ducking), Einstieg mitten in der Runde, Rückführung bei Abweichung über das Nav-Backend (`/scenic/rejoin`), Ankunftserkennung (< 50 m, ≥ 95 % der Runde, ca. 10 s Stillstand), Fahrleiste mit einstellbaren KPIs (Fortschritt, Tempo, verbleibende Kurven, Höhe, Höhenmeter, g-Werte), Wechsel auf dunkles Thema nach Sonnenuntergang.
- **Aufzeichnung:** Vordergrunddienst, jederzeit startbar, Wiederaufnahme nach Abbruch, GPX-Export.
- **Updates:** `version.json` von `scenic-routes-app-download.vercel.app`; Download per DownloadManager, SHA-256-Prüfung.
- **Quellenangabe:** „© OpenStreetMap-Mitwirkende (ODbL)“ auf jeder Karte; Datenquellen in den Einstellungen.

## Aufbau

```
shared/src/commonMain/kotlin/de/scenicroutes/shared/
  model/ geo/ grid/ nav/ freeloop/ places/ routes/ trip/ offline/ community/
androidApp/src/main/kotlin/de/scenicroutes/app/
  ui/ (Screens, Tokens.kt – generiert)  nav/ (NavigationScreen, NavViewModel, NavSpeech, DriveBackGuard)
  data/ (Remote, Updater, OfflineRouting, FreeLoops, Places …)  rec/ (Aufzeichnung)
androidApp/src/main/assets/  loops.json, routes/ (342 Runden), places.tsv, map/
server/navserver.py          altes lokales Nav-Backend (Port 8002)
tools/                       build_assets.py, build_nav.py, build_places.py, gen_tokens.py, curves/, navsim/ (Rust)
docs/                        parity.md, offline-sim.md, entry-sim.md, design/UI-AUDIT-0.6.4.md, screenshots/
```

## Bauen und testen

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
./gradlew :androidApp:assembleRelease     # braucht den lokalen Signaturschlüssel
```
Das SDK steht in `local.properties` (lokal, nicht im Repo). Nav-Backend beim Bauen setzen: `-Pscenic.backend=https://…`; Default ist der Vercel-Navserver. Der Release-Schlüssel kommt aus Umgebungsvariablen/lokaler Datei und liegt nicht im Repo; ohne ihn entsteht kein signierter Release-Build.

Teststand 30.10.: Android 133, shared jvm 99, androidHost 49, alle grün.

Assets neu erzeugen (aus dem Projektroot, Valhalla-Umgebung `vh/`):
```bash
vh/bin/python app/tools/build_nav.py
vh/bin/python app/tools/build_assets.py
python3 app/tools/gen_tokens.py
```
Die Skripte haben feste Pfade auf `/workspace/scenic`.

## Sicherheit

Klartext-HTTP nur im Debug-Build (10.0.2.2, 127.0.0.1, localhost); Release nur HTTPS.

## Offen

- Gerätetests: Fahrmodus am echten Gerät (Sonne, Handschuhe, TalkBack, 200 % Schrift), siehe UI-Audit.
- Entscheidungen vom 30.10. (Zurück mit Rückfrage, hellere Rahmen, Abstandsstufen, Vollbild-Karte): Umsetzungsstand siehe [../docs/entscheidungen.md](../docs/entscheidungen.md).
- Konten/Synchronisierung, virtuelle Garage, iOS.
