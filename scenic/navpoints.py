"""Lenkpunkte (shaping points) für die Navi-Übergabe an Google Maps / Apple Karten.

Ziel: mit wenigen Zwischenzielen erreichen, dass ein Standard-Router (hier Valhalla, auto, als Proxy für Google/Apple)
die Original-Runde nachfährt. Reine Geometrie + Greedy-Verfahren; der Router wird injiziert (testbar ohne Valhalla).

Treue (fidelity) = min(Anteil Original-Länge innerhalb BUF des Routings, Anteil Routing-Länge innerhalb BUF des Originals).
Die zweite Hälfte bestraft Umwege und Wendemanöver, die die erste nicht sieht.

Verfahren je Runde:
  1. Kandidaten: Mitten von Router-Kanten (Kreuzung → Kreuzung) entlang der gematchten Runde, mind. EDGE_GAP m von beiden
     Kanten-Enden entfernt → Punkte liegen auf der Straßenachse, nie an einer Kreuzung (keine Abbiegeseiten-Mehrdeutigkeit).
  2. Start = Ziel = Rundenstart. Routen, Abweichung messen.
  3. Längsten nicht abgedeckten Abschnitt des Originals suchen, Kandidat nahe dessen Mitte einfügen (Reihenfolge = Fahrtrichtung,
     sortiert nach Position s entlang der Runde). Nur übernehmen, wenn die Treue steigt; sonst nächstlängsten Abschnitt probieren.
  4. Stopp bei Limit oder Treue ≥ TARGET.
  5. Reicht das Limit nicht: Runde in 2 bzw. 3 Etappen (gleich lange Teile, Schnitt an Kandidaten) teilen, jede Etappe mit vollem Limit.
"""
from __future__ import annotations
import math
import numpy as np
from scipy.spatial import cKDTree

BUF = 25.0          # m Puffer für Überdeckung
STEP = 5.0          # m Verdichtung für die Messung
EDGE_GAP = 30.0     # m Mindestabstand eines Lenkpunkts zu Kreuzungen (Kanten-Enden)
TARGET = 0.95
LIMITS = {"google": 9, "apple": 13}  # Zwischenziele je Link, siehe README / Bericht (Quellen)

def proj(ll, lat0):
    a = np.asarray(ll, float); k = 111320.0
    return np.column_stack([(a[:, 0]) * k * math.cos(math.radians(lat0)), a[:, 1] * k])

def densify(xy, step=STEP):
    """Polyline -> (Punkte, Segmentlängen-Gewichte, kumulierte Position s)"""
    xy = np.asarray(xy, float); out = [xy[:1]]
    for a, b in zip(xy[:-1], xy[1:]):
        L = float(np.hypot(*(b - a))); n = max(1, int(math.ceil(L / step)))
        t = np.arange(1, n + 1)[:, None] / n; out.append(a + (b - a) * t)
    p = np.vstack(out); seg = np.hypot(*np.diff(p, axis=0).T)
    w = np.r_[seg, 0.0] / 2 + np.r_[0.0, seg] / 2; s = np.r_[0.0, np.cumsum(seg)]
    return p, w, s

def coverage(a_xy, b_xy, buf=BUF):
    """Anteil der Länge von a innerhalb buf von b, plus Maske der abgedeckten verdichteten a-Punkte"""
    pa, wa, sa = densify(a_xy); pb, _, _ = densify(b_xy)
    d, _ = cKDTree(pb).query(pa, distance_upper_bound=buf + STEP)
    m = d <= buf
    return float(wa[m].sum() / max(wa.sum(), 1e-9)), m, sa

def fidelity(orig_xy, route_xy):
    if route_xy is None or len(route_xy) < 2: return 0.0, None, None
    c1, m, s = coverage(orig_xy, route_xy); c2, _, _ = coverage(route_xy, orig_xy)
    return min(c1, c2), m, s

def gaps(mask, s):
    """nicht abgedeckte Läufe -> [(länge, s_start, s_end)] absteigend"""
    out = []; i = 0; n = len(mask)
    while i < n:
        if not mask[i]:
            j = i
            while j + 1 < n and not mask[j + 1]: j += 1
            out.append((s[j] - s[i], s[i], s[j])); i = j + 1
        else: i += 1
    return sorted(out, reverse=True)

def candidates(edges_s, gap=EDGE_GAP):
    """edges_s: [(s_begin, s_end)] der gematchten Kanten in Metern entlang der Runde -> Kandidatenpositionen (Kantenmitten)"""
    return np.array(sorted((a + b) / 2 for a, b in edges_s if b - a >= 2 * gap))

