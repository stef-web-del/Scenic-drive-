"""Referenzstrecken: Kanten-/Abschnitts-Score und Perzentil (längengewichtet) gegenüber allen Haupt-/Nebenstraßen."""
import sys,json,numpy as np,scipy.sparse as sp
sys.path.insert(0,'/workspace/scenic')
from scipy.sparse.csgraph import dijkstra
from pyproj import Transformer
from scenic import api,profiles
from scenic.settings import OUTDIR
E,RG=api.prepare(profiles.get()); tf=Transformer.from_crs(4326,25832,always_xy=True)
REFS={'Miltenberg → Mainbullau (Anstieg)':[(49.7003,9.2390),(49.6908,9.1861)],
 'Windischbuchener Steige → Beuchen':[(49.6581,9.3407),(49.6002,9.2305)],
 'Windischbuchen → Wenschdorf':[(49.6581,9.3407),(49.6791,9.2875)],
 'Collenberg → Mönchberg':[(49.7733,9.3267),(49.7922,9.2669)],
 'Kaltenbrunn → Reinhardsachsen → Gerolzahn (BW)':[(49.648,9.3589),(49.6407,9.3443),(49.6258,9.3189)],
 'Miltenberg → Geisenhof → Wenschdorf (neu)':[(49.7018,9.2560),(49.6589,9.3062),(49.6791,9.2875)]}
# Längen-kürzester Pfad für die Referenzgeometrie (nicht score-gewichtet!)
Ct=RG.C.copy(); Ct.data=RG.len[RG.keid]+1e-6 if len(RG.keid)==len(Ct.data) else Ct.data
maj=E.highway.isin(['primary','secondary','tertiary','unclassified','primary_link','secondary_link','tertiary_link','trunk']).values
L=E.len_m.values
def wpct(vals,x,mask):
    v=vals[mask]; w=L[mask]; o=np.argsort(v); cw=np.cumsum(w[o])/w.sum(); return float(100*np.interp(x,v[o],cw))
res={}
for nm,wps in REFS.items():
    nodes=[RG.nearest(la,lo,tf) for la,lo in wps]; eids=[]
    for a,b in zip(nodes[:-1],nodes[1:]):
        D,P=dijkstra(Ct,indices=a,return_predecessors=True); path=[b]
        while path[-1]!=a: path.append(P[path[-1]])
        path=path[::-1]; eids+=list(RG.edge_ids(np.array(path[:-1]),np.array(path[1:])))
    e=np.array(eids); w=L[e]; f=lambda c:float((E[c].values[e]*w).sum()/w.sum())
    roads=[]; 
    for x in e:
        r=E.ref.iat[x] or E.name.iat[x]
        if r and r not in roads: roads.append(r)
    cs=np.r_[0,np.cumsum(w)]; csc=np.r_[0,np.cumsum(E.score.values[e]*w)]; best=0
    for i in range(len(e)):
        j=np.searchsorted(cs,cs[i]+3000)
        if j<len(cs): best=max(best,(csc[j]-csc[i])/(cs[j]-cs[i]))
    if best==0: best=f('score')
    res[nm]=dict(best3km=round(best,3),pct_best3km=round(wpct(E.score.values,best,maj)),len_km=round(w.sum()/1000,1),score=round(f('score'),3),edge=round(f('edge_score'),3),section=round(f('sec_score'),3),curv=round(f('curv_deg_km'),0),
       pct_score=round(wpct(E.score.values,f('score'),maj)),pct_section=round(wpct(E.sec_score.values,f('sec_score'),maj)),strassen=roads[:6],
       town_share=round(float((RG.town[e]*w).sum()/w.sum()),2))
    print(nm,res[nm])
json.dump(res,open(f'{OUTDIR}/referenzen.json','w'),ensure_ascii=False,indent=1)
