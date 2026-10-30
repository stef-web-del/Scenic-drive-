# Einstiegs-Simulation (M3)

Erzeugt von `shared/src/jvmTest/.../EntrySimTest.kt` (`SIM_BACKEND=http://127.0.0.1:8002 ./gradlew :shared:jvmTest --tests '*EntrySim*'`).
5 Top-Runden, Startposition bei 0/25/50/80 % der Runde, jeweils 0 m, 100 m, 1 km und 10 km quer zur Fahrtrichtung versetzt.
Geprüft: genau ein depart und ein arrive, keine Ankunftsansage am Übergang (OBI), Rundenlänge unverändert.
Anfahrt: Valhalla `/route` zum Einstieg, danach mit `LoopEntry.prepend` nahtlos in die Runde.

Ergebnis: **80 / 80** Fälle bestanden.

| Runde | Position | Versatz | Luftlinie zum Einstieg | Einstieg | Straße | UI | Rundenlänge | Schritte | Prüfung | Anfahrt (Server) |
|---|---|---|---|---|---|---|---|---|---|---|
| 7241 | 0 % | 0 m | 0 m | offiz. Start | Breitendieler Straße | Direktstart | 55.9 km | 32 | ok | - |
| 7241 | 0 % | 100 m | 100 m | offiz. Start | Breitendieler Straße | Direktstart | 55.9 km | 32 | ok | - |
| 7241 | 0 % | 1000 m | 994 m | Schritt 30/Knoten 10 | Mainbullauer Straße | Einstiegsdialog | 55.9 km | 33 | ok | - |
| 7241 | 0 % | 10000 m | 2241 m | Schritt 5/Knoten 397 | Wenschdorfer Straße | Einstiegsdialog | 55.9 km | 33 | ok | - |
| 7241 | 25 % | 0 m | 0 m | Schritt 5/Knoten 285 | Wenschdorfer Straße | Direktstart | 55.9 km | 33 | ok | - |
| 7241 | 25 % | 100 m | 95 m | Schritt 5/Knoten 282 | Wenschdorfer Straße | Direktstart | 55.9 km | 33 | ok | - |
| 7241 | 25 % | 1000 m | 944 m | Schritt 5/Knoten 278 | Wenschdorfer Straße | Einstiegsdialog | 55.9 km | 33 | ok | - |
| 7241 | 25 % | 10000 m | 8985 m | Schritt 30 | Mainbullauer Straße | Einstiegsdialog | 55.9 km | 32 | ok | - |
| 7241 | 50 % | 0 m | 14 m | Schritt 13 | Lauersmühle | Direktstart | 55.9 km | 32 | ok | - |
| 7241 | 50 % | 100 m | 24 m | Schritt 12/Knoten 30 | Mühlenstraße | Direktstart | 55.9 km | 33 | ok | - |
| 7241 | 50 % | 1000 m | 686 m | Schritt 9/Knoten 107 | Geisbergstraße | Einstiegsdialog | 55.9 km | 33 | ok | - |
| 7241 | 50 % | 10000 m | 6923 m | Schritt 8/Knoten 58 | Neusasser Straße | Einstiegsdialog | 55.9 km | 33 | ok | - |
| 7241 | 80 % | 0 m | 0 m | Schritt 23/Knoten 77 | St 507 | Direktstart | 55.9 km | 33 | ok | - |
| 7241 | 80 % | 100 m | 93 m | Schritt 23/Knoten 76 | St 507 | Direktstart | 55.9 km | 33 | ok | - |
| 7241 | 80 % | 1000 m | 135 m | Schritt 23/Knoten 17 | St 507 | Direktstart | 55.9 km | 33 | ok | - |
| 7241 | 80 % | 10000 m | 6026 m | Schritt 18 | Römerstraße | Einstiegsdialog | 55.9 km | 32 | ok | - |
| 1547 | 0 % | 0 m | 0 m | offiz. Start | Breitendieler Straße | Direktstart | 56.1 km | 37 | ok | - |
| 1547 | 0 % | 100 m | 100 m | offiz. Start | Breitendieler Straße | Direktstart | 56.1 km | 37 | ok | - |
| 1547 | 0 % | 1000 m | 994 m | Schritt 35/Knoten 10 | Mainbullauer Straße | Einstiegsdialog | 56.1 km | 38 | ok | - |
| 1547 | 0 % | 10000 m | 5119 m | Schritt 10 | MIL 14 | Einstiegsdialog | 56.1 km | 37 | ok | - |
| 1547 | 25 % | 0 m | 0 m | Schritt 7/Knoten 4 | - | Direktstart | 56.1 km | 38 | ok | - |
| 1547 | 25 % | 100 m | 101 m | Schritt 7/Knoten 3 | - | Direktstart | 56.1 km | 38 | ok | - |
| 1547 | 25 % | 1000 m | 995 m | Schritt 7 | - | Einstiegsdialog | 56.1 km | 37 | ok | - |
| 1547 | 25 % | 10000 m | 9753 m | Schritt 13/Knoten 34 | Jägerstraße | Einstiegsdialog | 56.1 km | 38 | ok | - |
| 1547 | 50 % | 0 m | 0 m | Schritt 18/Knoten 37 | Vollmersdorfer Straße | Direktstart | 56.1 km | 38 | ok | - |
| 1547 | 50 % | 100 m | 100 m | Schritt 18/Knoten 37 | Vollmersdorfer Straße | Direktstart | 56.1 km | 38 | ok | - |
| 1547 | 50 % | 1000 m | 990 m | Schritt 18/Knoten 35 | Vollmersdorfer Straße | Einstiegsdialog | 56.1 km | 38 | ok | - |
| 1547 | 50 % | 10000 m | 10002 m | Schritt 18/Knoten 30 | Vollmersdorfer Straße | Einstiegsdialog | 56.1 km | 38 | ok | - |
| 1547 | 80 % | 0 m | 0 m | Schritt 28/Knoten 63 | St 507 | Direktstart | 56.1 km | 38 | ok | - |
| 1547 | 80 % | 100 m | 100 m | Schritt 28/Knoten 64 | St 507 | Direktstart | 56.1 km | 38 | ok | - |
| 1547 | 80 % | 1000 m | 620 m | Schritt 28/Knoten 26 | St 507 | Einstiegsdialog | 56.1 km | 38 | ok | - |
| 1547 | 80 % | 10000 m | 6310 m | Schritt 23 | Römerstraße | Einstiegsdialog | 56.1 km | 37 | ok | - |
| 3895 | 0 % | 0 m | 0 m | offiz. Start | Breitendieler Straße | Direktstart | 55.4 km | 32 | ok | - |
| 3895 | 0 % | 100 m | 100 m | offiz. Start | Breitendieler Straße | Direktstart | 55.4 km | 32 | ok | - |
| 3895 | 0 % | 1000 m | 995 m | Schritt 30/Knoten 11 | Mainbullauer Straße | Einstiegsdialog | 55.4 km | 33 | ok | - |
| 3895 | 0 % | 10000 m | 3120 m | Schritt 7/Knoten 125 | Reichartshausen | Einstiegsdialog | 55.4 km | 33 | ok | - |
| 3895 | 25 % | 0 m | 1 m | Schritt 6/Knoten 7 | MIL 10 | Direktstart | 55.4 km | 33 | ok | - |
| 3895 | 25 % | 100 m | 101 m | Schritt 6/Knoten 8 | MIL 10 | Direktstart | 55.4 km | 33 | ok | - |
| 3895 | 25 % | 1000 m | 568 m | Schritt 5/Knoten 267 | Wenschdorfer Straße | Einstiegsdialog | 55.4 km | 33 | ok | - |
| 3895 | 25 % | 10000 m | 5102 m | Schritt 5 | Wenschdorfer Straße | Einstiegsdialog | 55.4 km | 32 | ok | - |
| 3895 | 50 % | 0 m | 0 m | Schritt 15/Knoten 43 | Weilbacher Straße | Direktstart | 55.4 km | 33 | ok | - |
| 3895 | 50 % | 100 m | 100 m | Schritt 15/Knoten 42 | Weilbacher Straße | Direktstart | 55.4 km | 33 | ok | - |
| 3895 | 50 % | 1000 m | 201 m | Schritt 15/Knoten 15 | Weilbacher Straße | Einstiegsdialog | 55.4 km | 33 | ok | - |
| 3895 | 50 % | 10000 m | 5068 m | Schritt 6 | MIL 10 | Einstiegsdialog | 55.4 km | 32 | ok | - |
| 3895 | 80 % | 0 m | 0 m | Schritt 21/Knoten 23 | L 3349 | Direktstart | 55.4 km | 33 | ok | - |
| 3895 | 80 % | 100 m | 96 m | Schritt 21/Knoten 22 | L 3349 | Direktstart | 55.4 km | 33 | ok | - |
| 3895 | 80 % | 1000 m | 607 m | Schritt 22/Knoten 17 | K 94 | Einstiegsdialog | 55.4 km | 33 | ok | - |
| 3895 | 80 % | 10000 m | 252 m | Schritt 28/Knoten 33 | St 2441 | Einstiegsdialog | 55.4 km | 33 | ok | - |
| 6339 | 0 % | 0 m | 0 m | offiz. Start | Breitendieler Straße | Direktstart | 59.9 km | 26 | ok | - |
| 6339 | 0 % | 100 m | 100 m | offiz. Start | Breitendieler Straße | Direktstart | 59.9 km | 26 | ok | - |
| 6339 | 0 % | 1000 m | 995 m | Schritt 24/Knoten 11 | Mainbullauer Straße | Einstiegsdialog | 59.9 km | 27 | ok | - |
| 6339 | 0 % | 10000 m | 5431 m | Schritt 6/Knoten 36 | MIL 13 | Einstiegsdialog | 59.9 km | 27 | ok | - |
| 6339 | 25 % | 0 m | 0 m | Schritt 6/Knoten 36 | MIL 13 | Direktstart | 59.9 km | 27 | ok | - |
| 6339 | 25 % | 100 m | 102 m | Schritt 6/Knoten 37 | MIL 13 | Direktstart | 59.9 km | 27 | ok | - |
| 6339 | 25 % | 1000 m | 1004 m | Schritt 6/Knoten 37 | MIL 13 | Einstiegsdialog | 59.9 km | 27 | ok | - |
| 6339 | 25 % | 10000 m | 9956 m | Schritt 6/Knoten 63 | MIL 13 | Einstiegsdialog | 59.9 km | 27 | ok | - |
| 6339 | 50 % | 0 m | 0 m | Schritt 10/Knoten 123 | St 507 | Direktstart | 59.9 km | 27 | ok | - |
| 6339 | 50 % | 100 m | 96 m | Schritt 10/Knoten 122 | St 507 | Direktstart | 59.9 km | 27 | ok | - |
| 6339 | 50 % | 1000 m | 938 m | Schritt 10/Knoten 117 | St 507 | Einstiegsdialog | 59.9 km | 27 | ok | - |
| 6339 | 50 % | 10000 m | 6970 m | Schritt 14/Knoten 7 | Rauenberger Straße | Einstiegsdialog | 59.9 km | 27 | ok | - |
| 6339 | 80 % | 0 m | 0 m | Schritt 18/Knoten 119 | Maintalstraße | Direktstart | 59.9 km | 27 | ok | - |
| 6339 | 80 % | 100 m | 100 m | Schritt 18/Knoten 120 | Maintalstraße | Direktstart | 59.9 km | 27 | ok | - |
| 6339 | 80 % | 1000 m | 999 m | Schritt 18/Knoten 121 | Maintalstraße | Einstiegsdialog | 59.9 km | 27 | ok | - |
| 6339 | 80 % | 10000 m | 9155 m | Schritt 22/Knoten 78 | Martinsbrücke | Einstiegsdialog | 59.9 km | 27 | ok | - |
| 477 | 0 % | 0 m | 0 m | offiz. Start | Breitendieler Straße | Direktstart | 57.0 km | 27 | ok | - |
| 477 | 0 % | 100 m | 100 m | offiz. Start | Breitendieler Straße | Direktstart | 57.0 km | 27 | ok | - |
| 477 | 0 % | 1000 m | 128 m | Schritt 23/Knoten 46 | St 2441 | Direktstart | 57.0 km | 28 | ok | - |
| 477 | 0 % | 10000 m | 6806 m | Schritt 17/Knoten 61 | St 2441 | Einstiegsdialog | 57.0 km | 28 | ok | - |
| 477 | 25 % | 0 m | 5 m | Schritt 5/Knoten 220 | Neudorfer Straße | Direktstart | 57.0 km | 28 | ok | - |
| 477 | 25 % | 100 m | 100 m | Schritt 5/Knoten 220 | Neudorfer Straße | Direktstart | 57.0 km | 28 | ok | - |
| 477 | 25 % | 1000 m | 764 m | Schritt 5/Knoten 261 | Neudorfer Straße | Einstiegsdialog | 57.0 km | 28 | ok | - |
| 477 | 25 % | 10000 m | 7082 m | Schritt 7 | Geisenhof | Einstiegsdialog | 57.0 km | 27 | ok | - |
| 477 | 50 % | 0 m | 59 m | Schritt 13 | St 2310 | Direktstart | 57.0 km | 27 | ok | - |
| 477 | 50 % | 100 m | 34 m | Schritt 12 | St 2310 | Direktstart | 57.0 km | 27 | ok | - |
| 477 | 50 % | 1000 m | 351 m | Schritt 13/Knoten 20 | St 2310 | Einstiegsdialog | 57.0 km | 28 | ok | - |
| 477 | 50 % | 10000 m | 905 m | Schritt 16/Knoten 25 | Mönchberger Straße | Einstiegsdialog | 57.0 km | 28 | ok | - |
| 477 | 80 % | 0 m | 23 m | Schritt 17/Knoten 4 | St 2441 | Direktstart | 57.0 km | 28 | ok | - |
| 477 | 80 % | 100 m | 92 m | Schritt 17/Knoten 2 | St 2441 | Direktstart | 57.0 km | 28 | ok | - |
| 477 | 80 % | 1000 m | 926 m | Schritt 17 | St 2441 | Einstiegsdialog | 57.0 km | 27 | ok | - |
| 477 | 80 % | 10000 m | 9917 m | Schritt 17 | St 2441 | Einstiegsdialog | 57.0 km | 27 | ok | - |
