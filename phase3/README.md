# Scenic Routes – Phase 3

Paket: `/workspace/scenic/scenic/` (Python, nur Open Data: OSM, DGM1 Bayern 10 m, Copernicus GLO-30)

| Modul | Inhalt |
|---|---|
| settings.py | Pfade, Start (OBI Miltenberg), DEM-Priorität |
| features.py | Kanten-Features aus OSM/DEM/Landcover (Cache `phase3/cache/features.gpkg`) |
| profiles.py | `Params`-Dataclass, Profile `auto_sport` (Standard) und `motorrad` |
| scoring.py | Kanten-Score, Abschnittsbildung, Abschnitts-Score (Kurvenqualität, Rhythmus, Länge) |
| graph.py | gerichteter Graph (Einbahnstraßen, Kreisverkehre), Kosten mit B-Straßen-Multiplikator |
| search.py | Dijkstra-Bäume, Ankerkombinationen, Reparatur mit Reuse-Strafe, Metriken, Clustering |
| character.py | Highlight/Transfer-Segmente, Top-k, Dramaturgie, Flow-Unterbrechungen, Viewshed, Pausen, Tags, Text |
| api.py | `find_loops(start, profile, n, diversity, **params)` → Runden mit Metriken |
| valhalla_check.py | Fahrzeit-Gegencheck (pyvalhalla in `vh/`) |
| export.py | GPX, GeoJSON, Höhenprofil-PNG, Karte PNG + HTML |
| cli.py | Kommandozeile |

```
venv/bin/python -m scenic.cli -n 5 --out phase3/out/cli                 # Auto sportlich, 45–60 min
venv/bin/python -m scenic.cli --profil motorrad --min 60 --max 90 --set max_b_share=0.05 hairpin_factor=0.9
venv/bin/python -m pytest -q tests                                        # 5 Tests, ca. 25 s
venv/bin/python phase3/enumerate.py && venv/bin/python phase3/analyze.py  # Vollauswertung (ca. 11 min)
venv/bin/python phase3/refcheck.py                                        # Referenzstrecken
```
Ausgaben in `phase3/out/`: runde_1..6 (.gpx/.geojson/_hoehenprofil.png), karte_runden.png/.html, korridor_heatmap.png,
verteilungen.png, richtungen.png, auswertung.json, gueltige_runden.csv, referenzen.json.

## Überschneidungs-Parameter und Karte aller Runden
- `max_jaccard` (Params, Default 0.25): max. Überschneidung zwischen ausgegebenen Runden (längengewichteter Jaccard). Nicht verwechseln mit `max_overlap` (doppelt gefahrene Länge innerhalb einer Runde).
  API: `api.find_loops(start, max_jaccard=0.35)` (alter Name `diversity=` geht weiter). CLI: `--ueberschneidung 35` bzw. `0.35` (Alias `--vielfalt`), oder `--set max_jaccard=0.35`.
- Karte: `char_all.py` (charakterisiert alle 1306 Runden → cache/char_all.pkl, ca. 1–2 min), danach `karte_alle.py` → `out/karte_alle_runden.html` (eigenständig, Leaflet eingebettet, Kacheln von OSM online). Vorlage: `karte_template.html`.
