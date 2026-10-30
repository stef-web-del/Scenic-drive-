# Mitarbeit

## Einstieg

1. [README.md](README.md) und [docs/architektur.md](docs/architektur.md) lesen.
2. Umgebung nach [docs/entwicklung.md](docs/entwicklung.md) aufsetzen.
3. Vor Änderungen am Verhalten [docs/entscheidungen.md](docs/entscheidungen.md) prüfen: Entscheidungen trifft der Projektinhaber.

## Regeln

- **Sprache:** Oberfläche, Fehlertexte, Doku und Logbuch auf Deutsch. Zahlen im deutschen Format (Dezimalkomma).
- **Design:** Farben, Abstände, Radien, Typo nur über `design/tokens.json` und `python3 app/tools/gen_tokens.py`. Keine festen Farbwerte im Code (`npm run lint` und `check-tokens.mjs` prüfen das im Web). Nur Lucide-Icons (`checkNoMaterialIcons`).
- **Generierte Dateien** nicht von Hand ändern: `Tokens.kt`, `tokens.css`, `terms.ts`, `mapTheme.ts`, Kartenstile, `colors_tokens.xml`, `public/nav`, `public/grid`, App-Assets.
- **Datenbank:** Schemaänderungen nur mit neuer SQLDelight-Migration und Schemaabzug; `verifyMigrations` muss grün sein. Hintergrund: Absturz 0.5.0 (siehe Logbuch).
- **Navigation:** Änderungen an `shared/nav` mit `navsim --strict` gegen alle Katalogrunden prüfen.
- **Tests:** vor jedem Merge pytest, Web `npm run test:all`, Gradle `:shared:jvmTest :androidApp:testDebugUnitTest`.
- **Secrets:** Signaturschlüssel, Passwörter und Tokens kommen aus Umgebungsvariablen bzw. lokalen Dateien und werden nie eingecheckt, geloggt oder in Issues gepostet.
- **Releases** nur mit Freigabe des Projektinhabers ([docs/release.md](docs/release.md)).
- **Logbuch:** Größere Änderungen als Eintrag in Prosa in `logbuch/LOGBUCH.md` mit Datum und Uhrzeit (Europe/Berlin).

## Commits und Pull Requests

Konventionen sind noch nicht festgelegt (**offen**). Bis dahin: kleine, thematisch geschlossene Commits mit deutscher Beschreibung; im PR Testergebnisse und bei UI-Änderungen Bildschirmfotos hell/dunkel.
