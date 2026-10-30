"""design/tokens.json → alle Plattform-Dateien. Aufruf: python3 app/tools/gen_tokens.py [--check]

Erzeugt:
  app/androidApp/.../ui/Tokens.kt      Android-Theme-Werte (Farben, Karte, Typo, Maße)
  webapp/src/tokens.css                CSS-Variablen --sr-*, shadcn-Variablen auf --sr-* gemappt
  webapp/src/lib/terms.ts              Begriffe
  webapp/src/lib/mapTheme.ts           Karten- und Score-Farben für MapLibre-Layer
  res/values*/colors_tokens.xml        Fensterhintergrund (Splash) aus background
  design/map/scenic-{light,dark}.json  MapLibre-Stil (OpenMapTiles-Schema, OpenFreeMap-Kacheln), auch als Android-Asset und webapp/public/map/
Prüft außerdem die Kontraste (WCAG AA) aller Text/Fläche-Paare; bei Verstoß Exit 1.
"""
import json, os, sys
R = '/workspace/scenic'
t = json.load(open(f'{R}/design/tokens.json'))
P, S = t['primitive'], t['semantic']

def res(v):
    """'palette.gold' → Hex aus den Primitiven; Hex bleibt Hex."""
    if v.startswith('#'): return v
    g, k = v.split('.', 1)
    x = P[g]
    return x[int(k)] if isinstance(x, list) else x[k]
def resolve(d): return {k: res(v) for k, v in d.items()}

B = S['brand']
BL, BD, ML, MD = resolve(B['light']), resolve(B['dark']), resolve(B['mapLight']), resolve(B['mapDark'])
BSCORE = [(s, res(c)) for s, c in B['score']]
L, D = BL, BD
SPEED = [res(c) for c in B['speed']]
C = t['component']

