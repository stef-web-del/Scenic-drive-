# Offline-Simulation (lokales Routing, Auto-Kacheln)

Router-Reihenfolge: lokal → Server (hier absichtlich nicht erreichbar).

**20/20 bestanden**

| Runde | Szenario | ok | Ergebnis |
|---|---|---|---|
| 7241 | Einstieg 1 km daneben | ✓ | Anfahrt 1.8 km via lokal, 25 ms |
| 7241 | Einstieg 10 km daneben | ✓ | Anfahrt 18.0 km via lokal, 12 ms |
| 7241 | 400 m Abweichung | ✓ | Wiedereinstieg bei km 16.3, 0.3 km ausgelassen, Ziel = Rundenziel (0 m) |
| 7241 | Sperrung 150 m voraus | ✓ | Umleitung meidet Sperrpunkt (min. 148 m), Wiedereinstieg 1.9 km dahinter |
| 1547 | Einstieg 1 km daneben | ✓ | Anfahrt 0.5 km via lokal, 13 ms |
| 1547 | Einstieg 10 km daneben | ✓ | Anfahrt 11.4 km via lokal, 9 ms |
| 1547 | 400 m Abweichung | ✓ | Wiedereinstieg bei km 15.3, 0.3 km ausgelassen, Ziel = Rundenziel (0 m) |
| 1547 | Sperrung 150 m voraus | ✓ | Umleitung meidet Sperrpunkt (min. 104 m), Wiedereinstieg 0.4 km dahinter |
| 3895 | Einstieg 1 km daneben | ✓ | Anfahrt 10.8 km via lokal, 10 ms |
| 3895 | Einstieg 10 km daneben | ✓ | Anfahrt 16.1 km via lokal, 7 ms |
| 3895 | 400 m Abweichung | ✓ | Wiedereinstieg bei km 16.5, 0.3 km ausgelassen, Ziel = Rundenziel (0 m) |
| 3895 | Sperrung 150 m voraus | ✓ | Umleitung meidet Sperrpunkt (min. 54 m), Wiedereinstieg 0.4 km dahinter |
| 6339 | Einstieg 1 km daneben | ✓ | Anfahrt 1.1 km via lokal, 3 ms |
| 6339 | Einstieg 10 km daneben | ✓ | Anfahrt 14.1 km via lokal, 6 ms |
| 6339 | 400 m Abweichung | ✓ | Wiedereinstieg bei km 16.4, 0.3 km ausgelassen, Ziel = Rundenziel (0 m) |
| 6339 | Sperrung 150 m voraus | ✓ | Umleitung meidet Sperrpunkt (min. 149 m), Wiedereinstieg 1.9 km dahinter |
| 477 | Einstieg 1 km daneben | ✓ | Anfahrt 1.1 km via lokal, 4 ms |
| 477 | Einstieg 10 km daneben | ✓ | Anfahrt 14.8 km via lokal, 7 ms |
| 477 | 400 m Abweichung | ✓ | Wiedereinstieg bei km 15.0, 0.3 km ausgelassen, Ziel = Rundenziel (0 m) |
| 477 | Sperrung 150 m voraus | ✓ | Umleitung meidet Sperrpunkt (min. 150 m), Wiedereinstieg 1.9 km dahinter |
