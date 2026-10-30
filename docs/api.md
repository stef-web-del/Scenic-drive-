# API

Alle Antworten JSON (UTF-8). Keine Authentifizierung. Fehlertexte sind deutsch.

## 1. „Runde ab Punkt“ – `server/server.py`

Standard `http://127.0.0.1:8765` (`SCENIC_HOST`, `SCENIC_PORT`). Nicht öffentlich deployt. Für Betrieb hinter Reverse-Proxy mit TLS: `deploy/hetzner/`.

| Methode | Pfad | Body / Query | Antwort |
|---|---|---|---|
| GET | `/health` | – | `{ok, warm_s, jobs}` |
| POST | `/jobs` | `{lat, lon, name?, minuten?, profil?, via?}` | 202 `{id, cached:false}`; gleicher Auftrag aus dem Cache: 200 `{id, cached:true}` |
| GET | `/jobs/{id}` | `?offset&limit` | Job-Ansicht (siehe unten) |
| GET | `/jobs/{id}/loops/{lid}` | – | `{id, coords, anfahrt, rueckweg}` |
| POST | `/jobs/{id}/loops/{lid}/nav` | – | Navi-Datei (Ferrostar/OSRM-JSON); 500 `Navi fehlgeschlagen`; 404 bei unbekannter Runde |
| DELETE | `/jobs/{id}` | – | `{id, cancel:true}` |
| sonst | | | 404 `{error:'nicht gefunden'}` |

`profil`: `sportlich` (Default), `geniesser`, `entdecker`. `minuten`: Wunschdauer (Default 45), Fenster `(t−10, t+5)`, t auf 30–90 begrenzt; bei 45 also 35–50. `via`: Wunschziel als `{lat, lon}`, erreicht bei ≤ 2 km. Laut Docstring liefert POST `{id}`; der Code ergänzt `cached`.

**Job-Ansicht:** `status, final, start, ziel, profil, via, verworfen, marks, progress, entries, first_s, count, offset, more, loops, error`. Seitengröße 6.

**Felder einer Runde in `loops`:** `id, min, anfahrt_min, anfahrt_km, rueckweg_min, rueckweg_km, start_abstand_m, einstieg, km, gesamt_min, hm, score, tour, bstrasse, ort, aussicht, tags, text, max_ueberschneidung, gefunden_s, ersetzt, via_m, an_rueck_ueberlappung, rang`.

**Abweichung:** Die Docstring von `GET /jobs/{id}/loops/{lid}` nennt ein Höhenprofil, der Code liefert keins.

Ein Beispiel-Ablauf:
```bash
curl -s -XPOST localhost:8765/jobs -d '{"lat":49.7067,"lon":9.2641,"profil":"sportlich"}'
curl -s 'localhost:8765/jobs/<id>?offset=0&limit=6'
curl -s -XPOST localhost:8765/jobs/<id>/loops/<lid>/nav > nav.json
```

## 2. Navigations-Backend

Zwei Implementierungen mit gleicher Schnittstelle:
- `app/server/navserver.py` (lokal, Port 8002; Umgebungsvariablen `SCENIC_ROOT`, `VALHALLA_CONFIG`, `SCENIC_DATA`)
- Vercel-Funktion (Default-Backend der App). Quelltext liegt außerhalb des Projektordners, siehe [architektur.md](architektur.md).

| Methode | Pfad | Inhalt |
|---|---|---|
| GET | `/health` | Status; Vercel zusätzlich `area`, `bbox`, `initS`, `uptimeS` |
| GET | `/scenic/loop/<id>` | `{route, waypoints}` einer Katalogrunde |
| POST | `/scenic/rejoin` | Rückführung auf die Runde |
| POST | `/route`, `/trace_route`, `/locate`, `/height` | Valhalla-Durchreichung |

Vercel-Funktion zusätzlich: nur Koordinaten in der Bbox `8.50,49.21,10.03,50.20`, max. 25 Orte (Shape 5000), Body ≤ 1 MB (413), Ratenlimit 60 Anfragen / 5 min je IP und Instanz (429), CORS offen, Fehler 400 `{error}`.

## 3. Statische Daten

| URL | Inhalt |
|---|---|
| `https://wqofepnfttvlzsvq.public.blob.vercel-storage.com/nav/v1/<id>.json` | Navigationsdatei je Runde |
| `…/grid/v1/index.json`, `…/grid/v1/pNNN.json` | Raster: Punkte bzw. Runden je Punkt mit `sha` je Navi-Datei |
| `…/routing/miltenberg-car-v1.tar.gz` | Offline-Routing-Paket (SHA-256 in `build.gradle.kts`) |
| `https://scenic-routes-app-download.vercel.app/version.json` | Update-Info: `versionCode, versionName, apkUrl, sha256, minSupported, …` |
| `https://scenic-routes-app-download.vercel.app/app.apk` | 307 auf das GitHub-Release-Asset |

Status Blob-Store: Laut Logbuch war er am 29.10. gesperrt („Your store is blocked“). Ob `nav/v1`, `grid/v1` und das Routing-Paket heute wieder ausgeliefert werden, habe ich nicht geprüft: **offen**.
