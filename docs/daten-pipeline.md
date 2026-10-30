# Daten-Pipeline

Python-Umgebungen:
- `venv/` (882 MB): Pipeline (`scenic/`), Tests
- `vh/` (436 MB): pyvalhalla 3.9.1 für Routing, Navi-Dateien, Server-Worker
- `tools/` (495 MB): micromamba/conda mit GDAL, osmium
Keine davon gehört ins Repo. Eine Lock-Datei für `venv` gibt es nicht; nur `deploy/hetzner/requirements.txt` für den Server: **offen**.

## Schritte

1. **Rohdaten** nach `raw/` (Geofabrik-PBF, Stand 27.10.2026), DEM nach `dem/`. Quellen: [daten-und-lizenzen.md](daten-und-lizenzen.md).
2. **Ausschnitt und Extraktion:** `extract_osm.sh`, `prep.py` → `miltenberg_bbox.osm.pbf`, `osm/*`, `roads.gpkg`, `landcover_osm.gpkg`, `places.gpkg`, `viewpoints.gpkg`, `building_density_250m.tif`. Genaue Reihenfolge und Aufrufe sind nicht dokumentiert: **offen**, aus den Skripten nachvollziehen.
3. **Valhalla-Kacheln** in `valhalla/` (`valhalla.json`), Offline-Paket in `offline/`.
4. **Runden je Start:**
   ```bash
   venv/bin/python -m scenic.generate --start 49.7067,9.2641 --minutes 45 --name "McDonald's Bürgstadt" --out starts/buergstadt-mcd-45
   ```
   Suche und Bewertung, Charakterisierung, Rohdaten/GPX/summary.json, Navi über `vh/` (`scenic/generate_nav.py`), `webapp/scripts/compact.py`, `overview.png`. Optionen u. a. `--profil`, `-n`, `--ueberschneidung`, `--keep` (Runden je Fahrstil), `--light` (Raster, ohne GPX/Bild), `--skip-nav`.
5. **Raster:** Punkte in `starts/grid-v1/plan.json`, je Punkt `pNNN/`. Zusammenfassen:
   ```bash
   venv/bin/python -m scenic.grid_build --src starts/grid-v1 --web webapp/public/grid/v1 --nav build/grid-nav/v1
   ```
   Runden außerhalb des Fensters (`window`, Default 35–50) werden verworfen, `sha` je Navi-Datei eingetragen. `starts/grid-v2` (104 MB) existiert ebenfalls; ihr Status ist im Logbuch nicht beschrieben: **offen**.
6. **Katalog der App:** `vh/bin/python app/tools/build_nav.py` (→ `webapp/public/nav/v1`), dann `vh/bin/python app/tools/build_assets.py` (→ `app/androidApp/src/main/assets/loops.json`, `routes/`). Ortsindex: `app/tools/build_places.py` (→ `places.tsv`, 11.533 Orte, 60 km).
7. **Hochladen** der `nav/v1`/`grid/v1`-Dateien in den Blob-Store: kein Skript im Projekt gefunden, bisher manuell: **offen**.

Weitere CLI: `scenic/cli.py` (Profile `auto_sport`/`motorrad`, `--min/--max/-n/--ueberschneidung/--set`). Pfade sind teils fest auf `/workspace/scenic` gesetzt (z. B. `app/tools/build_nav.py`, `curves.py`); für andere Rechner anpassen.

## Startpunkt-Defaults

`scenic/settings.py`: Start OBI Miltenberg 49.7009613/9.2248586, Radius 30 km.
