"""Regressionstest Qualität: Runden über die dokumentierten Referenzstrecken (Logbuch 28.10.: Mainbullau-Anstieg,
Windischbuchener Steige -> Beuchen, Strecken um Windischbuchen -> Wenschdorf, Collenberg -> Mönchberg) müssen weiter hoch
punkten. Die Strecken sind Gefühl, keine Pflichtabschnitte: geprüft wird nur, dass sie und Runden über sie gut bewertet sind.
  venv/bin/python -m pytest -q tests/test_referenzen.py        (~1,5 min; -m "not slow" nur der schnelle Teil)
Schwellen = Stand 29.10.2026 mit Sicherheitsabstand (refcheck: 77/68/78/95 Perzentil bester 3-km-Abschnitt)."""
import sys,os; sys.path.insert(0,os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import numpy as np, pytest
from scipy.sparse.csgraph import dijkstra
from pyproj import Transformer
from scenic import api,profiles,ranking
from scenic.settings import START
REFS={'Miltenberg → Mainbullau':([(49.7003,9.2390),(49.6908,9.1861)],65),
      'Windischbuchener Steige → Beuchen':([(49.6581,9.3407),(49.6002,9.2305)],55),
      'Windischbuchen → Wenschdorf':([(49.6581,9.3407),(49.6791,9.2875)],65),
      'Collenberg → Mönchberg':([(49.7733,9.3267),(49.7922,9.2669)],85)}
tf=Transformer.from_crs(4326,25832,always_xy=True)
@pytest.fixture(scope='module')
def ERG(): return api._prep_cached(profiles.get())
def ref_eids(RG,wps):
    Ct=RG.C.copy(); Ct.data=RG.len[RG.keid]+1e-6; nodes=[RG.nearest(la,lo,tf) for la,lo in wps]; out=[]
    for a,b in zip(nodes[:-1],nodes[1:]):
        _,P=dijkstra(Ct,indices=a,return_predecessors=True); p=[b]
        while p[-1]!=a: p.append(P[p[-1]])
        p=p[::-1]; out+=list(RG.edge_ids(np.array(p[:-1]),np.array(p[1:])))
    return np.array(out)
def best3(E,e):
    L=E.len_m.values[e]; cs=np.r_[0,np.cumsum(L)]; cc=np.r_[0,np.cumsum(E.score.values[e]*L)]; b=0
    for i in range(len(e)):
        j=np.searchsorted(cs,cs[i]+3000)
        if j<len(cs): b=max(b,(cc[j]-cc[i])/(cs[j]-cs[i]))
    return b
@pytest.mark.parametrize('name',list(REFS))
def test_referenz_abschnitt_punktet_hoch(ERG,name):
    E,RG=ERG; wps,thr=REFS[name]; e=ref_eids(RG,wps)
    maj=E.highway.isin(['primary','secondary','tertiary','unclassified','primary_link','secondary_link','tertiary_link','trunk']).values
    v=E.score.values[maj]; w=E.len_m.values[maj]; o=np.argsort(v); cw=np.cumsum(w[o])/w.sum()
    pct=100*np.interp(best3(E,e),v[o],cw)
    assert pct>=thr, f'{name}: bester 3-km-Abschnitt nur Perzentil {pct:.0f} (Soll >= {thr})'
# Stand 29.10.2026 (OBI, 45–60 min, 6000 Kombis, 99 gültige Runden): bester Rang einer Runde, die >=1,5 km auf der
# Referenz fährt (40-m-Korridor): Mainbullau 0,99 · Wenschdorf 0,94 · Beuchen 0,62 (Perzentil Tour-Score).
# Collenberg → Mönchberg liegt ab OBI außerhalb 45–60 min und wird hier nicht geprüft (nur Abschnittstest oben).
LOOP_THR={'Miltenberg → Mainbullau':0.85,'Windischbuchen → Wenschdorf':0.8,'Windischbuchener Steige → Beuchen':0.5}
@pytest.mark.slow
def test_runden_ueber_referenzen_oben():
    from shapely.geometry import LineString
    from shapely.ops import unary_union
    from scenic.character import analyze
    p=profiles.get(min_min=45,max_min=60); E,RG=api._prep_cached(p)
    E,RG,S,routes=api.evaluate_all(START,p,k_max=3,max_combos=6000,E_RG=(E,RG),repair_top=1000)
    ok=[r for r in routes if api.passes(r,p)]; assert len(ok)>=50
    C=[analyze(RG,E,r) for r in ok]; t=np.array([c['tour_score'] for c in C])
    tr=lambda c: LineString(np.column_stack(tf.transform(*zip(*c)))); G=[tr(c['coords']) for c in C]; hit_any=set()
    for n,thr in LOOP_THR.items():
        b=unary_union([tr(list(E.geometry.iat[x].coords)) for x in ref_eids(RG,REFS[n][0])]).buffer(40)
        idx=[i for i,g in enumerate(G) if g.intersection(b).length>=1500]; hit_any|=set(idx)
        assert idx, f'{n}: keine gültige Runde fährt die Referenz'
        best=max((t<t[i]).mean() for i in idx)
        assert best>=thr, f'{n}: beste Referenz-Runde nur Perzentil {best:.2f} (Soll >= {thr})'
    sel=ranking.select(ok,C,lambda a,b:0.0,keep=20)
    for st,L in sel.items(): assert any(d['i'] in hit_any for d in L), f'{st}: keine Referenz-Runde unter den Top 20'
def test_fahrstile_messbar():
    assert set(ranking.PROFILES)=={'sportlich','geniesser','entdecker'}
    for pr in ranking.PROFILES.values():
        assert abs(sum(pr['w'].values())-1)<1e-9 and set(pr['w'])<=set(ranking.METRICS)
def test_auswahl_ueberschneidung_und_gruende():
    rng=np.random.default_rng(1); n=40
    C=[dict(curv_mean=rng.random(),relief_mean=rng.random(),ascent_m=300+500*rng.random(),view_frac=rng.random(),highlight_share=rng.random(),
            rhythm_mean=rng.random(),open=.3,forest=.3,town_share=.3*rng.random(),interrupts_per10km=5*rng.random(),orte=['a']*int(10*rng.random()),
            pauses=[],tour_score=rng.random()) for _ in range(n)]
    J=lambda a,b: 0.5 if (a%5)==(b%5) else 0.1
    S=ranking.select(list(range(n)),C,J,ov=0.25,keep=20)
    for L in S.values():
        assert [d['rank'] for d in L]==list(range(1,len(L)+1)) and 15<=len(L)<=20
        top=[d['i'] for d in L[:5]]; assert len({x%5 for x in top})==5   # 5 Gruppen à 50 % Überschneidung -> erst 5 verschiedene
        assert all(d.get('why') for d in L[5:]) and not any('why' in d for d in L[:5])
