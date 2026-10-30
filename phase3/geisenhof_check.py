import sys,numpy as np; sys.path.insert(0,'/workspace/scenic')
from scipy.sparse.csgraph import dijkstra
from pyproj import Transformer
from scenic import api,profiles
from scenic.character import places
E,RG=api.prepare(profiles.get()); tf=Transformer.from_crs(4326,25832,always_xy=True); inv=Transformer.from_crs(25832,4326,always_xy=True)
pl,_=places(); s=pl[pl.name.str.contains('Geisen|Wensch',na=False)]
for n,g in zip(s.name,s.geometry): print(n, inv.transform(g.x,g.y))
L=E.len_m.values; isB=(E.ref.fillna('').str.startswith('B ')|E.highway.isin(['primary','trunk'])).values
maj=E.highway.isin(['primary','secondary','tertiary','unclassified','primary_link','secondary_link','tertiary_link','trunk']).values
def wpct(vals,x):
    v=vals[maj]; w=L[maj]; o=np.argsort(v); cw=np.cumsum(w[o])/w.sum(); return float(100*np.interp(x,v[o],cw))
def run(wps,blockB):
    Ct=RG.C.copy(); c=L[RG.keid]+1e-6
    if blockB: c=c*np.where(isB[RG.keid],1000,1)
    Ct.data=c; nodes=[RG.nearest(la,lo,tf) for la,lo in wps]; eids=[]
    for a,b in zip(nodes[:-1],nodes[1:]):
        D,P=dijkstra(Ct,indices=a,return_predecessors=True); path=[b]
        while path[-1]!=a:
            if P[path[-1]]<0: print('unreachable',a,b); return
            path.append(P[path[-1]])
        path=path[::-1]; eids+=list(RG.edge_ids(np.array(path[:-1]),np.array(path[1:])))
    e=np.array(eids); w=L[e]; roads=[]
    for x in e:
        r=E.ref.iat[x] if isinstance(E.ref.iat[x],str) else (E.name.iat[x] if isinstance(E.name.iat[x],str) else None)
        if r and r not in roads: roads.append(r)
    cs=np.r_[0,np.cumsum(w)]; csc=np.r_[0,np.cumsum(E.score.values[e]*w)]; best=0
    for i in range(len(e)):
        j=np.searchsorted(cs,cs[i]+3000)
        if j<len(cs): best=max(best,(csc[j]-csc[i])/(cs[j]-cs[i]))
    sc=(E.score.values[e]*w).sum()/w.sum()
    print(blockB, round(w.sum()/1000,1),'B-km',round(w[isB[e]].sum()/1000,1),'score',round(sc,3),'P',round(wpct(E.score.values,sc)),'best3',round(best,3),'P',round(wpct(E.score.values,best)),roads)
W=[(49.7018,9.2560),(49.6589,9.3062),(49.6791,9.2875)]

m=E.ref.fillna('').eq('St 2309').values
import shapely
for i in np.where(m)[0]:
    c=E.geometry.iat[i].coords[0]
    if 49.66<c[1]<49.70: print('st2309',round(c[1],4),round(c[0],4),E.name.iat[i])
print('NEU'); run([(49.7018,9.2560),(49.7006,9.2755),(49.6572,9.3069)],False)
