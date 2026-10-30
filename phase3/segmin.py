"""Datengrundlage Mindestlänge Highlight-Abschnitte: Länge guter Läufe (Score>=hl_min_score, Lücken<=hl_gap_m verschmolzen)
in Referenzstrecken und in gültigen Runden. Ausgabe out/segment_mindestlaenge.json"""
import sys,json,pickle,numpy as np
sys.path.insert(0,'/workspace/scenic')
from scipy.sparse.csgraph import dijkstra
from pyproj import Transformer
from scenic import api,profiles,character
from scenic.settings import OUTDIR,CACHE
exec(open('/workspace/scenic/phase3/refcheck.py').read().split('# Längen')[0].split('E,RG=api.prepare')[1].split('\n',1)[1]) if False else None
src=open('/workspace/scenic/phase3/refcheck.py').read(); REFS=eval(src[src.index('REFS=')+5:src.index('# Längen')])
p=profiles.get(hl_min_m=0); E,RG=api.prepare(p); tf=Transformer.from_crs(4326,25832,always_xy=True)
Ct=RG.C.copy(); Ct.data=RG.len[RG.keid]+1e-6
def good_lengths(eids):
    c=character.analyze(RG,E,dict(eids=eids,score=0.2,len_km=1,time_min=1,town_share=0,b_share=0),with_view=False)
    return [(g['len_m'],round(g['score'],3),g['name']) for g in c['segments'] if g['kind']=='highlight']
res={'referenzen':{}}
for nm,wps in REFS.items():
    nodes=[RG.nearest(la,lo,tf) for la,lo in wps]; eids=[]
    for a,b in zip(nodes[:-1],nodes[1:]):
        D,P=dijkstra(Ct,indices=a,return_predecessors=True); path=[b]
        while path[-1]!=a: path.append(P[path[-1]])
        path=path[::-1]; eids+=list(RG.edge_ids(np.array(path[:-1]),np.array(path[1:])))
    g=good_lengths(eids); res['referenzen'][nm]=dict(laengster_guter_lauf_m=max([x[0] for x in g],default=0),laeufe=g)
    print(nm,res['referenzen'][nm]['laengster_guter_lauf_m'],g[:5])
R=pickle.load(open(f'{CACHE}/{sys.argv[1] if len(sys.argv)>1 else "routes_all.pkl"}','rb'))['routes']
V=[r for r in R if api.passes(r,profiles.get())]; rng=np.random.default_rng(0)
L=[]
for i in rng.choice(len(V),min(150,len(V)),replace=False): L+=[x[0] for x in good_lengths(V[i]['eids'])]
L=np.array(L); res['runden']=dict(n_laeufe=len(L),perzentile={q:float(np.percentile(L,q)) for q in (10,25,50,75,90)},
   anteil_laenge_unter={t:float(L[L<t].sum()/L.sum()) for t in (500,800,1000,1500,2000,3000)},anzahl_unter={t:int((L<t).sum()) for t in (500,800,1000,1500,2000,3000)})
print(res['runden'])
json.dump(res,open(f'{OUTDIR}/segment_mindestlaenge.json','w'),ensure_ascii=False,indent=1)
