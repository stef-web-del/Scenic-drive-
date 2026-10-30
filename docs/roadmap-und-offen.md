# Roadmap und Offenes

## Offene Entscheidungen (Stefan)

- Lizenz des Projektcodes (keine LICENSE-Datei vorhanden)
- Welche Teile ins öffentliche Repo kommen (Vorschlag: [_repo-inhalt.md](_repo-inhalt.md)), v. a. Screenshots (49 MB) und `webapp/public/nav` (51 MB)
- Veröffentlichung 0.6.4 (gebaut, nicht freigegeben)
- Hosting „Runde ab Punkt“ (Hetzner vorbereitet; Konto und Maschinengröße offen)
- CI einrichten (Vorlage `ci/`)

## Technisch offen

- Vercel-Navserver: Code liegt in `server/vercel-navserver/`; das Bauverfahren der Valhalla-Kacheln (Filter, Split) ist nicht dokumentiert.
- Blob-Store: am 29.10. gesperrt; aktueller Status von `nav/v1`, `grid/v1` und Routing-Paket nicht geprüft.
- `version.json`: lokaler Pflegeort nicht gefunden.
- Pipeline: Reihenfolge der Vorverarbeitung (`prep.py`, `extract_osm.sh`) nicht dokumentiert; kein Lockfile für `venv`; Pfade teils fest auf `/workspace/scenic`.
- `DEM_PRIORITY`: Auswertungslogik nicht geprüft.
- `starts/grid-v2`: Zweck/Status nicht dokumentiert.
- Server: Docstring nennt Höhenprofil in `GET /jobs/{id}/loops/{lid}`, Code liefert keins.
- `scenic/profiles.py` hat Default-Fenster 45–60 min, gültig sind 35–50.
- Freie Runden leben nur im Speicher; nach App-Neustart muss die Navi-Datei neu geholt werden.
- navsim-Grenzfälle: Obernburg Nr. 5 „Abkürzung“ (Banner 124 s veraltet).
- Tests der Pipeline brauchen lokale Großdaten.
- Doku-Altlasten: `webapp/README.md` nennt noch 45–60 min. Web-CI-Workflow liegt unter `webapp/.github/` und läuft daher nicht.
- UI-Audit 0.6.4: 9 offene Punkte und Geräteprüfungen (Sonne, Handschuhe, echte Karte, TalkBack, 200 % Schrift), siehe `app/docs/design/UI-AUDIT-0.6.4.md`; Teile davon hat Stefan inzwischen entschieden ([entscheidungen.md](entscheidungen.md)).

## Roadmap (aus dem Logbuch, ohne Termine)

- A→B-Routen
- Konten und Synchronisierung (lokale Datenbank ist mit IDs, Zeitstempeln und Löschkennzeichen vorbereitet; gesendet wird nichts)
- virtuelle Garage
- Offline-Suche auf dem Handy messen
- iOS (Ziele in `shared` angelegt, keine App)
- Tests im WLAN/am Gerät
