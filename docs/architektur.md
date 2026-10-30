# Architektur

## Überblick

```
Rohdaten (OSM, DEM)                       design/tokens.json
      │  prep.py, extract_osm.sh                │ app/tools/gen_tokens.py
      ▼                                         ▼
 roads.gpkg, landcover, places, DEM ──► scenic/ (Pipeline) ──► starts/grid-v*/pNNN
                                              │ scenic.grid_build
                                              ▼
                        webapp/public/grid/v1, build/grid-nav/v1, webapp/public/data, nav/v1
                                              │
            ┌─────────────────────────────────┼──────────────────────────────┐
            ▼                                 ▼                              ▼
     Web-App (Vercel)              Vercel Blob (nav/v1, grid/v1)      Android-App (Assets + Download)
                                                                            │
                     Vercel-Navserver (Valhalla, Rejoin) ◄──────────────────┤
                     server/server.py „Runde ab Punkt“ (nur lokal/Debug) ◄──┘
```

## Komponenten

### Daten-Pipeline (`scenic/`, Python)
Baut aus OSM und Höhenmodell einen bewerteten Straßengraphen, sucht Rundkurse, charakterisiert sie und schreibt Katalog- und Navigationsdateien. Details: [daten-pipeline.md](daten-pipeline.md), [algorithmus.md](algorithmus.md). `phase2/` und `phase3/` sind Vorgänger; `phase3/cache` (130 MB) dient dem Server noch als Datenquelle (laut `deploy/hetzner`).

### Routing
Valhalla über `pyvalhalla` 3.9.1 in einer eigenen Umgebung `vh/`. Kacheln in `valhalla/` (Konfiguration `valhalla/valhalla.json`) und `offline/`. Auf dem Handy: `valhalla-mobile` 0.6.4 mit einem herunterladbaren Routing-Paket (URL, SHA-256 und Größe als `BuildConfig` in `app/androidApp/build.gradle.kts`).

### Server
- `server/server.py`: Prototyp „Runde ab Punkt“, `127.0.0.1:8765` (über `SCENIC_HOST`/`SCENIC_PORT` änderbar). Hält Graph und Valhalla im Speicher, Jobs mit Fortschritt. Nicht deployt; Hetzner-Vorlage in `deploy/hetzner/`. Im Android-Debug-Build vorbelegt (`http://10.0.2.2:8765`), im Release leer = Funktion aus.
- `app/server/navserver.py`: älteres Navigations-Backend, Port 8002 (Valhalla-Proxy plus `/scenic/loop`, `/scenic/rejoin`).
- Vercel-Navserver: Python-Funktion, Standard-Backend der App (`scenic.backend`, Default `https://scenic-routes-nav-riederstef-8388.vercel.app`). Quellcode in [`server/vercel-navserver/`](../server/vercel-navserver/README.md); die Valhalla-Kacheln (`tiles/`) sind nicht eingecheckt.

API-Details: [api.md](api.md).

### Web-App (`webapp/`)
Vite, React 19, TypeScript, Tailwind v4, shadcn/ui, MapLibre, lucide-react. Statische Daten unter `public/` (`data/` Katalog, `grid/v1` Raster, `nav/v1` Navigationsdateien, `map/` Stile). Daten werden mit `webapp/scripts/compact.py` erzeugt. Deploy auf Vercel (`vercel.json`), Produktion `scenic-routes-miltenberg.vercel.app`, Hobby-Tarif.

### Android-App (`app/`)
Kotlin Multiplatform, Gradle 9 (AGP 9.5.0, Kotlin 2.4.21, JDK 17), minSdk 29, targetSdk 36, ABIs arm64-v8a und x86_64, kein R8.
- `shared/` (common, android, jvm, ios-Ziele): Modelle, Geo, Raster, Navigationslogik (`nav/`: Einstieg, Rejoin, Off-Route, Ankunft, Kurven/Fahrstatistik, Sonne), Ortsindex, Fahrten, eigene Routen, SQLDelight 2.4.1 (Schema 1–4 in `sqldelight/databases`).
- `androidApp/`: Compose Material 3, Ferrostar 0.59.0 (Turn-by-Turn), MapLibre Compose 0.13.0, Lucide-Icons (Material-Icons per Gradle-Task `checkNoMaterialIcons` verboten), Fahrtaufzeichnung als Vordergrunddienst, Updater.
- Assets (`src/main/assets`, 51 MB): `loops.json`, `routes/` (342 Navigationsdateien), `places.tsv` (Ortsindex), `map/` (Kartenstile).
- Remote-Daten: `nav/v1` und `grid/v1` vom Vercel-Blob-Store, `version.json` von `scenic-routes-app-download.vercel.app`.

### Design-Tokens
`design/tokens.json` ist die einzige Quelle für Farben, Typo, Maße und Kartenfarben. `app/tools/gen_tokens.py` erzeugt `Tokens.kt`, `webapp/src/tokens.css`, `webapp/src/lib/terms.ts`, `webapp/src/lib/mapTheme.ts`, die Kartenstile und `colors_tokens.xml`; `--check` prüft Aktualität und WCAG-Kontraste. Generierte Dateien nicht von Hand ändern.

## Bekannte Abweichungen
- `design/DESIGN.md` beschreibt noch die Bronze-Palette; gültig ist seit 30.10. die Palette in `tokens.json` (#1A1A2E, #16213E, #0F3460, #EFC07B).
- `app/server/navserver.py` und Vercel-Navserver überschneiden sich; welcher langfristig bleibt, ist offen.
