import numpy as np
"""API-Schicht: find_loops(start, params) -> Liste von Runden mit Kennzahlen."""
import os, numpy as np, geopandas as gpd
from .settings import CACHE
from . import profiles, scoring
from .graph import RoadGraph
from .search import Searcher, metrics, cluster, backtrack
_F=None
def features():
    global _F
    if _F is None:
        f=f'{CACHE}/features.gpkg'
        if not os.path.exists(f):
            from .features import build_features; build_features()
        _F=gpd.read_file(f)
    return _F.copy()
def prepare(params):
    E=scoring.score_all(features(),params); return E,RoadGraph(E,params)
def evaluate_all(start,params,k_max=3,max_combos=None,E_RG=None,repair=True,repair_top=None):
    """repair_top: nur die besten N Baum-Kandidaten (nach Score der unreparierten Runde) reparieren (Schnellmodus)."""
    E,RG=E_RG or prepare(params); S=Searcher(RG,start); orders=S.candidates(k_max,max_combos)
    if repair and repair_top and len(orders)>repair_top:
        est=[]
        for o in orders:
            e=S.build(o); m=metrics(RG,e) if e is not None else None
            est.append(-1 if m is None else m['score']*(1-m['b_share'])*(1-0.5*m['town_share']))
        orders=[orders[i] for i in np.argsort(est)[::-1][:repair_top]]
    seen=set(); routes=[]
    for o in orders:
        e=S.repair(o) if repair else S.build(o)
        if e is None: continue
        key=hash(tuple(e))
        if key in seen: continue
        seen.add(key); m=metrics(RG,e); m['backtrack']=backtrack(RG,e,S._zone()); m['eids']=e; m['anchors']=[int(S.anchor_eids[j]) for j in o]; routes.append(m)
    return E,RG,S,routes
def passes(m,p):
    """Alle Kriterien"""
    if p.no_backtrack and m.get('backtrack',{}).get('bad',False): return False
    return (p.min_min<=m['time_min']<=p.max_min and m['overlap']<=p.max_overlap and m['b_share']<=p.max_b_share
            and m['town_share']<=p.max_town_share and m['score']>=p.min_score)
def find_loops(start,profile='auto_sport',max_results=10,diversity=None,max_combos=4000,repair_top=400,characterize=True,n=None,**kw):
    """Gibt nur Familienvertreter zurück (beste Runde je Familie nach Tour-Score), max. max_results (Default 10). n = Alias."""
    if n is not None: max_results=n
    """start=(lat,lon). Schnellmodus: max_combos Wegpunkt-Kombinationen je Wegpunktanzahl.
    Gibt bis zu n verschiedene Runden zurück, sortiert nach Tour-Score v2. Zwei ausgegebene Runden überschneiden sich
    höchstens um max_jaccard (längengew. Jaccard, Default 0.25 aus Params; auch per find_loops(..., max_jaccard=0.35)).
    diversity ist der alte Name und überschreibt max_jaccard, falls gesetzt."""
    import time; T={}; t=time.time()
    p=profiles.get(profile,**kw)
    if diversity is not None: p=profiles.replace(p,max_jaccard=diversity)
    E,RG=_prep_cached(p); T['prepare']=time.time()-t; t=time.time()
    E,RG,S,routes=evaluate_all(start,p,max_combos=max_combos,E_RG=(E,RG),repair_top=repair_top); T['search']=time.time()-t; t=time.time()
    ok=[r for r in routes if passes(r,p)]
    lab,reps=cluster(RG,ok,p.max_jaccard) if ok else ([],[]); T['cluster']=time.time()-t; t=time.time()
    top=[ok[i] for i in reps[:max(n*3,10)]]
    if characterize:
        from .character import analyze
        for r in top: r['char']=analyze(RG,E,r)
        top.sort(key=lambda r:-r['char']['tour_score'])
    T['character']=time.time()-t
    return top[:max_results], dict(params=p,candidates=len(routes),valid=len(ok),clusters=len(reps),families=len(reps),timing=T,E=E,RG=RG)
_CACHE={}
def _prep_cached(p):
    """Scoring+Graph hängen nur von Gewichten ab -> pro Parametersatz cachen (Server: einmal beim Start)."""
    k=repr({k:v for k,v in p.__dict__.items() if k not in ('min_min','max_min','max_overlap','max_b_share','max_town_share','min_score','max_jaccard')})
    if k not in _CACHE: _CACHE[k]=prepare(p)
    return _CACHE[k]
