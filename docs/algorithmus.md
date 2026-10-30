# Algorithmus

Quelle: `scenic/profiles.py`, `scenic/ranking.py`, `scenic/generate.py`, `scenic/settings.py`, `server/server.py`, Logbuch. Werte sind Code-Stand 30.10.2026.

## 1. Kantenbewertung

Jede Straßenkante bekommt einen Score aus positiven Gewichten und Abzügen (`Params`, Profil `auto_sport`):

| Positiv | Gewicht | Abzug | Wert |
|---|---|---|---|
| Kurvigkeit `w_curv` | 0,35 | Stadt `pen_town` | 0,45 |
| Relief `w_relief` | 0,20 | Dorf `pen_village` | 0,15 |
| Kantenqualität `w_edge` | 0,12 | Gebäude `pen_bldg` | 0,25 |
| Aussicht `w_view` | 0,10 | Bundesstraße `pen_bstrasse` | 0,35 |
| Offenland `w_open` | 0,08 | trunk `pen_trunk` | 0,60 |
| Wald `w_forest` | 0,05 | Wohnstraße 0,30, track 0,50, minor 0,12, schmal 0,06, Kehre 0,08 |

`w_section` = 0,5 mischt Abschnitts- und Kantenscore. Enge Kehren (r < 40 m) zählen mit `hairpin_factor` 0,5. Profil `motorrad` setzt andere Werte (w_curv 0,45, w_relief 0,15, hairpin_factor 0,8, …), wird aber laut Entscheidung vom 29.10. nicht fahrzeugspezifisch genutzt.

Höhenmodell: Reihenfolge laut `scenic/settings.py` (`DEM_PRIORITY`): `dem/dgm10_by.vrt`, `dem/dgm1_by_miltenberg.vrt`, `dem/glo30_bbox.tif`. GLO-30 ist ein DSM (Wald/Gebäude enthalten). Wie die Liste genau ausgewertet wird (erste vorhandene Datei oder Überlagerung), habe ich nicht geprüft: **offen**.

## 2. Rundensuche (Generator)

`scenic.generate`: Anker in 3–16 km Abstand (Abstand 1200 m, bis 160 Anker), Kombination zu Runden (`--kombis` 20000), Reparatur der besten (`--repair-top` 3000), parallel (`--procs` 6).

Harte Regeln:
- keine Stichfahrt (`no_backtrack`): keine Straße doppelt oder in Gegenrichtung, keine Wende; Ausnahme Startzone 1500 m mit max. 2000 m doppelter Länge
- doppelt gefahrene Länge ≤ 8 % (`max_overlap`)
- Bundesstraße/primary/trunk ≤ 12 % (`max_b_share`)
- Ortsdurchfahrt ≤ 25 % (`max_town_share`)
- Mindestscore 0,15
- Überschneidung zwischen ausgegebenen Runden ≤ 25 % (längengewichteter Jaccard, `max_jaccard`)

Highlights: Abschnitte ≥ 1500 m mit Mittelscore ≥ 0,30, Lücken bis 300 m werden überbrückt.

Dauer: Zielfenster der reinen Runde 35–50 min, gemessen mit Valhalla. Die Graph-Fahrzeit liegt etwa 14 % über Valhalla; daher Kalibrierung (Server `KAL` = 0,864). Hinweis: `scenic/profiles.py` hat noch die Defaults 45–60 min, `generate` den Default `--minutes 45 --tol 5`; maßgeblich für Raster ist `plan.json` (`window`, Default 35–50 in `grid_build`).

## 3. Charakterisierung und Klassen

`scenic/character.analyze` liefert u. a. `tour_score`, `curv_mean`, `rhythm_mean`, `relief_mean`, `view_frac`, `ascent_m`, `town_share`, `b_share`, `highlight_share`, `interrupts_per10km`, Orte, Pausen, Text und Tags. Klassen in `generate`: Grenzen 0,15/0,18/0,21/0,24 → C, B, A, A+. Score-Aufschlüsselung in der Web-App (laut `webapp/README.md`): route 0,40, topk 0,25, flow 0,15, dram 0,10, view 0,10, Unterbrechungen −0,01 je Einheit.

## 4. Fahrstile (Ranking)

`scenic/ranking.py`: Kennzahlen werden je Start als Perzentil (0..1) normiert. Gesucht wird einmal, der Stil bestimmt nur die Reihenfolge. Je Start und Stil bleiben bis zu 20 Runden (KEEP), 6 werden gezeigt (TOP); ab Rang 7 bekommt jede Runde einen Grund, warum sie schwächer ist.

| Stil | Gewichte |
|---|---|
| Sportlich | Kurven 0,30, Rhythmus 0,15, Relief 0,15, Highlights 0,15, wenig Unterbrechungen 0,10, Gesamt 0,15 |
| Genießer | Aussicht 0,25, Landschaft 0,15, ortsfrei 0,20, Einkehr 0,10, Kurven 0,10, Gesamt 0,20 |
| Entdecker | Vielfalt (Orte) 0,25, Aussicht 0,20, Höhenmeter 0,15, Einkehr 0,10, Highlights 0,10, Gesamt 0,20 |

## 5. „Runde ab Punkt“ (Server)

`server/server.py`, Konstanten: Zielfenster 35–50 min (`ZIEL`), Überschneidung 0,25 (`OV`), Seite 6 (`PAGE`), max. 12 Runden (`MAX_LOOPS`), Zeitbudget 90 s, `KAL` 0,864. Mit Wunschdauer: Fenster `(t−10, t+5)`, t auf 30–90 begrenzt. Wunschziel (`via`) gilt als erreicht, wenn die Runde in ≤ 2000 m vorbeiführt (`VIA_M`).

Zweistufige Suche: erst ab Startpunkt bis zur ersten Runde, dann bis zu 3 Einstiege (schöne Straße außerhalb Ort/Bundesstraße, 3–14 min vom Start, ≥ 3 km voneinander). Anfahrt und Rückweg werden getrennt ausgewiesen; Kennzahlen kommen nur aus der reinen Runde. Überlappung Anfahrt/Rückweg: weich 0,3, hart 0,6, `AR_KEEP` 3. Gezeigte Plätze bleiben stabil; eine bessere ähnliche Runde ersetzt die gezeigte auf demselben Platz (`ersetzt`). Messwerte: `server/MESSWERTE.md` (erster Vorschlag 2–5 s, ganze Suche 10–28 s warm).

## 6. Navigation (App)

Ferrostar schaltet einen Schritt nur weiter, wenn ein Fix ≤ 30 m vor dessen Ende liegt; deshalb eigene Logik in `shared/nav` (StepSync, OffRouteMonitor/-Policy, Rejoin, LoopEntry, ArrivalDetector). Geprüft mit `app/tools/navsim` (Rust, echter Ferrostar-Kern, 9 Szenarien). Kurvenzählung (`DriveStats.kt`, Referenz `app/tools/curves/curves.py`): Schritt 10 m, Radius 120 m, Kurve ab 30°, Kehre ab 135°, Abbiegen an Knoten (±25 m) zählt nicht.
