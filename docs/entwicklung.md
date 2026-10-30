# Entwicklung und Tests

## Voraussetzungen

| Bereich | Werkzeuge |
|---|---|
| Pipeline/Server | Python 3 mit `venv/` (Pipeline) und `vh/` (pyvalhalla 3.9.1); GDAL/osmium über conda (`tools/`) |
| Web | Node.js + npm (Version nicht festgelegt: **offen**) |
| Android | JDK 17, Android SDK (Pfad in `app/local.properties`, lokal, nicht im Repo), Gradle Wrapper im Repo |
| navsim | Rust/Cargo (`app/tools/navsim`) |

## Web

```bash
cd webapp
npm ci
npm run dev            # Vite-Entwicklungsserver
npm run build          # tsc + vite build
npm run lint           # ESLint + scripts/check-tokens.mjs
npm run typecheck
npm test               # Vitest (125 Unit-Tests, Stand 30.10.)
npx playwright test    # 44 Tests inkl. axe (e2e/flows, a11y, visual)
npm run test:lighthouse
npm run test:all       # lint, typecheck, coverage, Playwright
```
`npm run predeploy` führt `test:all` aus und bricht bei Fehlern ab. `webapp/patches/vaul+1.1.2.patch` wird per `patch-package` (postinstall) angewendet.

## Android

```bash
cd app
./gradlew :androidApp:assembleDebug
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
```
Teststand 30.10.: Android 133, shared jvm 99, androidHost 49, alle grün. Screenshot-Tests mit Roborazzi 1.77.0. `checkNoMaterialIcons` läuft vor jedem Build (nur Lucide-Icons erlaubt). SQLDelight prüft Migrationen (`verifyMigrations`, Schemas 1–4).

Debug-Build: „Runde ab Punkt“-Server auf `http://10.0.2.2:8765` vorbelegt. Nav-Backend über Gradle-Property `scenic.backend` überschreibbar. Release-Build: siehe [release.md](release.md).

## Pipeline und Server

```bash
venv/bin/python -m pytest tests/            # test_scenic, test_navpoints, test_referenzen
venv/bin/python -m pytest tests/ -m "not slow"
venv/bin/python -m server.server [--port 8765]   # laut Docstring und deploy/hetzner
```
`test_referenzen` (23 Referenzfälle) ist als `slow` markiert (~1 min). Die Tests brauchen die lokalen Daten (Graph, DEM); ohne sie laufen sie nicht: **offen**, ob Testdaten-Fixtures ins Repo sollen.

## Design-Tokens

```bash
python3 app/tools/gen_tokens.py          # erzeugt alle Plattformdateien
python3 app/tools/gen_tokens.py --check  # Aktualität + WCAG-Kontraste
```
Nur `design/tokens.json` ändern.

## Navigation simulieren

`app/tools/navsim` (Rust) fährt den echten Ferrostar-Kern mit 9 Szenarien ab, `--strict` für harte Prüfung. Ergebnisse als CSV im Ordner. Beschreibung: `app/docs/offline-sim.md`, `app/docs/entry-sim.md`, Parität Web/App: `app/docs/parity.md`.

## CI

Nicht eingerichtet. Vorlage: `ci/github-actions.example.yml` (pytest, Tokens-Check, Web-Lint/Test/Build, Gradle-Tests). `webapp/.github/workflows/test.yml` existiert, wird von GitHub aber nicht ausgeführt, weil sie nicht im Repo-Root liegt.

## Regeln

- Generierte Dateien (Tokens, `public/nav`, Assets) nicht von Hand ändern.
- Vor größeren Änderungen wurde bisher eine Sicherung angelegt (`backup_*`); im Repo ersetzt Git das.
- Material-Icons sind verboten, Lucide ist Pflicht (App und Web).
