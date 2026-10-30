# UI-Audit Scenic Routes 0.6.4 (Kandidat, versionCode 13)

Stand: 30.10.2026, Europe/Berlin. Grundlage: Palette 0.6.4 (#1A1A2E / #16213E / #0F3460, einziger Akzent #EFC07B), Dark als Hauptthema.
Geprüft: Android-App (Compose, Material 3) und Web-App (React, shadcn/Tailwind), jeweils hell und dunkel.
Veröffentlicht ist nichts. version.json, GitHub und Vercel sind unverändert.

## 1. Prüfmethode und Grenzen

- Statische Durchsicht des UI-Codes aller Screens (`androidApp/src/main/kotlin/de/scenicroutes/app/{ui,nav,rec}`, `webapp/src`).
- Screenshot-Tests (Roborazzi, Robolectric) hell/dunkel; Web per Playwright (Desktop 1440×900, Pixel 7) hell/dunkel.
- Automatische Kontrastprüfung aller Token-Paare (`app/tools/gen_tokens.py --check`).
- Web: axe-core (WCAG 2.1 AA) in 3 Viewports, E2E-Flows, ESLint, Typecheck, Build.
- **Nicht geprüft (nur am Gerät möglich):** siehe Abschnitt 6. MapLibre rendert in den Android-Tests nicht; die Karte in den Vorschaubildern ist eine Skizze. Schriftgröße 200 % wurde nicht per Screenshot getestet.

## 2. Verwendete Checkliste

| Bereich | Prüfpunkte |
|---|---|
| Material 3 | Farbrollen nur aus Theme/Tokens, M3-Komponenten, Shapes aus Tokens, Typo-Rollen |
| Android | Edge-to-Edge und Insets, Predictive Back, Schriftgröße bis 200 % (keine festen Höhen mit Text), Touch-Ziele ≥ 48 dp, im Fahrmodus ≥ 64 dp |
| WCAG 2.2 AA | Kontrast Text 4,5:1 und UI 3:1, Fokus sichtbar (2.4.7/2.4.11), TalkBack-Beschriftungen, Rollen/Zustände, Zielgröße (2.5.8), Bewegung (2.3.3) |
| Konsistenz | Abstände 4/8/12/16/20/24, Radien 8/12/16/28, Typo-Skala, Icons nur Lucide, Begriffe aus `Tokens.Terms`, deutsche Zahlen und Einheiten (km, min, Hm) |
| Zustände | gedrückt, deaktiviert, lädt, leer, Fehler |
| Fahrmodus | große Ziffern (Tokens drive*), wenige Elemente, große Ziele, keine Texteingabe während der Fahrt |
| Web | responsiv (Mobil/Desktop), Tastatur, Fokusringe, prefers-reduced-motion, aria, keine Inline-Styles/Hex-Farben |

## 3. Befunde

Schweregrade: **Hoch** = Barriere oder Bedienfehler, **Mittel** = sichtbare Inkonsistenz oder Risiko bei Schriftgröße/Barrierefreiheit, **Niedrig** = Kosmetik oder Code-Hygiene.

### 3.1 Android

| ID | Screen | Befund | Schwere | Status |
|---|---|---|---|---|
| A01 | Rundendetail, „Warum diese Runde?“ | Info-Schaltfläche nur 40 dp (unter 48 dp) | Hoch | behoben (`Tokens.touchMin`) |
| A02 | alle Screens | 137 rohe dp-Werte in Abständen und Radien, die exakt auf der Token-Skala liegen | Mittel | behoben (auf `Tokens.space*`/`radius*` umgestellt) |
| A03 | Einstieg, Planen, Aufnahme, Navigation | Feste Button-Höhen 48/52/56 dp: uneinheitlich, Text wird bei großer Schrift abgeschnitten | Mittel | behoben (`heightIn(min = touchMin)`; Losfahren im Navigationsscreen, Aufnahme-Pille und „Speichern“ nach der Aufnahme `touchDrive` 64 dp) |
| A04 | Aufnahme | Schriftgrößen als Literal (72 sp, 40 sp) | Mittel | behoben (`Tokens.driveTurn`, `Tokens.driveKpi`) |
| A05 | Planen, Ab hier, Detail, Meine Routen | Leerzeichen als Abstand zwischen Icon und Text (15 Stellen) | Mittel | behoben (`Spacer(Tokens.spaceSm)`) |
| A06 | Einstellungen › Ansagen, Auswahl-Dialoge | Zeilen ohne Radio-Semantik, doppeltes Klickziel (Zeile und RadioButton) | Mittel | behoben (`selectable(role = RadioButton)`, Zeile ≥ 48 dp) |
| A07 | Einstellungen, Startsuche | Klickbare Zeilen ohne Rolle „Schaltfläche“; Ortstreffer unter 48 dp | Niedrig | behoben (`Role.Button`, `heightIn(touchMin)`) |
| A08 | App | `enableOnBackInvokedCallback` fehlte (Predictive Back auf Android 13–15) | Niedrig | behoben (im Manifest; targetSdk 36 hat es auf Android 16 ohnehin) |
| A09 | Theme.kt | veralteter Kommentar „Dunkelblau/Bronze“ | Niedrig | behoben |
| A10 | Navigation (Fahrmodus) | System-Zurück ruft nur `popBackStack`, die Schaltfläche „Beenden“ ruft `stopAll`. Ob die Führung nach System-Zurück weiterläuft, ist im Code nicht eindeutig | Hoch | **behoben**: `DriveBackGuard` fängt System-Zurück (auch Predictive Back) ab und fragt „Navigation beenden?“. „Beenden“ ruft `stopAll`, „Weiterfahren“ schließt nur den Dialog. Test `AuditFix064Test`, Bild `nachher-2/a10-dialog-navigation-beenden-{light,dark}.png` |
| A11 | Dunkel, Karten/Chips | `outlineVariant` = #0F3460 auf Fläche #16213E: Rahmen von Karten und Chips kaum sichtbar | Mittel | **behoben**: eigene Rahmen-Tokens. Dunkel `border` #3A5F96 (2,65:1 auf #1A1A2E, 2,47:1 auf #16213E), `outline` #7F8BA8 (5,00 / 4,66 / 3,67:1 auf den drei Blautönen). Hell `border` #A5ADC1 (2,08:1), `outline` #757D94 (3,80 / 3,19:1). Bild `nachher-2/a11-rahmen-dunkel.png` |
| A12 | Ab hier, Listen, Detail | 135 dp-Werte außerhalb der Skala (2, 3, 5, 6, 10, 14, 18 dp und feste Maße wie 150×170, 230, 260). Feste Kartenhöhen im Karussell (u. a. „Mehr Runden zeigen“ 150×170 mit zweizeiligem Text) schneiden bei 200 % Schrift ab | Mittel | **behoben**: Off-Scale-Maße als Tokens (Abstände, Strichstärken, Komponentenmaße), Karussellkarten mit `heightIn` statt fester Höhe. Bilder `nachher-2/a12-karussell-schrift-200*.png` |
| A13 | Kartenattribution im Detail | Hintergrund `surface` mit 80 % Deckkraft. Der Kontrast hängt vom Kartenbild ab und lässt sich ohne echtes Rendering nicht berechnen | Niedrig | **offen** (Gerät) |
| A14 | Tokens.kt (generiert) | Kommentar „Blau → Bronze“ in der Skala kommt aus dem Generator | Niedrig | **behoben**: Generatorkommentar lautet jetzt „Blau → Gold“ |
| A15 | Routentexte (App und Web) | Beschreibungstexte aus den Routendaten enthalten „5.2 km“ (Dezimalpunkt) | Mittel | **behoben**: Routentexte in App und Web mit deutschem Zahlformat (862 Dateien geändert, Nachprüfung 0 Treffer) |

### 3.2 Web

| ID | Screen | Befund | Schwere | Status |
|---|---|---|---|---|
| W01 | Ab hier, Runden-Karussell | `<button role="listitem">`: Screenreader verlieren die Schaltflächenrolle, `aria-pressed` ist auf listitem ungültig | Hoch | behoben (listitem-Wrapper, `type="button"`) |
| W02 | Ab hier, Karussell | Karten ohne Fokusring und Hover-Zustand | Mittel | behoben (`focus-visible:ring-2 ring-ring`, `hover:bg-accent` wie in der Rundenliste) |
| W03 | ganze App | prefers-reduced-motion nicht berücksichtigt (CSS-Animationen und Kamerafahrten der Karte) | Mittel | behoben (globale Media Query, `fitBounds` ohne Animation) |
| W04 | Karte | MapLibre-Bedienelemente in Standard-Weiß, im Dunkelmodus ein Fremdkörper, ohne eigenen Fokusring | Mittel | behoben (Fläche, Rahmen und Fokus über Tokens, Icons im Dunkelmodus invertiert) |
| W05 | App, Ab hier | Inline-Styles für Safe-Area-Abstände | Niedrig | behoben (Tailwind-Klassen). Bleiben: datengetriebene Balkenbreiten in ScoreBreakdown und Progress, das ist zulässig |
| W06 | Ab hier, Karussell | „hm“ statt „Hm“, km ohne deutsches Zahlenformat | Niedrig | behoben (`fmt`, „Hm“ wie im Rest der App) |
| W07 | Karte mobil | Zwei Standort-Schaltflächen (Kopfleiste und MapLibre-Geolocate) | Mittel | **behoben**, abweichend von der Empfehlung: die Standort-Schaltfläche in der Kopfleiste ist entfernt, das MapLibre-Geolocate bleibt der einzige Standortknopf. „Runde ab hier“ bleibt als Schaltfläche oben in der Liste erreichbar |
| W08 | App.tsx | 4 ESLint-Warnungen `set-state-in-effect` | Niedrig | **behoben**: abgeleiteter Zustand statt `setState` im Effekt, ESLint 0 Warnungen |
| W09 | Build | JS-Chunk > 500 kB (MapLibre) | Niedrig | **kein Handlungsbedarf**: MapLibre ist bereits ein eigener, nachgeladener Chunk (1 093,6 kB, gzip 295 kB), wird nur über die Lazy-Komponenten importiert und nicht vorgeladen. Die Warnung betrifft die Bibliothek selbst |

### 3.3 Zählung

| Schwere | gesamt | behoben | offen |
|---|---|---|---|
| Hoch | 3 | 3 | 0 |
| Mittel | 12 | 12 | 0 |
| Niedrig | 9 | 8 (W09 ohne Änderung erledigt) | 1 (A13, Gerät) |
| **Summe** | **24** | **15** | **9** |

Dazu kommen die Geräteprüfungen in Abschnitt 6.

### 3.4 Nachtrag: Karte im Vollbild (Wunsch Stefan, 30.10.2026)

In allen Routendetails (Katalog, eigene/geplante Runden, gefahrene Touren) gibt es oben rechts einen runden Knopf mit dem Lucide-Symbol Maximize („Karte im Vollbild“, 48 dp). Er öffnet die Karte randlos unter Beachtung der Insets, die Route wird mit Rand eingepasst, Zoomen und Verschieben sind aktiv, Kompass und Standortknopf sind wie auf der Hauptkarte vorhanden. Geschlossen wird mit X („Vollbild schließen“) oder System-Zurück (Predictive Back). Umsetzung als eine wiederverwendbare Komponente: App `ui/FullscreenMap.kt` (`ExpandableMap`, `FullscreenMap`), Web `components/FullscreenMap.tsx` (Radix-Dialog, Esc schließt, Fokus geht auf X und beim Schließen zurück auf den Auslöser). Nur Tokens. Tests: `MapFullscreenTest` (3), Playwright-Flow und axe-Prüfung „Karte im Vollbild“. Bilder: `nachher-2/karte-{vorschau-knopf,vollbild}-{light,dark}.png` und `nachher-2/web/web-{desktop,mobil}-karte-{vorschau-knopf,vollbild}-{light,dark}.png`. Hinweis: Im Web sind die MapLibre-Zoomknöpfe 29 px groß (Bibliotheksstandard). Pinch- und Mausradzoom gehen, X und Auslöser haben 48 px.

## 4. Token-Übersicht (Quelle `design/tokens.json`)

**Farben**

| Rolle | Hell | Dunkel |
|---|---|---|
| background | #F5F6FA | #1A1A2E |
| surface / surfaceContainer | #F5F6FA / #ECEFF5 | #16213E / #16213E |
| surfaceContainerHigh | #DDE3EF | #0F3460 |
| primary (Akzent) | #8A5A14 (abgedunkelter Akzent für Kontrast auf Hell) | #EFC07B |
| onPrimary | #F5F6FA | #1A1A2E |
| primaryContainer / onPrimaryContainer | #FBEBD3 / #5E3D0C | #0F3460 / #F5D3A0 |
| accent / onAccent | #EFC07B / #1A1A2E | #EFC07B / #1A1A2E |
| onSurface / onSurfaceVariant | #1A1A2E / #4A5370 | #ECEEF4 / #B8C1D6 |
| outline / outlineVariant | #7A8299 / #C5CBD8 | #7F8BA8 / #0F3460 |
| error / errorContainer / onErrorContainer | #B3261E / #F9DEDC / #5C1612 | #F2B8B5 / #5C1612 / #F2B8B5 |
| warning / success | #9A6400 / #2E6B4F | #F0C470 / #8FD1B0 |
| record / onRecord | #B3261E / #F5F6FA | #F2B8B5 / #1A1A2E |
| Score-Skala | #3E5F9A → #7A8DB0 → #C9A46E → #EFC07B | gleich |

**Typografie** (Geist, tabellarische Ziffern)

| Token | Größe / Zeilenhöhe / Gewicht |
|---|---|
| display | 56 / 64 sp / 600 |
| headline | 24 / 32 sp / 600 |
| title | 18 / 24 sp / 600 |
| body | 15 / 22 sp / 400 |
| label | 13 / 18 sp / 500 |
| caption | 12 / 16 sp / 400 |
| Fahrmodus | driveTurn 72 sp, driveDist 56 sp, driveKpi 40 sp, driveEta 24 sp, driveKpiSecondary 22 sp |

**Abstände:** spaceXs 4, spaceSm 8, spaceMd 12, spaceLg 16, spaceXl 20, spaceXxl 24 dp.
**Radien:** radiusSm 8, radiusMd 12, radiusLg 16, radiusXl 28 dp. Web: `--radius` mit sm…4xl abgeleitet.
**Ziele:** touchMin 48 dp, touchDrive 64 dp. **Linien:** Route 6 dp (+2 Casing), Alternative 4 dp, Fortschritt 4 dp.

## 5. Kontrasttabelle

Automatisch berechnet mit `gen_tokens.py --check`. Alle 56 Paare erfüllen WCAG AA, min = gefordertes Verhältnis.

```
ok  brand.light             onSurface auf background           15.79 (min 4.5)
ok  brand.light             onSurface auf surfaceContainer     14.81 (min 4.5)
ok  brand.light             onSurface auf surfaceContainerHigh 13.25 (min 4.5)
ok  brand.light      onSurfaceVariant auf background            7.04 (min 4.5)
ok  brand.light      onSurfaceVariant auf surfaceContainer      6.60 (min 4.5)
ok  brand.light      onSurfaceVariant auf surfaceContainerHigh  5.90 (min 4.5)
ok  brand.light             onSurface auf surface              15.79 (min 4.5)
ok  brand.light      onSurfaceVariant auf surface               7.04 (min 4.5)
ok  brand.light               primary auf surfaceContainer      5.13 (min 3.0)
ok  brand.light               primary auf surfaceContainerHigh  4.59 (min 3.0)
ok  brand.light             onPrimary auf primary               5.47 (min 4.5)
ok  brand.light    onPrimaryContainer auf primaryContainer      8.34 (min 4.5)
ok  brand.light              onAccent auf accent               10.15 (min 4.5)
ok  brand.light               primary auf background            5.47 (min 3.0)
ok  brand.light               outline auf background            3.55 (min 3.0)
ok  brand.light                 error auf background            6.05 (min 4.5)
ok  brand.light      onErrorContainer auf errorContainer       10.46 (min 4.5)
ok  brand.light               warning auf background            4.63 (min 3.0)
ok  brand.light               success auf background            5.84 (min 3.0)
ok  brand.light              onRecord auf record                6.05 (min 4.5)
ok  brand.light                record auf background            6.05 (min 3.0)
ok  brand.dark              onSurface auf background           14.70 (min 4.5)
ok  brand.dark              onSurface auf surfaceContainer     13.70 (min 4.5)
ok  brand.dark              onSurface auf surfaceContainerHigh 10.77 (min 4.5)
ok  brand.dark       onSurfaceVariant auf background            9.45 (min 4.5)
ok  brand.dark       onSurfaceVariant auf surfaceContainer      8.81 (min 4.5)
ok  brand.dark       onSurfaceVariant auf surfaceContainerHigh  6.93 (min 4.5)
ok  brand.dark              onSurface auf surface              13.70 (min 4.5)
ok  brand.dark       onSurfaceVariant auf surface               8.81 (min 4.5)
ok  brand.dark                primary auf surfaceContainer      9.46 (min 3.0)
ok  brand.dark                primary auf surfaceContainerHigh  7.44 (min 3.0)
ok  brand.dark              onPrimary auf primary              10.15 (min 4.5)
ok  brand.dark     onPrimaryContainer auf primaryContainer      8.75 (min 4.5)
ok  brand.dark               onAccent auf accent               10.15 (min 4.5)
ok  brand.dark                primary auf background           10.15 (min 3.0)
ok  brand.dark                outline auf background            5.00 (min 3.0)
ok  brand.dark                  error auf background            9.99 (min 4.5)
ok  brand.dark       onErrorContainer auf errorContainer        7.79 (min 4.5)
ok  brand.dark                warning auf background           10.43 (min 3.0)
ok  brand.dark                success auf background            9.68 (min 3.0)
ok  brand.dark               onRecord auf record                9.99 (min 4.5)
ok  brand.dark                 record auf background            9.99 (min 3.0)
ok  map.light             routeActive auf land                  4.49 (min 3.0)
ok  map.light             routeActive auf forest                3.51 (min 3.0)
ok  map.light             routeActive auf roadMajor             3.78 (min 3.0)
ok  map.light             routeActive auf water                 3.30 (min 3.0)
ok  map.light             routeActive auf road                  4.85 (min 3.0)
ok  map.light                routeAlt auf land                  3.55 (min 3.0)
ok  map.light                   label auf land                  7.04 (min 4.5)
ok  map.dark              routeActive auf land                 10.15 (min 3.0)
ok  map.dark              routeActive auf forest                8.22 (min 3.0)
ok  map.dark              routeActive auf roadMajor             5.01 (min 3.0)
ok  map.dark              routeActive auf water                 7.44 (min 3.0)
ok  map.dark              routeActive auf road                  6.95 (min 3.0)
ok  map.dark                 routeAlt auf land                  5.00 (min 3.0)
ok  map.dark                    label auf land                  9.45 (min 4.5)
```

Rahmen im Dunkelmodus (A11, behoben): `border` #3A5F96 hat 2,65:1 auf #1A1A2E und 2,47:1 auf #16213E, `outline` #7F8BA8 hat 5,00 / 4,66 / 3,67:1. Nicht in der Tabelle: Attribution mit 80 % Deckkraft (A13, offen). Deaktivierte Elemente sind nach WCAG 1.4.3 ausgenommen.

## 6. Nur am Gerät prüfbar (offen)

| Punkt | Warum offen |
|---|---|
| Lesbarkeit im Sonnenlicht (Fahrleiste, Karte, Hell/Dunkel) | braucht echtes Display im Freien |
| Bedienung mit Handschuhen, 64-dp-Ziele im Fahrmodus | braucht Gerät und Halterung |
| Echte Kartendarstellung (MapLibre, Routenlinie auf Kacheln) | MapLibre rendert in den Tests nicht |
| TalkBack-Durchlauf aller Screens | Robolectric prüft keine echte Ansage |
| Schriftgröße 200 % und Anzeigegröße groß | nicht als Screenshot getestet |
| Predictive-Back-Animation (Android 14/15/16) | nur auf Gerät sichtbar |
| Fahrer-Ablenkung (Blickdauer, Ansagen-Timing) | nur auf echter Fahrt |

## 7. Komponenteninventar

**Android (Material 3, Lucide-Icons):** Button (≈120), TextButton (43), OutlinedButton (21), FilledTonalButton (11), IconButton (29), FilledTonalIconButton (5), Card/ElevatedCard/OutlinedCard, ListItem (26), Switch (15), SegmentedButton, FilterChip, RadioButton, Slider, OutlinedTextField, TopAppBar, NavigationBar (4 Tabs), AlertDialog (16), ModalBottomSheet (4), Snackbar, Linear-/CircularProgressIndicator. Eigene Komponenten: DriveHud (Fahrleiste), EntryCard, ElevationProfile, ScoreBreakdown/InfoButton, StartPicker, FreeLoopSection, UpdateDialog, RecordPill.
**Web (shadcn/Radix, Lucide):** Button, Badge, Card, Tabs, Sheet (Bottom-Sheet mobil), Drawer (vaul), Popover, DropdownMenu, Slider, RadioGroup, Progress, Spinner, Sonner-Toast, Chart (Recharts). Eigene: MapView, LoopList, LoopDetail, ScoreBreakdown, Filters, NavMenu, AbHier.
**Konsistenz App ↔ Web:** gleiche Token-Quelle, gleiche Begriffe (`Tokens.Terms` / `terms.ts`), gleiche Score-Skala und Kartenstile, beide nur Lucide.

## 8. Tests und Artefakte

- Android Unit/Screenshot: 141 Tests, 0 Fehler. shared jvm: 99, androidHost: 49, 0 Fehler. `checkNoMaterialIcons` grün.
- APK 0.6.4 (versionCode 13), Zertifikat 538baaec…35d7, SHA-256 cda0f3dc203787640803e64432c91106397a5530ded504b43dad13ad503bfe30. Nicht veröffentlicht.
- Web: ESLint 0 Fehler, 0 Warnungen, Token-Wächter ok, Typecheck ok, Vitest 125/125, Build ok, Playwright a11y + Flows 50 bestanden, 1 übersprungen (axe ohne Verstöße).
- APK: `app/androidApp/build/outputs/apk/release/androidApp-release.apk`, 0.6.4 / versionCode 13, gleiches Zertifikat (538baaec…35d7), SHA-256 3907a17e03a783fab382b7a5640600acd2c48d246b6c06fda5f023adc24d248f.
- Screenshots: `app/docs/screenshots/ui-audit-0.6.4/vorher/` und `…/nachher/` (Android `android-*`, Web `web-*`, je hell/dunkel; Web zusätzlich Tastaturfokus).

## 9. Abnahme-Checkliste Design-Team

- [ ] Palette und Akzent wie freigegeben (Dark Hauptthema, #EFC07B einziger Akzent)
- [ ] Kontrasttabelle (Abschnitt 5) akzeptiert
- [ ] Behobene Befunde in den Vorher/Nachher-Bildern geprüft
- [x] A10: Rückfrage bei System-Zurück im Fahrmodus
- [x] A11: helleres `outlineVariant` im Dunkelmodus
- [x] A12: Tokens für Off-Scale-Maße, Karussellkarten flexibel
- [x] A15: Zahlformat im Routentext-Generator
- [x] W07: doppelte Standort-Schaltfläche im Web
- [ ] Geräteprüfungen aus Abschnitt 6 durchgeführt (Sonne, Handschuhe, Karte, TalkBack, 200 %, Predictive Back, Fahrt)
- [ ] Freigabe zur Veröffentlichung (separat durch Stefan)
