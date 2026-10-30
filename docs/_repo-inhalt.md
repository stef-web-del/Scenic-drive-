# Repo-Inhalt (Vorschlag)

Stand 30.10.2026, Größen per `du -sh` im Projektordner `/workspace/scenic`. Ziel-Repo: `stef-web-del/Scenic-drive-` (öffentlich). Hochgeladen ist noch nichts.

## Gehört ins Repo (≈ 66 MB)

| Pfad | Größe | Hinweis |
|---|---|---|
| `README.md`, `CONTRIBUTING.md`, `.gitignore`, `docs/` | < 100 KB | |
| `scenic/` | 360 KB | ohne `__pycache__` |
| `server/` | 220 KB | inkl. `MESSWERTE.md`, Messläufe `runs*.jsonl` |
| `tests/`, `pytest.ini` | 100 KB | |
| `prep.py`, `overview.py`, `extract_osm.sh` | klein | |
| `phase2/`, `phase3/` ohne `cache/` | 44 KB + 80 KB | historische Prototypen |
| `design/` | 36 KB | Tokens, Kartenstile, DESIGN.md |
| `ci/`, `deploy/hetzner/` | 32 KB | `scenic.env.example` ist nur eine Vorlage |
| `logbuch/` | 76 KB | |
| App: Gradle-Dateien, Wrapper, `gradle/` | ~100 KB | |
| `app/shared/src` | 588 KB | |
| `app/androidApp/src` ohne Assets | ~1 MB | main/kotlin, res, test, debug |
| `app/androidApp/src/main/assets` | 51 MB | `routes/` 47 MB, `loops.json` 3,5 MB, `places.tsv` 564 KB, `map/`; für den Build nötig |
| `app/server/`, `app/tools/*.py`, `app/tools/curves/curves.py` | ~60 KB | |
| `app/tools/navsim` ohne `target/` | ~0,7 MB | Cargo-Dateien, `src`, `fixtures`, Ergebnis-CSV |
| `app/docs/*.md`, `app/docs/design/` | < 100 KB | |
| `webapp/` Quellen: `src`, `e2e`, `scripts`, `patches`, Konfigurationen, `package*.json` | ~2 MB | `e2e` 1,1 MB inkl. Referenzbildern |
| `webapp/public` ohne `nav/` | ~6,5 MB | `data` 4,4 MB, `grid` 1,8 MB, `map`, Favicon, Vorschaubilder |

## Entscheidung nötig

| Pfad | Größe | Empfehlung |
|---|---|---|
| `app/docs/screenshots/` | 49 MB | weglassen oder Git LFS; Doku verweist darauf |
| `webapp/public/nav/` | 51 MB | generiert (`build_nav.py`), Inhalt deckungsgleich mit `assets/routes`; weglassen, falls die Web-App aus dem Blob lädt (nicht geprüft) |
| `deploy/index.html` | 4,5 MB | Download-Seite; nur aufnehmen, wenn die Seite aus dem Repo deployt wird |
| `webapp/screenshots/` | 5,9 MB | weglassen |
| Vercel-Navserver (`server/vercel-navserver/`) | Code ≈ 24 KB, `data/loops.json` 1,9 MB | übernommen; `tiles/` (≈ 99 MB) nicht |

## Gehört nicht ins Repo

| Pfad | Größe | Grund |
|---|---|---|
| `venv/` | 882 MB | Umgebung |
| `tools/` (conda) | 495 MB | Umgebung |
| `vh/` | 436 MB | Umgebung |
| `webapp/node_modules/` | 575 MB | Abhängigkeiten |
| `raw/` | 781 MB | OSM-Rohdaten (Geofabrik) |
| `osm/` | 1,3 GB | abgeleitet |
| `offline/` | 481 MB | Routing-Pakete |
| `dem/` | 373 MB | Höhendaten |
| `valhalla/` | 241 MB | Kacheln |
| `starts/` | 202 MB | Generator-Ausgaben |
| `phase3/cache/` | 130 MB | Cache |
| `bin/` | 19 MB | Binärdateien |
| `roads.gpkg` 203 MB, `landcover_osm.gpkg` 164 MB, `miltenberg_bbox.osm.pbf` 214 MB, `places.gpkg` 18 MB, `viewpoints.gpkg`, `building_density_250m.tif` | ~600 MB | abgeleitete Daten |
| `overview.png` | 3,1 MB | generiert |
| `*.log` (`routing_*.log`) | klein | Logs |
| `app/build/`, `app/androidApp/build/`, `app/.gradle/` | APKs 105–145 MB | Build-Ausgaben |
| `app/dist/` | 243 MB | APKs (Releases über GitHub) |
| `app/tools/navsim/target/` | 345 MB | Rust-Build |
| `webapp/dist/`, `playwright-report/`, `test-results/` | 60 MB, 516 KB | Build/Testausgaben |
| `app-navdebug/` | 60 MB | Arbeitskopie |
| `backup_*` | ~2,6 GB | Sicherungen |
| `app/local.properties` | klein | lokaler SDK-Pfad |
| `app/keys/` | 16 KB | Signaturschlüssel |

Gesamtgröße Vorschlag: etwa 66 MB (inkl. `deploy/index.html`); mit Screenshots und `public/nav` 166 MB, 1358 Dateien. Geprüft per `git ls-files -o --exclude-standard` mit dieser `.gitignore` in einem temporären Git-Verzeichnis außerhalb des Projekts: keine Datei aus `keys/`, kein `*.jks`, keine `keystore.properties`, keine `local.properties` in der Liste.

## Fundstellen von Secrets (nur Pfade, Inhalte nicht gelesen)

- `app/keys/keystore.properties`
- `app/keys/scenic-release.jks`
- `app-navdebug/keys/keystore.properties`
- `app-navdebug/keys/scenic-release.jks`
- `backup_app_2910_vor_navsettings/keys/keystore.properties`, `…/scenic-release.jks`
- `backup_app_2910_vor_ortsindex/keys/keystore.properties`, `…/scenic-release.jks`
- `backup_app_2910_vor_uiredesign.tgz` (enthält `app/keys/keystore.properties` und `app/keys/scenic-release.jks`)
- `app/local.properties`, `app-navdebug/local.properties` (kein Geheimnis, aber lokal)

`app/androidApp/build.gradle.kts` liest die Schlüsseldatei nur ein und enthält selbst keine Werte. Eine Suche nach Token-Mustern (GitHub, Vercel Blob, private Schlüssel) in Quellordnern fand sonst nichts. Vor dem Push zusätzlich mit einem Secret-Scanner (z. B. gitleaks) über den Staging-Stand prüfen.
