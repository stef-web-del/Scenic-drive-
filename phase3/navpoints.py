"""Lenkpunkte je Runde vorberechnen (offline). Läuft im Valhalla-venv:
   vh/bin/python phase3/navpoints.py            -> phase3/out/web_raw/nav.json  (danach webapp/scripts/compact.py)
Algorithmus: scenic/navpoints.py. Router-Proxy: Valhalla lokal, costing auto, Zwischenziele als 'break' (wie Stopps in Google/Apple)."""
import json, sys, os, math, time
from multiprocessing import Pool
_SR=os.environ.get('SCENIC_ROOT') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _SR)
import importlib.util
spec = importlib.util.spec_from_file_location('navpoints', f'{_SR}/scenic/navpoints.py'); NP = importlib.util.module_from_spec(spec); spec.loader.exec_module(NP)
RAW = f'{_SR}/phase3/out/web_raw'; CFG = os.environ.get('SCENIC_VALHALLA_CONFIG', f'{_SR}/valhalla/valhalla.json')
A = None
def decode6(s):
    out = []; i = la = lo = 0
    while i < len(s):
        vals = []
        for _ in range(2):
            sh = r = 0
            while True:
                b = ord(s[i]) - 63; i += 1; r |= (b & 0x1f) << sh; sh += 5
                if b < 0x20: break
            vals.append(~(r >> 1) if r & 1 else r >> 1)
        la += vals[0]; lo += vals[1]; out.append((lo / 1e6, la / 1e6))
    return out
def route(pts):
    try:
        r = json.loads(A.route(json.dumps({"locations": [{"lon": p[0], "lat": p[1], "type": "break"} for p in pts], "costing": "auto"})))
        return [q for leg in r["trip"]["legs"] for q in decode6(leg["shape"])]
    except Exception:
        return None
def edges_s(g):
    x = json.loads(A.trace_attributes(json.dumps({"shape": [{"lon": p[0], "lat": p[1]} for p in g], "costing": "auto", "shape_match": "map_snap",
        "filters": {"attributes": ["edge.begin_shape_index", "edge.end_shape_index", "shape"], "action": "include"}})))
    sh = decode6(x["shape"]); lat0 = sum(p[1] for p in g) / len(g); xy = NP.proj(sh, lat0)
    cum = [0.0]
    for a, b in zip(xy[:-1], xy[1:]): cum.append(cum[-1] + math.hypot(*(b - a)))
    _, _, S = NP.densify(NP.proj(g, lat0)); k = S[-1] / max(cum[-1], 1)
    return [(cum[e["begin_shape_index"]] * k, cum[e["end_shape_index"]] * k) for e in x["edges"]]
def init():
    global A
    import valhalla; A = valhalla.Actor(CFG)
def job(item):
    i, g = item; t = time.time(); es = edges_s(g); out = {}
    for app, lim in NP.LIMITS.items(): out[app] = NP.plan(route, g, es, lim)
    out["_t"] = round(time.time() - t, 1); return i, out
if __name__ == '__main__':
    items = [(i, d['g']) for k in range(32) for i, d in json.load(open(f'{RAW}/r/s{k}.json')).items()]
    if len(sys.argv) > 1: items = items[:int(sys.argv[1])]
    res = {}
    with Pool(8, initializer=init) as p:
        for n, (i, o) in enumerate(p.imap_unordered(job, items)):
            res[i] = o
            if n % 20 == 0: print(n, i, o['google']['single']['f'], o['apple']['single']['f'], o['_t'], flush=True)
    json.dump(res, open(f'{RAW}/nav.json', 'w'), separators=(',', ':'))
    print(len(res), 'Runden ->', f'{RAW}/nav.json')
