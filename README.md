# Scenic Routes

Schöne Autorunden (Rundkurse) im Raum Miltenberg: berechnet aus OSM- und Höhendaten, bewertet nach Kurven, Relief, Aussicht und Ortsanteil, ausgeliefert als Web-App und Android-App mit Turn-by-Turn-Navigation.

Stand: 30.10.2026. App-Version im Code 0.6.4 (versionCode 13, unveröffentlicht); zuletzt veröffentlicht 0.6.3 (versionCode 12).

**Releases:** Die Android-APKs liegen unter [GitHub Releases](https://github.com/stef-web-del/Scenic-drive-/releases) (aktuell v0.6.3). Download und Update-Prüfung laufen über `https://scenic-routes-app-download.vercel.app/app.apk` und `/version.json`. Bis 30.10.2026 enthielt dieses Repository nur Release-Dateien; seitdem liegt hier auch der Quellcode.

## Bestandteile

| Ordner | Inhalt | Technik |
|---|---|---|
| `scenic/` | Daten-Pipeline: Graph, Bewertung, Rundensuche, Fahrstile, Raster | Python (venv) |
| `server/` | Prototyp „Runde ab Punkt“ (HTTP, Port 8765) | Python, Valhalla über `vh/` |
| `webapp/` | Web-App, Produktion `scenic-routes-miltenberg.vercel.app` | Vite, React 19, TypeScript, Tailwind v4, shadcn, MapLibre |
| `app/` | Android-App (Kotlin Multiplatform: `shared` + `androidApp`) | Compose M3, Ferrostar 0.59, MapLibre Compose, SQLDelight, valhalla-mobile |
| `design/` | Design-Tokens (`tokens.json`), Kartenstile, DESIGN.md | Generator `app/tools/gen_tokens.py` |
| `deploy/` | Hetzner-Vorlage (nicht ausgerollt), Download-Seite | |
| `ci/` | CI-Vorlage, nicht eingerichtet | |
| `phase2/`, `phase3/` | ältere Prototypen, vom Paket `scenic/` abgelöst | |
| `logbuch/` | Projektlogbuch mit allen Entscheidungen | |

## Schnellstart

Details in [docs/entwicklung.md](docs/entwicklung.md).

```bash
# Web
cd webapp && npm ci && npm run dev
# Android (JDK 17, Android SDK)
cd app && ./gradlew :androidApp:assembleDebug
# Pipeline-Tests
venv/bin/python -m pytest tests/
```

Die Pipeline braucht große Rohdaten (OSM, DEM), die nicht im Repo liegen; siehe [docs/daten-pipeline.md](docs/daten-pipeline.md).

## Dokumentation

- [Architektur](docs/architektur.md)
- [Algorithmus](docs/algorithmus.md)
- [API](docs/api.md)
- [Daten-Pipeline](docs/daten-pipeline.md)
- [Entwicklung und Tests](docs/entwicklung.md)
- [Release](docs/release.md)
- [Entscheidungen](docs/entscheidungen.md)
- [Roadmap und Offenes](docs/roadmap-und-offen.md)
- [Daten und Lizenzen](docs/daten-und-lizenzen.md)
- [Repo-Inhalt](docs/_repo-inhalt.md)
- [Mitarbeit](CONTRIBUTING.md)

## Lizenz

Copyright © 2026 Stefan Rieder. Alle Rechte vorbehalten.

Es gibt bewusst keine LICENSE-Datei: Der Code ist öffentlich einsehbar, aber nicht zur Nutzung, Veränderung oder Weitergabe lizenziert.

Drittdaten und ihre Lizenzen:

- OpenStreetMap-Daten: ODbL 1.0, © OpenStreetMap-Mitwirkende
- Copernicus GLO-30 DEM: Copernicus-DEM-Lizenz (frei, Namensnennung)
- DGM1 Bayern: CC BY 4.0, Bayerische Vermessungsverwaltung
- Kartenkacheln: OpenFreeMap (auf OSM-Basis)

Details: [docs/daten-und-lizenzen.md](docs/daten-und-lizenzen.md).

## Secrets

Der Release-Signaturschlüssel und seine Passwörter kommen aus Umgebungsvariablen bzw. einer lokalen Datei und liegen nicht im Repo.
