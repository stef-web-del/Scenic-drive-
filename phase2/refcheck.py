"""Validierung: Referenzabschnitte (zeitkürzester Weg zwischen Orten) -> Score-Perzentil und Abdeckung in Top-Runden"""
import json, numpy as np, networkx as nx, os, sys
import loops as L
from pyproj import Transformer
REFS={ # Name: Liste von (lat,lon)-Wegpunkten
 'Miltenberg–Mainbullau':[(49.7005,9.2390),(49.6908,9.1861)],
 'Windischbuchener Steige → Beuchen':[(49.6581,9.3407),(49.6002,9.2305)],
 'Windischbuchen → Wenschdorf (Bensdorf?)':[(49.6581,9.3407),(49.6791,9.2875)],
 'Collenberg–Mönchberg':[(49.7733,9.3267),(49.7922,9.2669)],
 'Kaltenbrunn–Reinhardsachsen–Gerolzahn (BW, GLO-30)':[(49.648,9.3589),(49.6407,9.3443),(49.6258,9.3189)],
}
def node(lat,lon):
    x,y=L.tf.transform(lon,lat); return tuple(L.nodes[np.argmin((L.nx_-x)**2+(L.ny_-y)**2)])
def tpath(a,b): return L.path_edges(nx.dijkstra_path(L.G,a,b,weight=lambda u,v,d:L.tm[d['eid']]))
def refs():
    out={}
    for k,pts in REFS.items():
        e=[];ns=[node(*p) for p in pts]
        for a,b in zip(ns[:-1],ns[1:]): e+=tpath(a,b)
        out[k]=e
    return out
if __name__=='__main__':
    R=refs(); allsc=L.sc; w=L.ln
    top=json.load(open(f'{L.OUT}/loops.json'))['top']
    rep={}
    for k,e in R.items():
        s=(L.sc[e]*L.ln[e]).sum()/L.ln[e].sum(); m=~L.E.highway.isin(['residential','living_street']).values; pct=(L.ln[m]*(allsc[m]<s)).sum()/L.ln[m].sum()*100
        names=sorted(set(str(x) for x in L.E.loc[e,'name'].dropna()))[:6]
        cov=[round(L.ln[[x for x in e if x in set(t['eids'])]].sum()/L.ln[e].sum(),2) for t in top]
        rep[k]=dict(km=round(L.ln[e].sum()/1000,1),score=round(s,3),perzentil=round(pct),namen=names,abdeckung_top=cov,eids=e)
        print(k,{a:b for a,b in rep[k].items() if a!='eids'})
    json.dump(rep,open(f'{L.OUT}/refcheck.json','w'),default=int,ensure_ascii=False,indent=1)
