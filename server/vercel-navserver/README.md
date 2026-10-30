# Nav-Backend auf Vercel

Python-Funktion (`api/index.py`) mit pyvalhalla 3.9.1, kompatibel zu `app/server/navserver.py`. Ist das Default-Backend der App (`scenic-routes-nav-riederstef-8388.vercel.app`). Grenzen: Testgebiet-Bbox lon 8,50–10,03 / lat 49,21–50,20, max. 25 Orte, 1 MB Body, 60 Anfragen je 5 min je IP und Instanz.

| Datei | Inhalt |
|---|---|
| `api/index.py` | HTTP-Handler, Prüfungen, Valhalla-Aufrufe |
| `api/osrm_merge.py` | Zusammenführen von OSRM-Antworten |
| `api/valhalla.json` | Valhalla-Konfiguration (`tile_dir` wird zur Laufzeit gesetzt) |
| `data/loops.json` | Rundenkatalog für `/scenic/rejoin` |
| `vercel.json` | Funktion, `includeFiles`, Rewrite aller Pfade auf `api/index` |

## Nicht im Repo: `tiles/`

Die Valhalla-Kacheln (53 Dateien, ≈ 99 MB, aus OpenStreetMap, ODbL) sind generiert und nicht eingecheckt. Große Kacheln liegen als `*.partN` gesplittet vor; `prep_tiles()` setzt sie beim Kaltstart in `/tmp/tiles` zusammen. Erzeugt wurden sie mit `valhalla_build_tiles` aus einem auf das Testgebiet und Autostraßen gefilterten OSM-Extrakt. Das genaue Filter- und Split-Verfahren ist nicht dokumentiert (**offen**). Für ein Deployment muss `tiles/` neben `api/` liegen.

## Deploy

Vercel-Projekt mit diesem Ordner als Root (`vercel deploy`). Zugangsdaten nur lokal bzw. aus Umgebungsvariablen, `.vercel/` wird nicht eingecheckt.
