"""Nachbearbeitung nach phase3/export_web.py (Rohdaten in phase3/out/web_raw) -> webapp/public/data.
Start lädt nur Familienvertreter; alle übrigen Runden liegen in all.json / lines-all.bin (lazy, Expertenmodus/Varianten).
- Vertreter je Familie und Überschneidungsstufe = Runde mit dem besten Tour-Score (r[i]); fn[i] = Familiengröße
- ti = Kurztitel aus Hauptorten der Runde
- lines*.bin: Douglas-Peucker ~10 m, 1e-4°-Raster, Int16-Deltas.
  Format (little endian): int32 n, int32 0, dann je Runde int32 id, int32 cnt, int32 x0, int32 y0, int16[2*(cnt-1)]
- Beschreibungstexte (tx) wandern in die Detail-Shards r/sN.json
Aufruf: python scripts/compact.py [RAW] [OUT]"""
import json, os, re, struct, sys, shutil
from collections import Counter, defaultdict
from shapely.geometry import LineString
H = os.path.dirname(os.path.abspath(__file__))
RAW = sys.argv[1] if len(sys.argv) > 1 else os.path.join(H, '../../phase3/out/web_raw')
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(H, '../public/data')
J = lambda o, f: json.dump(o, open(os.path.join(OUT, f), 'w'), ensure_ascii=False, separators=(',', ':'), allow_nan=False)
KIND = {'restaurant': 'Restaurant', 'cafe': 'Café', 'biergarten': 'Biergarten', 'fuel': 'Tankstelle', 'viewpoint': 'Aussichtspunkt', 'parking': 'Parkplatz', 'fast_food': 'Imbiss', 'pub': 'Kneipe'}
meta = json.load(open(f'{RAW}/meta.json')); lines = json.load(open(f'{RAW}/lines.json'))
os.makedirs(f'{OUT}/r', exist_ok=True)
KIND0 = {'restaurant': 'Restaurant', 'cafe': 'Café', 'biergarten': 'Biergarten', 'fuel': 'Tankstelle', 'viewpoint': 'Aussichtspunkt', 'parking': 'Parkplatz', 'fast_food': 'Imbiss', 'pub': 'Kneipe'}
lay = json.load(open(f'{RAW}/layers.json'))
for ft in lay['pauses']['features']:  # NaN-Namen (unbenannte POIs) -> sonst ungültiges JSON im Browser
    pr = ft['properties']
    if not isinstance(pr.get('n'), str) or not pr['n'].strip(): pr['n'] = KIND0.get(pr.get('k'), pr.get('k')) + ' (ohne Namen)'
