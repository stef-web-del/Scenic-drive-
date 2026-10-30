# Scenic Routes Miltenberg – Web-App

Interaktive Karte aller valide Auto-Runden (45–60 min) ab OBI Miltenberg.
Vite + React + TypeScript + Tailwind v4 + shadcn/ui (radix-nova) + MapLibre GL, statisch gehostet (Vercel Hobby).

## Daten erzeugen

Die App liest nur statische JSON-Dateien aus `public/data/`:

| Datei | Inhalt | Größe (gzip) |
|---|---|---|
| `meta.json` | Liste aller Runden (Kennzahlen, Klasse, Region, Tags, Familien je Überschneidungs-Stufe, BBox) | ~62 KB |
| `lines.json` | vereinfachte Geometrien aller Runden (35 m, 4 Nachkommastellen) | ~257 KB |
| `layers.json` | Heatmap-Kanten, Referenzrouten, Pausenorte | ~83 KB |
| `r/s<id % 32>.json` | Details (Geometrie 8 m, Höhenprofil, Pausen, Orte, Google-Maps-Wegpunkte), 32 Shards | je ~100 KB |

```bash
cd /workspace/scenic
venv/bin/python phase3/char_all.py      # Voraussetzung: cache/char_all.pkl (Charakterisierung aller Runden)
venv/bin/python phase3/export_web.py    # schreibt webapp/public/data/*
```

## Entwickeln / bauen

```bash
cd webapp
npm install
npm run dev        # http://localhost:5173
npm run build      # -> dist/
npx vite preview   # dist lokal testen
```

`vercel.json` setzt Cache-Header (Assets immutable, Daten 1 h). Deploy: Inhalt von `dist/` + `vercel.json` als statisches Projekt.

## Aufbau

- `src/lib/data.ts` – Typen, Loader (lazy, Shard-Cache), Farben, GPX-Export, Google-Maps-Link
- `src/lib/state.ts` – kompletter UI-Zustand im URL-Hash (`kl, rg, t, s, rep, lvl, r, sort, ebenen`) → teilbare Links
- `src/components/MapView.tsx` – MapLibre-Karte (OpenFreeMap Positron/Dark), Ebenen, Auswahl, Tap-Toleranz
- `src/components/Filters.tsx`, `LoopList.tsx`, `LoopDetail.tsx` – shadcn-Komponenten
- `src/App.tsx` – Layout: Desktop-Sidebar (Tabs), Mobil Vollbildkarte + Bottom-Sheet (vaul) + Filter-Sheet

Karte © OpenStreetMap-Mitwirkende, OpenFreeMap, OpenMapTiles; Höhen: Copernicus DEM.

## Daten & Performance (Okt 2026)
- `scripts/compact.py` erzeugt aus `phase3/out/web_raw` kompakte Daten in `public/data`: `meta.json` (nur Familienvertreter, 52 KB), `lines.bin` (Vertreter-Geometrie, int16-Delta, 90 KB), lazy `all.json` + `lines-all.bin` (Varianten/Expertenmodus), Detailtexte in `r/sN.json`.
- Dekodierung der Linien im Web Worker (`src/lib/lines.worker.ts`).
- Standardansicht: Familienvertreter bei 25 % Überschneidung (regelbar 15–70 %); „Varianten dieser Runde anzeigen“ bzw. „Experten: alle Runden“ laden den Rest nach.
- Karte: statisches Vorschaubild (`public/preview-*.webp`), MapLibre lädt auf Mobil bei erster Interaktion / Rundenauswahl, auf Desktop sobald der Browser idle ist.
- API/CLI: `find_loops(..., max_results=10)` bzw. `--max-results/-n` (Default 10) liefern nur Familienvertreter.

## Score-Aufschlüsselung (Datenformat, auch für die Android-App)

Erzeugen: `venv/bin/python phase3/score_parts.py` (schreibt `phase3/cache/parts.pkl`), danach `venv/bin/python webapp/scripts/compact.py`.
Alle Werte stammen aus `character.analyze()`, nichts ist geschätzt. Straßenbelag wird nicht bewertet (kein Score-Anteil, keine Kennzahl); unbefestigte Wege (OSM `surface` unpaved/gravel/…, `highway=track` ohne Belag, schlechte `smoothness`) sind im Routing hart ausgeschlossen (`scenic/features.py`). Reines JSON, kein NaN.

**Detail-Shard `public/data/r/sN.json` → `<id>.sb`**
- `c`: additive Beiträge zum Tour-Score; Summe = `t` (±0,001 Rundung).
  `route` = 0,40·Strecken-Score, `topk` = 0,25·Mittel der 3 besten Abschnitte, `flow` = 0,15·(1−Verbindungsanteil),
  `dram` = 0,10·Dramaturgie, `view` = 0,10·min(1, 4·Aussichtsanteil), `interrupts` = −0,01·min(10, Unterbrechungen/10 km).
