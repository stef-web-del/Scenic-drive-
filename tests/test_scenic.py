"""Kurztests:  venv/bin/python -m pytest -q tests   (≈1 min)"""
import sys; sys.path.insert(0,'/workspace/scenic')
import numpy as np, pytest
from scenic import api,profiles
from scenic.search import Searcher,metrics
from scenic.settings import START
@pytest.fixture(scope='module')
def ERG(): return api.prepare(profiles.get())
def test_profiles():
    a,m=profiles.get('auto_sport'),profiles.get('motorrad'); assert a.hairpin_factor<m.hairpin_factor
    assert profiles.get(max_b_share=0.05).max_b_share==0.05
def test_scores_range(ERG):
    E,_=ERG; assert E.score.between(0,1).all() and E.sec_score.notna().all()
def test_oneway_respected(ERG):
    E,RG=ERG; ow=E.index[E.oneway.isin(['yes','true','1'])&~E.junction.eq('roundabout')][:50]
    nodes={x:i for i,x in enumerate(RG.nodes)}
    for e in ow: assert RG.edge_id(nodes[E.v[e]],nodes[E.u[e]])!=e
def test_loop_closed_and_metrics(ERG):
    E,RG=ERG; S=Searcher(RG,START); c=S.candidates(2,max_combos=200)
    e=S.repair(c[0]); m=metrics(RG,e)
    assert 0<=m['overlap']<=1 and m['len_km']>5
    g=E.geometry.iloc[e]; assert g.iloc[0].distance(g.iloc[-1])<0.01   # Start=Ziel
def test_find_loops_fast():
    loops,info=api.find_loops(START,n=3,max_combos=800)
    assert info['valid']>0 and len(loops)>=1
    for r in loops: assert 45<=r['time_min']<=60 and r['b_share']<=0.12 and r['char']['text']
from scenic.search import backtrack
from scenic.character import merge_runs
def test_backtrack_detects_stub(ERG):
    E,RG=ERG; S=Searcher(RG,START); c=S.candidates(2,max_combos=200)
    e=next(x for x in (S.repair(o) for o in c) if x)
    assert not backtrack(RG,e,S._zone())['bad']
    k=len(e)//2; stub=e[:k]+[e[k],e[k]]+e[k:]          # Wende: hin und gleiche Kante zurück
    b=backtrack(RG,stub,S._zone()); assert b['bad'] and b['uturns']>=1
def test_repair_never_backtracks(ERG):
    E,RG=ERG; S=Searcher(RG,START)
    for o in S.candidates(3,max_combos=60)[:60]:
        e=S.repair(o)
        if e: assert not backtrack(RG,e,S._zone())['bad']
def test_legacy_mode_switch():
    assert profiles.get(no_backtrack=False).no_backtrack is False and profiles.get().start_zone_m==1500
def test_merge_runs_min_length():
    # Einheit Stützpunkte à 25 m: 30 gut (750 m), 8 Lücke (200 m), 40 gut (1000 m) -> verschmolzen 1950 m = Highlight
    r=merge_runs([[0,30,'good'],[30,38,'normal'],[38,78,'good'],[78,100,'transfer']],gap=12,min_len=60)
    assert r[0]==[0,78,'highlight'] and r[1][2]=='transfer'
    # 750 m allein ist zu kurz
    r=merge_runs([[0,30,'good'],[30,80,'normal'],[80,90,'good']],gap=12,min_len=60)
    assert all(k!='highlight' for _,_,k in r)
def test_all_results_no_backtrack():
    """Globaler Check: keine gültige Runde der Vollauswertung enthält eine Stichfahrt."""
    import pickle,os
    from scenic.settings import CACHE
    f=f'{CACHE}/routes_all.pkl'
    if not os.path.exists(f): pytest.skip('keine Vollauswertung')
    p=profiles.get(); E,RG=api.prepare(p); R=pickle.load(open(f,'rb'))['routes']
    V=[r for r in R if api.passes(r,p)]; assert V
    assert all(not backtrack(RG,r['eids'])['bad'] for r in V)
