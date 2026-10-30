"""Lenkpunkt-Algorithmus (scenic/navpoints.py) mit Fake-Router (Luftlinie) – ohne Valhalla."""
import sys, math; sys.path.insert(0, '/workspace/scenic')
import numpy as np
from scenic import navpoints as NP

LAT0 = 49.7
def ll(xy):  # Meter -> lon/lat
    k = 111320.0; return [(x / (k * math.cos(math.radians(LAT0))), LAT0 + y / k) for x, y in xy]
# Zickzack-Runde: 8 Ecken, Kanten à 1 km, Start = Ziel
CORNERS = [(0, 0), (1000, 300), (2000, 0), (3000, 300), (3000, 1300), (2000, 1600), (1000, 1300), (0, 1600), (0, 0)]
G = ll(CORNERS)
def edges():
    s, out = 0.0, []
    for a, b in zip(CORNERS[:-1], CORNERS[1:]):
        L = math.dist(a, b); out.append((s, s + L)); s += L
    return out
def straight(pts):
    """Fake-Router: folgt der Runde, wenn zwei aufeinanderfolgende Punkte <= 2,5 km (entlang der Runde) auseinander
    liegen, sonst Abkürzung per Luftlinie – modelliert 'Router kürzt ab, wenn Lenkpunkte fehlen'."""
    xy = NP.proj(G, LAT0); P, _, S = NP.densify(xy); from scipy.spatial import cKDTree
    q = NP.proj(pts, LAT0); _, idx = cKDTree(P).query(q); idx = list(idx); idx[-1] = len(P) - 1 if len(pts) > 1 and idx[-1] == 0 else idx[-1]
    out = [q[0]]
    for a, b in zip(idx[:-1], idx[1:]):
        if b >= a and S[b] - S[a] <= 2500: out.extend(P[a:b + 1])
        else: out.append(P[b])
    k = 111320.0; return [(x / (k * math.cos(math.radians(LAT0))), y / k) for x, y in out]

def test_densify_and_coverage_identity():
    xy = NP.proj(G, LAT0); f, m, _ = NP.fidelity(xy, xy)
    assert f == 1.0 and m.all()

def test_fidelity_penalizes_detour_both_ways():
    xy = NP.proj(G, LAT0); short = xy[[0, 4, 8]]  # Abkürzung
    f, _, _ = NP.fidelity(xy, short); assert f < 0.6
    detour = np.vstack([xy, xy[-1] + [0, -3000], xy[-1]])  # Umweg am Ende (Hin und zurück)
    f2, _, _ = NP.fidelity(xy, detour); assert f2 < 0.9

def test_candidates_avoid_junctions():
    c = NP.candidates([(0, 50), (50, 400), (400, 1400)])
    assert list(c) == [225.0, 900.0]  # zu kurze Kante (50 m) verworfen, Mitten statt Enden
    for x, (a, b) in zip(c, [(50, 400), (400, 1400)]): assert x - a >= NP.EDGE_GAP and b - x >= NP.EDGE_GAP

def test_plan_reaches_target_with_ordered_points_within_limit():
    r = NP.plan(straight, G, edges(), limit=9)
    s = r["single"]; assert s["f"] >= NP.TARGET and 1 <= len(s["wp"]) <= 9 and r["stages"] is None
    # Fahrtrichtung: Punkte in Reihenfolge entlang der Runde
    xy = NP.proj(G, LAT0); P, _, S = NP.densify(xy); from scipy.spatial import cKDTree
    _, idx = cKDTree(P).query(NP.proj(s["wp"], LAT0)); assert list(idx) == sorted(idx)

def test_plan_monotone_in_limit_and_stages_when_limit_too_small():
    f = [NP.plan(straight, G, edges(), limit=k)["single"]["f"] for k in (1, 3, 9)]
    assert f[0] <= f[1] <= f[2]
    r = NP.plan(straight, G, edges(), limit=1)
    assert r["single"]["f"] < NP.TARGET and r["stages"] is not None
    st = r["stages"]; assert 2 <= len(st["legs"]) <= 3 and st["f"] >= r["single"]["f"] + 0.02
    assert all(len(l["wp"]) <= 1 for l in st["legs"])
    end = st["legs"][-1]["end"]; assert math.dist(end, G[0]) < 1e-4  # letzte Etappe endet am Start

def test_router_failure_gives_zero():
    r = NP.plan(lambda pts: None, G, edges(), limit=3)
    assert r["single"]["f"] == 0.0 and r["single"]["wp"] == []