- `v`: Rohwerte. `kurv` mittlere Kurvigkeit 0–1 · `kehr` enge Kurven °/km · `rhy` Rhythmus 0–1 (1 − Streuung der Kurvigkeit je Abschnitt/0,4) ·
  `rel` Relief 0–1 · `hmkm` Hm/km · `aus` Anteil mit freier Sicht · `fern` Sichtweite m · `wald`/`offen` Landschaftsanteile ·
  `ort` Anteil innerorts · `ortn` Ortseinfahrten · `b` Anteil Bundesstraße ·
  `hl`/`tr` Highlight-/Verbindungsanteil · `unt` Unterbrechungen je 10 km · `amp` Ampeln/Stopps · `dram` Dramaturgie 0–1 · `top` Mittel Top-3-Abschnitte.
- `p`: Perzentil 0–100 über alle 342 Runden = Anteil der Runden, die in dieser Kennzahl *schlechter* sind
  (bei `ort`, `ortn`, `b`, `tr`, `unt`, `amp` ist niedriger besser; `wald`/`offen` ohne Perzentil).
- `sg`: Abschnitte in Fahrtrichtung `[km-Start, Länge m, Abschnitts-Score, Art "h"|"n"|"t" (Highlight/normal/Verbindung), Straßenname, Steigung %]`.
  Geometrie: aus `g` per kumulativer Distanz schneiden, skaliert auf Σ Länge (siehe `sliceGeom` in `src/lib/score.ts`).

**`meta.json`/`all.json`**: je Runde `st` = bis zu 3 Stärken (Kennzahl-Schlüssel mit Perzentil ≥ 75); global `sbm` = `{avg, med, c (Ø Beiträge), n}`.
Labels und Erklärtexte: `src/lib/score.ts`.

## Tests

Komplettes Frontend-Testsystem (alles lokal/kostenlos, kein Vercel-Bezahlfeature):

| Ebene | Werkzeug | Dateien |
|---|---|---|
| Unit/Komponenten | Vitest + React Testing Library + jsdom | `src/**/*.test.ts(x)`, Setup `src/test/` |
| E2E (Desktop Chrome, iPhone 390×844, Pixel 7) | Playwright | `e2e/flows.spec.ts` |
| Barrierefreiheit (WCAG 2.1 AA) | axe-core via `@axe-core/playwright` | `e2e/a11y.spec.ts` |
| Visuelle Regression | Playwright `toHaveScreenshot` (Karte ausgeblendet) | `e2e/visual.spec.ts`, Baselines `e2e/__screenshots__/` |
| Performance-Budgets | Lighthouse CI (mobil, Performance ≥ 90) | `lighthouserc.json` |
| Statisch | ESLint, `tsc -b` | `eslint.config.js`, `tsconfig*.json` |

```bash
npm test                 # Unit/Komponenten
npm run test:watch       # Watch-Modus
npm run test:coverage    # mit Coverage (Schwellen: Lines/Statements 80 %, Functions 75 %, Branches 70 %) → coverage/
npm run test:e2e         # E2E-Flows (baut + startet lokal `vite preview` auf :4173)
npm run test:a11y        # axe
npm run test:visual      # Screenshot-Vergleich; neue Baselines: npm run test:visual:update
npm run test:lighthouse  # Lighthouse CI gegen dist/ (vorher npm run build)
npm run test:all         # lint + typecheck + coverage + alle Playwright-Projekte
npm run predeploy        # test:all + build – vor jedem Deploy

BASE_URL=https://scenic-routes-miltenberg.vercel.app npx playwright test e2e/flows.spec.ts   # gegen Production
```

E2E-Kernflüsse: Start zeigt 9 Repräsentanten · Runde öffnen · Varianten anzeigen · 50 % Überschneidung → 41 Runden ·
Expertenansicht → 1306 Runden · Attribution sichtbar (auch mobiles Vorschaubild) · keine Konsolenfehler · GPX-Download.
Jeder Playwright-Test schlägt bei `console.error`/`pageerror` fehl (`e2e/fixtures.ts`).

Hinweise:
- Kein manuell gestartetes `vite preview` auf Port 4173 laufen lassen – Playwright würde es lokal wiederverwenden.
- Screenshot-Baselines sind plattformabhängig (Fonts); CI nutzt das Playwright-Docker-Image. Bei Abweichungen Baselines im selben Image neu erzeugen.
- `patches/vaul+1.1.2.patch` (über `patch-package` im `postinstall`) reicht `modal={false}` an Radix durch; sonst wird `<main>` mobil per `aria-hidden` versteckt.
- Berichte: `playwright-report/index.html`, `coverage/index.html`, `.lighthouseci/`.
- CI: Vorlagen `webapp/.github/workflows/test.yml` (GitHub Actions) und `webapp/.gitlab-ci.yml`. GitHub führt nur Workflows unter `.github/workflows/` im Repo-Root aus; im Monorepo ist die Datei daher derzeit inaktiv. Siehe `ci/github-actions.example.yml`.
