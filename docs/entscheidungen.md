# Entscheidungen

Nur Entscheidungen von Stefan (Projektinhaber). Quelle: Logbuch und Gesprächsverlauf. Vorschläge von Agenten sind keine Entscheidungen.

## Produkt (29.10.2026)

| Thema | Entscheidung |
|---|---|
| Länge | als Dauer gewählt; km nur zur Anzeige |
| Rundendauer | reine Runde 35–50 min (Valhalla-Zeit); Anfahrt/Rückweg getrennt |
| Fahrstile | Sportlich, Genießer, Entdecker (früher „Kurvenjagd“); keine Blitzerwarnungen, keine Bestzeiten |
| Anzahl | bis zu 6 Runden, „Mehr anzeigen“; sichtbar unterschiedlich (≤ 25 % Überschneidung), nicht mit Fast-Gleichen auffüllen |
| Erstes Inkrement | vorberechnete Runden im Testgebiet (Raster) |
| Offline-Suche auf dem Handy | erst messen |
| Fahrzeuge | keine Unterscheidung bei der Routenwahl; später „virtuelle Garage“ ohne Einfluss auf Routen |
| Bundesstraßen | weiter streng meiden (≤ 12 %) |
| Wunschziel | gilt als erreicht bei Vorbeifahrt in ≤ 2 km |
| Freie Runden | eigener kleiner Server (Generator dauert 72 s je Punkt) |
| Chip „schmale Straßen“ | gestrichen (Breite nur bei 7 %, Spurzahl bei 9 % der Kanten erfasst) |
| Favorit „Garage“ | heißt „Zuhause“ |

## Technik und Betrieb

| Datum | Entscheidung |
|---|---|
| 29.10. | Alte APKs m1–m6 aus dem Blob-Store gelöscht |
| 29.10. | APK-Auslieferung über GitHub Releases in `stef-web-del/Scenic-drive-` |
| 29.10. | Klartext-HTTP nur im Debug-Build |
| 29.10., 18:36 | Design Dunkelblau/Bronze freigegeben; später Lucide-Icons auch für Android |
| 29.10. | Fortschritt „Runde“ standardmäßig an, Tempo aus; neue KPIs (Kurven, Höhe, Höhenmeter, g) standardmäßig aus |
| 29.10. | Releases 0.6.0 (19:39) und 0.6.3 (21:49) freigegeben |

## Design und UI (30.10.2026)

| Thema | Entscheidung | Umsetzungsstand |
|---|---|---|
| Palette | #1A1A2E, #16213E, #0F3460, #EFC07B (ersetzt Bronze) | umgesetzt in `design/tokens.json` (0.6.4) |
| System-Zurück im Fahrmodus | Rückfrage „Navigation beenden?“ | nicht geprüft (läuft parallel) |
| Rahmenfarbe | eigene, hellere Rahmenfarbe (Audit A11) | nicht geprüft |
| Abstands-Skala | um Zwischenstufen ergänzen; Kartenhöhen flexibel (A12) | nicht geprüft |
| Zahlen in Routentexten | deutsches Format im Generator (A15) | nicht geprüft |
| Standort-Schaltfläche Web | die auf der Karte bleibt (W07) | nicht geprüft |
| Karte | Vollbild-Karte | nicht geprüft |
| Quellcode | ins öffentliche Repo `Scenic-drive-`, ohne Keystore und Secrets; erst Doku, dann Upload | Doku liegt vor, Upload nicht erfolgt |

## Offene Entscheidungen

Siehe [roadmap-und-offen.md](roadmap-und-offen.md).