json.dump(lay, open(f'{OUT}/layers.json', 'w'), ensure_ascii=False, separators=(',', ':'), allow_nan=False)
loops = meta['loops']; NL = len(meta['levels'])
shards = {k: json.load(open(f'{RAW}/r/s{k}.json')) for k in range(32)}
det = {i: d for s in shards.values() for i, d in s.items()}
HOME = {'Miltenberg', 'Großheubach', 'Kleinheubach', 'Bürgstadt'}
def title(l):
    o = [x for x in det[str(l['id'])]['orte'] if x not in HOME and not x.startswith(('Eisenwerk', 'Gut ', 'Hof ')) and not re.search(r'(mühle|hof|haus|hütte|säge|siedlung)$', x, re.I)] or det[str(l['id'])]['orte']
    if not o: return l['rg'].split(' (')[0]
    a, b = o[len(o) // 3], o[(2 * len(o)) // 3]
    return f'{a} – {b}' if a != b else a
for l in loops:
    l['ti'] = title(l); l['fn'] = [0] * NL; l['r'] = [0] * NL
for i in range(NL):
    fam = defaultdict(list)
    for l in loops: fam[l['f'][i]].append(l)
    for ms in fam.values():
        best = max(ms, key=lambda l: (l['t'], l['s'])); best['r'][i] = 1
        for l in ms: l['fn'][i] = len(ms)
# doppelte Titel eindeutig machen (nur unter Vertretern relevant)
c = Counter(l['ti'] for l in loops if any(l['r']))
for l in loops:
    if not any(l['r']): continue
    if c[l['ti']] > 1: l['ti'] += f" ({round(l['km'])} km)"
# Navi-Lenkpunkte (phase3/navpoints.py -> nav.json): nav.g = Google, nav.a = Apple; je {f, wp, st?: {f, legs: [{wp, end, f}]}}
NAV = json.load(open(f'{RAW}/nav.json')) if os.path.exists(f'{RAW}/nav.json') else {}
def navpack(o):
    r = {'f': o['single']['f'], 'wp': [list(p) for p in o['single']['wp']]}
    if o['stages']: r['st'] = {'f': o['stages']['f'], 'legs': [{'wp': [list(p) for p in l['wp']], 'end': list(l['end']), 'f': l['f']} for l in o['stages']['legs']]}
    return r
# Score-Aufschlüsselung (phase3/score_parts.py -> cache/parts.pkl), Format siehe README "Score-Aufschlüsselung"
import pickle
PP = f'{RAW}/../../cache/parts.pkl'
PARTS = pickle.load(open(PP, 'rb')) if os.path.exists(PP) else {}
# Kennzahl -> (Quelle, Richtung +1 höher besser / -1 niedriger besser / 0 neutral, Rundung)
MET = {'kurv': ('curv_mean', 1, 3), 'kehr': ('tight_mean', 1, 0), 'rhy': ('rhythm_mean', 1, 3), 'rel': ('relief_mean', 1, 3),
       'hmkm': (lambda c: c['ascent_m'] / c['len_m'] * 1000, 1, 1), 'aus': ('view_frac', 1, 3), 'fern': ('view_far_m', 1, 0),
       'wald': ('forest', 0, 3), 'offen': ('open', 0, 3), 'ort': ('town_share', -1, 3), 'ortn': ('town_entries', -1, 0),
       'b': ('b_share', -1, 3), 'hl': ('highlight_share', 1, 3), 'tr': ('transfer_share', -1, 3),
       'unt': ('interrupts_per10km', -1, 2), 'amp': ('signals', -1, 0), 'dram': ('dramaturgy', 1, 3), 'top': ('topk', 1, 3)}
CON = ('route', 'topk', 'flow', 'dram', 'view', 'interrupts')
SB = {}
if PARTS:
    ids = [str(l['id']) for l in loops]
    raw = {k: {i: float(f(PARTS[int(i)]) if callable(f) else PARTS[int(i)][f]) for i in ids} for k, (f, _, _) in MET.items()}
    def pctl(k, i):  # Anteil der übrigen Runden, die in dieser Kennzahl schlechter sind (Gleichstand halb), 0–100
        d = MET[k][1]; x = raw[k][i]; o = [v for j, v in raw[k].items() if j != i]
        if not d: x, o, d = x, o, 1
        return round(100 * sum((v < x) if d > 0 else (v > x) for v in o) / len(o) + 50 * sum(v == x for v in o) / len(o))
    for i in ids:
        c = PARTS[int(i)]
        assert abs(c['tour_score'] - next(l for l in loops if str(l['id']) == i)['t']) < 6e-4, i
        sg = [[round(float(g['km0']), 2), int(g['len_m']), round(g['score'], 3), g['kind'][0], g['name'], round(g['grade'], 1)] for g in c['segments']]
        SB[i] = {'c': {k: round(float(c['contrib'][k]), 4) for k in CON}, 'v': {k: round(raw[k][i], MET[k][2]) for k in MET},
                 'p': {k: pctl(k, i) for k in MET if MET[k][1]}, 'sg': sg}
    # Stärken-Chips: bis zu 3 Kennzahlen mit Perzentil >= 75 (nur "höher/niedriger besser", ohne Doppelungen)
    CHIP = ['kurv', 'rhy', 'kehr', 'rel', 'aus', 'hl', 'dram', 'ort', 'b', 'unt']
    for l in loops:
        p = SB[str(l['id'])]['p']; l['st'] = [k for k in sorted(CHIP, key=lambda k: -p[k]) if p[k] >= 75][:3]
    avg = {k: round(sum(raw[k].values()) / len(ids), MET[k][2] + 1) for k in MET}
    srt = {k: sorted(raw[k].values()) for k in MET}; med = {k: round(srt[k][len(ids) // 2], MET[k][2] + 1) for k in MET}
    cavg = {k: round(sum(float(PARTS[int(i)]['contrib'][k]) for i in ids) / len(ids), 4) for k in CON}
tx = {}
for l in loops: tx[str(l['id'])] = l.pop('tx', '')
for k, s in shards.items():
    for i, d in s.items():
        d['tx'] = tx[i]
        if i in NAV: d['nav'] = {'g': navpack(NAV[i]['google']), 'a': navpack(NAV[i]['apple'])}
        if i in SB: d['sb'] = SB[i]
        d.pop('wp', None)  # alte 8 Gleichverteilungs-Punkte durch nav ersetzt
        for p in d['pauses']:  # unbenannte POIs kommen als NaN aus pandas -> ungültiges JSON im Browser
            if not isinstance(p['n'], str) or not p['n'].strip(): p['n'] = KIND.get(p['k'], p['k']) + ' (ohne Namen)'
    json.dump(s, open(f'{OUT}/r/s{k}.json', 'w'), ensure_ascii=False, separators=(',', ':'), allow_nan=False)
reps = sorted([l for l in loops if any(l['r'])], key=lambda l: -l['t']); rest = [l for l in loops if not any(l['r'])]
def binfile(ls, f):
    buf = [struct.pack('<ii', len(ls), 0)]
    for l in ls:
        g = LineString(lines[str(l['id'])]).simplify(0.00012)
        q = [(round(x * 1e4), round(y * 1e4)) for x, y in g.coords]; q = [p for j, p in enumerate(q) if j == 0 or p != q[j - 1]]
        buf.append(struct.pack('<iiii', l['id'], len(q), *q[0]))
        buf.append(struct.pack(f'<{2*(len(q)-1)}h', *[v for a, b in zip(q, q[1:]) for v in (b[0]-a[0], b[1]-a[1])]))
    open(f'{OUT}/{f}', 'wb').write(b''.join(buf))
binfile(reps, 'lines.bin'); binfile(rest, 'lines-all.bin')
J(dict(levels=meta['levels'], start=meta['start'], total=len(loops), loops=reps, **({'sbm': {'avg': avg, 'med': med, 'c': cavg, 'n': len(loops)}} if SB else {})), 'meta.json'); J(rest, 'all.json')
for f in ('meta.json', 'all.json', 'lines.bin', 'lines-all.bin'): print(f, os.path.getsize(f'{OUT}/{f}') // 1024, 'KB')
print(len(reps), 'Vertreter (alle Stufen),', {meta['levels'][i]: sum(l['r'][i] for l in loops) for i in range(NL)})