# ---------- Kontrast ----------
def lum(h):
    c = [int(h[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    c = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True); return (la + 0.05) / (lb + 0.05)
PAIRS = [('onSurface', 'background', 4.5), ('onSurface', 'surfaceContainer', 4.5), ('onSurface', 'surfaceContainerHigh', 4.5),
         ('onSurfaceVariant', 'background', 4.5), ('onSurfaceVariant', 'surfaceContainer', 4.5), ('onSurfaceVariant', 'surfaceContainerHigh', 4.5), ('onSurface', 'surface', 4.5), ('onSurfaceVariant', 'surface', 4.5),
         ('primary', 'surfaceContainer', 3.0), ('primary', 'surfaceContainerHigh', 3.0),
         ('onPrimary', 'primary', 4.5), ('onPrimaryContainer', 'primaryContainer', 4.5), ('onAccent', 'accent', 4.5),
         ('primary', 'background', 3.0), ('outline', 'background', 3.0), ('error', 'background', 4.5),
         ('outline', 'surface', 3.0), ('outline', 'surfaceContainerHigh', 3.0), ('border', 'background', 2.0), ('border', 'surface', 2.0), ('border', 'surfaceContainer', 1.6),
         ('onErrorContainer', 'errorContainer', 4.5), ('warning', 'background', 3.0), ('success', 'background', 3.0), ('onRecord', 'record', 4.5), ('record', 'background', 3.0)]
MAPPAIRS = [('routeActive', 'land', 3.0), ('routeActive', 'forest', 3.0), ('routeActive', 'roadMajor', 3.0), ('routeActive', 'water', 3.0), ('routeActive', 'road', 3.0), ('routeAlt', 'land', 3.0), ('label', 'land', 4.5)]
def contrast():
    rows, bad = [], 0
    for name, sch, pairs in (('brand.light', BL, PAIRS), ('brand.dark', BD, PAIRS), ('map.light', ML, MAPPAIRS), ('map.dark', MD, MAPPAIRS)):
        for f, b, need in pairs:
            r = ratio(sch[f], sch[b]); ok = r >= need; bad += not ok
            rows.append(f'{"ok " if ok else "FEHLER"} {name:12s} {f:>20s} auf {b:<20s} {r:5.2f} (min {need})')
    return rows, bad

# ---------- Android ----------
def kc(h): return 'Color(0xFF' + h[1:].upper() + ')'
def kobj(n, c, ind='    '): return [f'{ind}object {n} {{'] + [f'{ind}    val {k} = {kc(v)}' for k, v in c.items()] + [f'{ind}}}']
kt = ['package de.scenicroutes.app.ui', '', '// GENERIERT aus design/tokens.json (app/tools/gen_tokens.py) – nicht von Hand ändern.',
      'import androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.unit.dp', 'import androidx.compose.ui.unit.sp', '', 'object Tokens {']
kt += kobj('Light', L) + kobj('Dark', D) + kobj('MapLight', ML) + kobj('MapDark', MD)
kt.append('    /** Score- und Heatmap-Skala Blau → Gold (farbenblindsicher). */')
kt.append('    val scoreStops = listOf(' + ', '.join(f'{s} to {kc(c)}' for s, c in BSCORE) + ')')
kt.append('    val speedStops = listOf(' + ', '.join(kc(c) for c in SPEED) + ')')
kt.append('    object Type {')
for k, v in t['type'].items():
    if k.startswith('$'): continue
    sz, lh, w = v
    kt.append(f'        val {k} = Triple({sz}.sp, {lh}.sp, {w})')
kt.append('    }')
kt.append(f'    val touchMin = {C["touch"]["min"]}.dp; val touchDrive = {C["touch"]["drive"]}.dp')
kt.append(f'    val driveTurn = {C["drive"]["turnSize"]}.sp; val driveDist = {C["drive"]["distSize"]}.sp; val driveEta = {C["drive"]["etaSize"]}.sp')
kt.append(f'    val driveKpi = {C["drive"]["kpiPrimary"]}.sp; val driveKpiSecondary = {C["drive"]["kpiSecondary"]}.sp; val driveProgress = {C["drive"]["progress"]}.dp')
kt.append(f'    val routeWidth = {C["map"]["routeWidth"]}.dp; val routeCasing = {C["map"]["routeCasing"]}.dp; val altWidth = {C["map"]["altWidth"]}.dp')
kt += [f'    val radius{k[0].upper()+k[1:]} = {v}.dp' for k, v in t['radius'].items()]
kt += [f'    val space{k[0].upper()+k[1:]} = {v}.dp' for k, v in t['space'].items()]
kt += [f'    val stroke{k[0].upper()+k[1:]} = {v}.dp' for k, v in t['stroke'].items()]
kt += [f'    val size{k[0].upper()+k[1:]} = {v}.dp' for k, v in t['size'].items() if not k.startswith('$')]
kt.append('    object Terms {'); kt += [f'        const val {k} = "{v}"' for k, v in t['terms'].items()]; kt.append('    }')
kt.append('    val detailOrder = listOf(' + ', '.join(f'"{x}"' for x in t['detailOrder']) + ')')
kt.append('}')

# ---------- CSS ----------
def css(c, p='--sr-'): return '\n'.join(f'  {p}{k}: {v};' for k, v in c.items())
SHAD = {'background': 'background', 'foreground': 'onSurface', 'card': 'surfaceContainer', 'card-foreground': 'onSurface', 'popover': 'surfaceContainerHigh',
        'popover-foreground': 'onSurface', 'primary': 'primary', 'primary-foreground': 'onPrimary', 'secondary': 'surfaceContainerHigh',
        'secondary-foreground': 'onSurface', 'muted': 'surfaceContainer', 'muted-foreground': 'onSurfaceVariant', 'accent': 'primaryContainer',
        'accent-foreground': 'onPrimaryContainer', 'destructive': 'error', 'border': 'border', 'input': 'outline', 'ring': 'primary',
        'chart-1': 'score-0', 'chart-2': 'score-1', 'chart-3': 'score-2', 'chart-4': 'score-3', 'chart-5': 'primary',
        'sidebar': 'surface', 'sidebar-foreground': 'onSurface', 'sidebar-primary': 'primary', 'sidebar-primary-foreground': 'onPrimary',
        'sidebar-accent': 'primaryContainer', 'sidebar-accent-foreground': 'onPrimaryContainer', 'sidebar-border': 'border', 'sidebar-ring': 'primary',
        'warning': 'warning', 'success': 'success', 'record': 'record'}
shad = '\n'.join(f'  --{k}: var(--sr-{v});' for k, v in SHAD.items())
typ = '\n'.join(f'  --sr-text-{k}: {s}px; --sr-leading-{k}: {l}px; --sr-weight-{k}: {w};' for k, (s, l, w) in ((k, v) for k, v in t['type'].items() if not k.startswith('$')))
cs = f"""/* GENERIERT aus design/tokens.json (app/tools/gen_tokens.py) – nicht von Hand ändern. */
:root {{
{css(L)}
{css(ML, '--sr-map-')}
{chr(10).join(f'  --sr-score-{i}: {c};' for i, (s, c) in enumerate(BSCORE))}
{chr(10).join(f'  --sr-speed-{i}: {c};' for i, c in enumerate(SPEED))}
{chr(10).join(f'  --sr-radius-{k}: {v}px;' for k, v in t['radius'].items())}
{chr(10).join(f'  --sr-space-{k}: {v}px;' for k, v in t['space'].items())}
{chr(10).join(f'  --sr-stroke-{k}: {v}px;' for k, v in t['stroke'].items())}
  --radius: 0.75rem;
  --sr-touch-min: {C['touch']['min']}px;
  --sr-touch-drive: {C['touch']['drive']}px;
{typ}
{shad}
}}
.dark {{
{css(D)}
{css(MD, '--sr-map-')}
}}
"""

# ---------- mapTheme.ts ----------
ts = ('// GENERIERT aus design/tokens.json (app/tools/gen_tokens.py) – nicht von Hand ändern.\n'
      'export const MAP_THEME = ' + json.dumps({'light': ML, 'dark': MD}, indent=2) + ' as const\n'
      'export const SCORE_STOPS: ReadonlyArray<readonly [number, string]> = ' + json.dumps(BSCORE) + '\n'
      'export const SPEED_STOPS: ReadonlyArray<string> = ' + json.dumps(SPEED) + '\n'
      'export const ROUTE = ' + json.dumps(C['map']) + ' as const\n')

# ---------- MapLibre-Stil ----------
def style(m, name):
    road = lambda cls, col, w: {'id': f'road-{cls}', 'type': 'line', 'source': 'omt', 'source-layer': 'transportation', 'filter': ['in', 'class', *cls.split('+')],
                                 'layout': {'line-cap': 'round', 'line-join': 'round'}, 'paint': {'line-color': col, 'line-width': ['interpolate', ['linear'], ['zoom'], 8, w / 4, 16, w * 2]}}
    return {'version': 8, 'name': f'Scenic Routes {name} (aus Tokens)', 'glyphs': 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',
            'sources': {'omt': {'type': 'vector', 'url': 'https://tiles.openfreemap.org/planet', 'attribution': '© OpenStreetMap-Mitwirkende (ODbL) · OpenFreeMap'}},
            'layers': [{'id': 'land', 'type': 'background', 'paint': {'background-color': m['land']}},
                       {'id': 'forest', 'type': 'fill', 'source': 'omt', 'source-layer': 'landcover', 'filter': ['==', 'class', 'wood'], 'paint': {'fill-color': m['forest']}},
                       {'id': 'park', 'type': 'fill', 'source': 'omt', 'source-layer': 'park', 'paint': {'fill-color': m['forest'], 'fill-opacity': 0.5}},
                       {'id': 'water', 'type': 'fill', 'source': 'omt', 'source-layer': 'water', 'paint': {'fill-color': m['water']}},
                       {'id': 'river', 'type': 'line', 'source': 'omt', 'source-layer': 'waterway', 'paint': {'line-color': m['water'], 'line-width': 1.5}},
                       road('minor+service+track', m['road'], 2), road('tertiary+secondary', m['road'], 3), road('primary+trunk+motorway', m['roadMajor'], 4),
                       {'id': 'place', 'type': 'symbol', 'source': 'omt', 'source-layer': 'place', 'filter': ['in', 'class', 'town', 'village', 'city'],
                        'layout': {'text-field': '{name}', 'text-font': ['Noto Sans Regular'], 'text-size': 12}, 'paint': {'text-color': m['label'], 'text-halo-color': m['land'], 'text-halo-width': 1.2}}]}

def write(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True); open(p, 'w').write(s)

if __name__ == '__main__':
    rows, bad = contrast()
    print('\n'.join(rows))
    if bad: print(f'{bad} Kontrastfehler'); sys.exit(1)
    if '--check' in sys.argv: sys.exit(0)
    write(f'{R}/app/androidApp/src/main/kotlin/de/scenicroutes/app/ui/Tokens.kt', '\n'.join(kt) + '\n')
    write(f'{R}/webapp/src/tokens.css', cs)
    write(f'{R}/webapp/src/lib/terms.ts', '// GENERIERT aus design/tokens.json\nexport const TERMS = ' + json.dumps(t['terms'], ensure_ascii=False, indent=2) + ' as const\nexport const DETAIL_ORDER = ' + json.dumps(t['detailOrder']) + ' as const\n')
    write(f'{R}/webapp/src/lib/mapTheme.ts', ts)
    for d, sch in (('values', L), ('values-night', D)):
        write(f'{R}/app/androidApp/src/main/res/{d}/colors_tokens.xml', '<?xml version="1.0" encoding="utf-8"?>\n<!-- GENERIERT aus design/tokens.json (app/tools/gen_tokens.py) – nicht von Hand ändern. -->\n<resources>\n    <color name="sr_window_background">#FF' + sch['background'][1:].upper() + '</color>\n</resources>\n')
    for n, m in (('light', ML), ('dark', MD)):
        js = json.dumps(style(m, n), indent=1)
        write(f'{R}/design/map/scenic-{n}.json', js); write(f'{R}/app/androidApp/src/main/assets/map/scenic-{n}.json', js); write(f'{R}/webapp/public/map/scenic-{n}.json', js)
    print('ok')
