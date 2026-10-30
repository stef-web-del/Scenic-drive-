# Scenic Routes – Phase 2 Prototyp (Profil: sportliches Auto)
Ausführung (aus diesem Ordner, venv aus Phase 1):
    ../venv/bin/python build_graph.py   # ~1 min: Graph + Kantenscores -> out/edges.gpkg, out/graph.pkl
    ../venv/bin/python loops.py 1500    # ~6 min: Rundensuche (Anzahl Wegpunkt-Kombinationen) -> out/loops.json
    ../venv/bin/python refcheck.py      # Referenzstrecken: Score/Perzentil/Abdeckung -> out/refcheck.json
    ../venv/bin/python export.py        # GPX, GeoJSON, Höhenprofile, karte_runden.png/.html, summary.json
    ../venv/bin/python tune.py          # Gewichts-Sensitivität auf Referenzen
Parameter/Gewichte: config.py, Scoreformel: scoring.py. Start: OBI Miltenberg (Nominatim node/480173326, 49.70096/9.22486).
Voraussetzungen: out/smoothness.json (aus PBF via osmium extrahiert), dem/dgm10_by.vrt (DGM1 BY LK Miltenberg, auf 10 m gemittelt).
Daten: © OpenStreetMap-Mitwirkende (ODbL), Copernicus GLO-30, DGM1 © Bayerische Vermessungsverwaltung (CC BY 4.0).
