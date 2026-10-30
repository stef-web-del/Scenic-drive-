# Scenic Routes – Design-Kurzreferenz

> **Status:** Freigegeben von Stefan Rieder am 29.10.2026 (Dunkelblau/Bronze). Lucide-Icons auf Android hat er am selben Abend ebenfalls freigegeben. Am 30.10.2026 hat er die Palette Ink/Navy/Blau/Gold freigegeben; sie ersetzt Bronze (Stand App 0.6.4).
> Bis dahin nutzt die App weiter die bisherigen Farben (`semantic.legacy`). Den Vorschlag gibt es nur in den Vorschau-Screenshots (`app/docs/screenshots/vorschau-design/`) und unter `ScenicTheme(brand = true)` bzw. der CSS-Klasse `.brand`.

## Richtung
Ruhig und klar, im Stil von Tesla. Die Marke ist Dunkelblau mit Gold (Palette `#1A1A2E` · `#16213E` · `#0F3460` · `#EFC07B`), ohne Neon, Verläufe oder nennenswerte Schatten. Die Karte bekommt so viel Fläche wie möglich.

- **Dark ist das Hauptthema.** Hintergrund `navy.900` #0E1A2B, Flächen `navy.850` #16243A.
- **Light** ist ein heller Stein- bzw. Papierton (`stone.50` #F6F3EE) mit denselben Akzenten.
- **Fahrmodus:** Das Thema wechselt automatisch bei Sonnenauf- und -untergang (`component.theme.driveAuto = "sun"`). Das wird erst nach der Freigabe umgesetzt.

## Farbe
- **Genau ein Akzent: Gold.**
  - Dark: `palette.gold` #EFC07B mit Schrift `palette.ink`.
  - Light: Buttons `paletteDerived.goldDark` #8A5A14. Fläche/Akzent ist `palette.gold` #EFC07B mit Schrift `palette.ink`, Container `goldTint` #FBEBD3.
  - Gold steht für die aktive Route, die Haupt-Buttons und die Auswahl. Sonst wird es nirgends eingesetzt.
- **Statusfarben** gibt es nur für Fehler, Warnung und Erfolg (`status.*`).
- **Score- und Heatmap-Skala:** vier Stufen von Blau nach Gold (`primitive.score`: #3E5F9A, #7A8DB0, #C9A46E, #EFC07B). Sie sind farbenblindsicher, weil sie über die Helligkeit lesbar sind und nicht über einen Rot-Grün-Kontrast.
- **Kontrast:** Es gilt WCAG AA, also 4,5 für Text und 3,0 für Linien und Bedienelemente. `gen_tokens.py` prüft alle Paare und bricht bei einem Verstoß ab.

## Karte
Der Stil ist aus den Tokens generiert: `design/map/scenic-{light,dark}.json`, OpenMapTiles-Schema mit OpenFreeMap-Kacheln.

| Element | Dark | Light |
|---|---|---|
| Land | navy.900 | stone.50 |
| Wald | dunkles Blaugrün #14302E | #C9D6CC |
| Straßen | entsättigt, Hauptstraßen kaum heller | weiß / stone |
| Aktive Route | Gold #EFC07B 6 dp, Kontur #0B0B16 2 dp | `goldMap` #9C6618, Kontur Ink |
| Alternativen | grau 4 dp | grau 4 dp |
| Anfahrt | gestrichelt, hell | gestrichelt, navy |

## Schrift
- **Schrift:** Geist (OFL). Sie liegt im Web über `@fontsource-variable/geist` vor und in Android unter `res/font/geist.ttf`. Zahlen werden immer mit `tabular-nums` (`tnum`) gesetzt.
- **Sechs Stufen**, jeweils Größe/Zeilenhöhe/Gewicht:

  | Stufe | Werte |
  |---|---|
  | display | 56/64/600 |
  | headline | 24/32/600 |
  | title | 18/24/600 |
  | body | 15/22/400 |
  | label | 13/18/500 |
  | caption | 12/16/400 |

- **Fahrmodus:** Abbiegepfeil 72, Distanz 56, ETA 24.

## Maße
- **Tippziele:** mindestens 48 dp, im Fahrmodus 64 dp.
- **Radien:** 8, 12, 16, 28. **Abstände:** 4, 8, 12, 16, 20, 24. Andere Werte sind nicht erlaubt.
- **Icons:** Web und Android nutzen nur Lucide. Im Web kommt `lucide-react`, auf Android `com.composables:icons-lucide-cmp` 2.2.1 (MIT) als ImageVector (`Lucide.ArrowLeft` usw.). Wo das Web ein Icon schon nutzt, nimmt Android dasselbe, etwa ArrowLeft, Navigation, LocateFixed, Flag, Repeat, Coffee, Share2, Download, Info und X. Material-Icons sind gesperrt: Der Gradle-Task `checkNoMaterialIcons` hängt an `preBuild` und bricht bei `androidx.compose.material.icons` oder `Icons.Outlined…` ab.

## Tokens und Generator
`design/tokens.json` ist die einzige Quelle. Sie hat drei Stufen:

1. **`primitive`:** rohe Paletten (`palette`, `paletteDerived`, status, map, score; navy, bronze, stone sind Altbestand und nicht mehr in den Rollen verwendet).
2. **`semantic`:** Rollen wie primary, surface, onSurface und die Kartenrollen. `semantic.brand` verweist auf Primitive (z. B. `"palette.gold"`). `semantic.legacy` sind die heutigen Werte.
3. **`component`:** Tippziele, Linienbreiten der Route, Größen im Fahrmodus, Theme-Wechsel.

Mit `python3 app/tools/gen_tokens.py` (bzw. `--check` nur für die Kontrastprüfung) werden erzeugt:
- **Android:** `ui/Tokens.kt` mit Legacy, `Tokens.Brand.*`, `Type`, `touch*` und `drive*`.
- **Web:** `webapp/src/tokens.css`. Darin stehen die `--sr-*`-Variablen; die shadcn-Variablen (`--background`, `--primary` …) zeigen unter `.brand` auf `--sr-*`.
- **Begriffe:** `webapp/src/lib/terms.ts`.
- **Karte und Score (Web):** `webapp/src/lib/mapTheme.ts`.
- **MapLibre-Stil:** `design/map/*.json`, zusätzlich als Android-Asset `assets/map/`.

Web und Android lesen dieselben Werte, 1:1.

## Regeln (gelten ab der Freigabe, mit CI-Check)
- Hex-Werte, `rgb(` und Tailwind-Standardpaletten nur in den Token-Dateien.
- Kein `style=`. Ausnahme sind dynamische Breiten über eine CSS-Variable; dafür gibt es eine ESLint-Ausnahmeliste.
- Neuer Code verwendet schon jetzt nur Tokens.
