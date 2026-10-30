# Daten, Quellen und Lizenzen

Dieser Text war bisher die Root-README (Phase 1). Inhalt unverändert übernommen, nur gegliedert.

## Gebiet

Bbox: `8.50,49.21,10.03,50.20` (50 km um Miltenberg Marktplatz 49.7030/9.2575 plus etwa 5 km Puffer). Dieselbe Bbox prüfen der Nav-Server (`app/server/navserver.py`) und die Vercel-Funktion (Fehler „außerhalb des Testgebiets Miltenberg“).

## Quellen

| Quelle | Bezug | Lizenz |
|---|---|---|
| OpenStreetMap über Geofabrik (Unterfranken, Hessen komplett, RB Karlsruhe, RB Stuttgart), Stand 27.10.2026 | `raw/*.osm.pbf`, Ausschnitt `miltenberg_bbox.osm.pbf` | ODbL 1.0 |
| Copernicus GLO-30 DEM (AWS Open Data) | `dem/glo30_bbox.tif` | Copernicus-DEM-Lizenz, frei inkl. kommerziell, Namensnennung. Achtung: DSM (inkl. Wald und Gebäude) |
| DGM1 Bayern (geodaten.bayern.de OpenData, Metalink je Landkreis) | `dem/` | CC BY 4.0, Quelle: Bayerische Vermessungsverwaltung |
| Kartenkacheln/Stile OpenFreeMap (Web und App) | Laufzeit | siehe openfreemap.org; Namensnennung OSM nötig. Genaue Bedingungen: **offen, prüfen** |

## Software (Pipeline)

Python venv (geopandas BSD, shapely BSD, pyproj MIT, rasterio BSD, osmnx MIT, pyrosm MIT, networkx BSD, scipy/numpy BSD, matplotlib PSF-like), osmium-tool GPLv3/Boost, GDAL MIT (via micromamba/conda-forge, BSD), Valhalla MIT (pip pyvalhalla 3.9.1).

App- und Web-Abhängigkeiten: siehe `app/gradle/libs.versions.toml` und `webapp/package.json`. Eine vollständige Drittlizenz-Liste (NOTICE) gibt es noch nicht: **offen**.

## Betrieb auf einem Hetzner-Server (vorbereitet, nicht ausgerollt)

Im Ordner `deploy/hetzner/` liegt alles, was für einen eigenen Server nötig ist. Ein Konto gibt es noch nicht, ausgerollt ist nichts.

- `Dockerfile` baut ein schlankes Image mit dem Server und einer eigenen Umgebung für Valhalla. Die Daten (`phase3/cache`, `places.gpkg`, `valhalla/`, zusammen gut 250 MB) gehören als Volume nach `/srv/scenic`, nicht ins Image.
- `scenic-server.service` ist die systemd-Unit für den Betrieb ohne Docker, mit eingeschränkten Rechten und Neustart bei Fehlern.
- `scenic.env.example` ist die Vorlage für `/etc/scenic/scenic.env`. Der Server liest `SCENIC_HOST` und `SCENIC_PORT`; Standard bleibt `127.0.0.1:8765`. Öffentlich erreichbar sollte er nur über einen Reverse-Proxy mit TLS sein.
- `requirements.txt` hält die Python-Pakete fest, ohne Test- und Kartenwerkzeuge.

Offen ist die Größe der Maschine. Für den Raum Miltenberg reichen nach den Messungen 2 vCPU und 4 GB RAM. Für größere Gebiete braucht es mehr Arbeitsspeicher, wegen der Valhalla-Kacheln.
