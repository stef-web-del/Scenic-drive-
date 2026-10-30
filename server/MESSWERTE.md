# Messwerte Prototyp „Runde ab Punkt“ (29.10.2026, lokal, 8 Kerne, Server warm)

| Startpunkt | erster Vorschlag | gesamt | verschiedene Runden (≤25 % Überschneidung) | gültige Kandidaten | vorher (generate) |
|---|---|---|---|---|---|
| Klingenberg, Suchbaum kalt | 3,9 s | 10,9 s | 3 | 12 | 72 s, 4 verschiedene |
| Klingenberg, Suchbaum im Cache | 2,0 s | 9,7 s | 3 | 12 | – |
| Bürgstadt (49,7067 / 9,2641) | 5,0 s | 27,6 s | 8 (Seite 1: 6, „mehr“, Seite 2: 2) | 456 | 7 Familien |
| OBI Miltenberg | 3,7 s | 14,1 s | 4 | 157 | – |
| Obernburg an der B469 (schwierig) | 4,1 s | 11,8 s | 1 | 19 | – |

Wiederholte Anfrage aus dem Ergebnis-Cache: 0,012 s. Navi-Datei für eine Runde (Bürgstadt): 0,1 s, 104 KB, 39,9 km, 37 min, 29 Schritte.
Abbruch per DELETE: Status „cancelled“ nach 3,2 s. Server lauscht nur auf 127.0.0.1:8765. Aufwärmen beim Start: etwa 6 s.
Rohdaten: runs1.jsonl, Navi-Beispiel: nav_buergstadt.json.

## 29.10.2026, nach Anfahrt-Logik und Zweistufen-Suche

Gemessen mit `/tmp/mess/c2.py` gegen 127.0.0.1:8765, Rohdaten in `runs3.jsonl`. Reine Runde 35–50 min, Anfahrt und Rückweg getrennt.

| Startpunkt | Erster Vorschlag vorher | nachher | Gesamt vorher | nachher | Runden vorher | nachher |
|---|---|---|---|---|---|---|
| Klingenberg | 3,9 s | 2,3 s (Baum im Cache) | 10,9 s | 27,4 s | 3 | 11 |
| Bürgstadt | 5,0 s | 3,7 s | 27,6 s | 23,5 s | 8 | 12 |
| OBI Miltenberg | 3,7 s | 3,2 s | 14,1 s | 21,3 s | 4 | 12 |
| Obernburg (B469) | 4,1 s | 3,5 s | 11,8 s | 25,7 s | 1 | 8 |

Die Gesamtzeit steigt, weil jetzt zusätzlich drei Einstiege abseits von Ort und Bundesstraße durchsucht werden. Der erste Vorschlag kommt trotzdem früher, weil die Suche ab dem Startpunkt zuerst läuft. Anfahrt am Ortsrand 0,1–6 min, über einen Einstieg 4,8–10,3 min. Ergebnis aus dem Cache 0,012 s, Navi-Datei mit Anfahrt und Rückweg (Bürgstadt) 0,1 s, 115 KB, 48,9 km. Abbruch greift nach 3,2 s.
