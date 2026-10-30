# Parität Web ↔ Android (Stand M2, 28.10.2026)

Quelle der Wahrheit: `design/tokens.json` → generiert `webapp/src/tokens.css` (`--sr-*`) und `shared/.../Tokens.kt` (`app/tools/gen_tokens.py`).
Begriffe: `webapp/src/lib/terms.ts` ⇔ `Tokens.Terms` (Kotlin). Reihenfolge der Detailansicht ist im Web kanonisch.

| Bereich | Web | Android | Status |
|---|---|---|---|
| Farben/Radien (hell + dunkel) | shadcn-Variablen auf `--sr-*` gemappt | M3 ColorScheme aus Tokens | ✅ gleich |
| Kopf: Score-Punkt, Titel, „Runde N“, Klasse | ✅ | ✅ | ✅ |
| 6 Kennzahlen: Länge (x,y km), Fahrzeit (min), Anstieg (Hm), Score, Tour-Score, Region | ✅ | ✅ | ✅ |
| Stärken-Chips, Tags, Beschreibung | ✅ | ✅ | ✅ |
| Losfahren: Google Maps / In der App navigieren (+ Nav-Treue %) | Menü | Dropdown | ✅ |
| GPX | Download | Teilen (FileProvider) | ✅ (plattformtypisch) |
| Link teilen | ✅ | – | offen |
| Offline speichern (Route + Ansagen + Kartenregion) | – (Web braucht Netz) | ✅ SQLDelight + MapLibre-Offline-Region | nur App |
| „Warum diese Runde?“: Beiträge mit Ø-Markern, Anteil Highlight/normal/Transfer, 9 BARS, beste Abschnitte | ✅ | ✅ | ✅ |
| Karte nach Abschnitt färben, Abschnitt hervorheben | ✅ | ✅ | ✅ |
| Höhenprofil, Pausenorte, Orte, Varianten | ✅ | ✅ | ✅ |
| Updates | – | version.json, 1×/Tag + „Nach Updates suchen“ | nur App |

Tests: Web `npm run predeploy` (Vitest, Playwright Flows/Visual/axe); Android `:shared:jvmTest` (7+8), `:androidApp:testDebugUnitTest` (Logic 4, UI 21, Roborazzi hell/dunkel → `docs/screenshots`).