def point_at(xy, s_target):
    p, _, s = densify(xy); i = int(np.clip(np.searchsorted(s, s_target), 0, len(p) - 1)); return p[i], i

def plan_leg(route, orig_ll, cand_s, s0, s1, limit, target=TARGET, lat0=None):
    """Greedy für einen Abschnitt [s0, s1] (Meter entlang der Runde). route(list[(lon,lat)]) -> list[(lon,lat)]
    Rückgabe: (gewählte Positionen s, Treue, Routing-Geometrie lon/lat)"""
    lat0 = lat0 if lat0 is not None else float(np.mean(np.asarray(orig_ll)[:, 1]))
    full = proj(orig_ll, lat0); P, _, S = densify(full)
    sel = (S >= s0) & (S <= s1); sub = P[sel]; sub_s = S[sel]
    inv = lambda q: unproj(q, lat0)
    endpoints = (inv(P[np.searchsorted(S, s0)]), inv(P[min(np.searchsorted(S, s1), len(P) - 1)]))
    cs = [c for c in cand_s if s0 + EDGE_GAP < c < s1 - EDGE_GAP]
    chosen: list[float] = []
    def run(ch):
        pts = [endpoints[0]] + [inv(P[np.searchsorted(S, c)]) for c in ch] + [endpoints[1]]
        r = route(pts)
        if r is None: return 0.0, None, None, None
        f, m, _ = fidelity(sub, proj(r, lat0))
        return f, m, r, pts
    f, m, r, _ = run(chosen); tried = set()
    while len(chosen) < limit and f < target and m is not None:
        improved = False
        for L, a, b in gaps(m, sub_s)[:6]:
            pool = [c for c in cs if c not in chosen and c not in tried and a - 150 <= c <= b + 150]
            if not pool: continue
            # Reihenfolge: Mitte, Anfang, Ende des Abschnitts (Anfang "verpflichtet" die Abzweigung)
            picks = []
            for t in ((a + b) / 2, a + 60, b - 60):
                c = min(pool, key=lambda x: abs(x - t))
                if c not in picks: picks.append(c)
            for c in picks:
                f2, m2, r2, _ = run(sorted(chosen + [c]))
                if f2 > f + 1e-4:
                    chosen = sorted(chosen + [c]); f, m, r = f2, m2, r2; improved = True; break
                tried.add(c)
            if improved: break
        if not improved: break
    return chosen, f, r

def unproj(q, lat0):
    k = 111320.0; return (round(float(q[0] / (k * math.cos(math.radians(lat0)))), 6), round(float(q[1] / k), 6))

def plan(route, orig_ll, edges_s, limit, target=TARGET, max_stages=3):
    """Gesamtplan für eine Runde und Ziel-App. Rückgabe dict:
    single: {wp:[(lon,lat)], f}, stages: None | {f, legs:[{wp, end:(lon,lat), f}]}"""
    lat0 = float(np.mean(np.asarray(orig_ll)[:, 1])); full = proj(orig_ll, lat0); P, _, S = densify(full); total = S[-1]
    cand = candidates(edges_s); at = lambda s: unproj(P[min(np.searchsorted(S, s), len(P) - 1)], lat0)
    ch, f, r = plan_leg(route, orig_ll, cand, 0.0, total, limit, target, lat0)
    res = {"single": {"wp": [at(c) for c in ch], "f": round(f, 3)}, "stages": None}
    if f >= target: return res
    best = None
    for n in range(2, max_stages + 1):
        cuts = [0.0] + [float(min(cand, key=lambda c: abs(c - total * i / n))) if len(cand) else total * i / n for i in range(1, n)] + [total]
        legs = []; routes = []
        for a, b in zip(cuts[:-1], cuts[1:]):
            c2, f2, r2 = plan_leg(route, orig_ll, cand, a, b, limit, target, lat0)
            legs.append({"wp": [at(c) for c in c2], "end": at(b) if b < total else at(total), "f": round(f2, 3)}); routes.append(r2)
        if any(x is None for x in routes): continue
        fr, _, _ = fidelity(full, proj([p for x in routes for p in x], lat0))
        cand_res = {"f": round(fr, 3), "legs": legs}
        if best is None or fr > best["f"] + 1e-3: best = cand_res
        if fr >= target: break
    if best and best["f"] >= f + 0.02: res["stages"] = best
    return res
