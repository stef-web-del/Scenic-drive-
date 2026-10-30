"""Datenexport für die Web-App (webapp/public/data). Benötigt cache/char_all.pkl (char_all.py).
Ausgabe: meta.json (Runden-Kennzahlen, Familien), lines.json (vereinfachte Geometrien), layers.json (Heatmap, Referenzen, Pausen),
r/s<id%32>.json ({id: Detail}, 32 Shards; Detail: Geometrie ~10 m, Höhenprofil, Pausen, Highlights, Google-Maps-Wegpunkte)."""
import sys,os,json,pickle,numpy as np,pandas as pd,shapely
from shapely.geometry import LineString
sys.path.insert(0,'/workspace/scenic')
from scipy.sparse.csgraph import dijkstra
from pyproj import Transformer
from scenic import api,profiles
from scenic.search import cluster
from scenic.character import elev
from scenic.settings import OUTDIR
CACHE='/workspace/scenic/phase3/cache'; OUT='/workspace/scenic/phase3/out/web_raw'; os.makedirs(f'{OUT}/r',exist_ok=True)  # danach: python webapp/scripts/compact.py
START=[9.2248586,49.7009613]; LEVELS=[0.15,0.25,0.35,0.5,0.7]
E,RG=api.prepare(profiles.get()); R=pickle.load(open(f'{CACHE}/routes_all.pkl','rb'))['routes']
V=pd.read_csv(f'{OUTDIR}/gueltige_runden.csv'); C=pickle.load(open(f'{CACHE}/char_all.pkl','rb'))
tf=Transformer.from_crs(4326,25832,always_xy=True); inv=Transformer.from_crs(25832,4326,always_xy=True)
def xy(coords): return np.column_stack(tf.transform(*zip(*coords)))
def ll(line): lo,la=inv.transform(*zip(*line.coords)); return [[round(a,5),round(b,5)] for a,b in zip(lo,la)]
def J(o,p): json.dump(o,open(p,'w'),ensure_ascii=False,separators=(',',':'))
routes=[R[i] for i in V.i]; fam={t:cluster(RG,routes,t) for t in LEVELS}
meta=[]; lines={}; pauses={}
SH={}
for k,row in enumerate(V.itertuples()):
    c=C[row.i]; P=xy(c['coords']); g=LineString(P)
    lines[int(row.i)]=[[round(a,4),round(b,4)] for a,b in ll(g.simplify(35))]
    # Höhenprofil alle 100 m
    d=np.arange(0,g.length,100.0); pts=np.array([g.interpolate(x).coords[0] for x in d]); z=elev(pts)
    z=pd.Series(z).interpolate().bfill().ffill().round().astype(int).tolist()
    # Google-Maps-Wegpunkte: 8 gleichmäßig verteilte Punkte
    wp=[inv.transform(*g.interpolate(f,normalized=True).coords[0]) for f in np.linspace(0,1,10)[1:-1]]
    ps=[]
    for p in (c.get('pauses') or []):
        lo,la=p['lonlat']; ps.append(dict(n=p['name'],k=p['kind'],km=float(p['km']),ll=[round(lo,5),round(la,5)]))
        q=pauses.setdefault(p['name'],dict(n=p['name'],k=p['kind'],ll=[round(lo,5),round(la,5)],c=0)); q['c']+=1
    hl=[dict(km=round(float(h['km0']),1),len=int(h['len_m']),s=round(float(h['score']),3)) for h in (c.get('highlights') or [])]
    SH.setdefault(int(row.i)%32,{})[str(int(row.i))]=(dict(id=int(row.i),g=ll(g.simplify(8)),prof=z,step=100,pauses=ps,hl=hl,orte=c.get('orte') or [],wp=[[round(a,5),round(b,5)] for a,b in wp]))
    meta.append(dict(id=int(row.i),km=round(row.len_km,1),min=int(round(row.time_min)),s=round(row.score,3),t=round(c.get('tour_score') or 0,3),
        kl=row.klasse.split()[0],rg=row.region,hm=int(round(c.get('ascent_m') or row.ascent_est)),tags=c.get('tags') or [],tx=c.get('text') or '',
        f=[int(fam[t][0][k]) for t in LEVELS],r=[int(k in set(fam[t][1])) for t in LEVELS],
        bb=[round(float(x),4) for x in (lambda a:[a[:,0].min(),a[:,1].min(),a[:,0].max(),a[:,1].max()])(np.array(lines[int(row.i)]))]))
maj=E.highway.isin(['primary','secondary','tertiary','unclassified','trunk']).values
H=E[maj&(E.score.values>0.2)].to_crs(25832) if E.crs and E.crs.to_epsg()!=25832 else None
heat={'type':'FeatureCollection','features':[]}
for _k,_v in SH.items(): J(_v,f"{OUT}/r/s{_k}.json")
for g,s in zip(E[maj&(E.score.values>0.2)].geometry,E[maj&(E.score.values>0.2)].score):
    heat['features'].append(dict(type='Feature',properties=dict(s=round(float(s),2)),geometry=dict(type='LineString',coordinates=ll(LineString(xy(list(g.coords))).simplify(20)))))
REFS={'Miltenberg → Mainbullau (Anstieg)':[(49.7003,9.2390),(49.6908,9.1861)],
 'Windischbuchener Steige → Beuchen':[(49.6581,9.3407),(49.6002,9.2305)],
 'Windischbuchen → Wenschdorf':[(49.6581,9.3407),(49.6791,9.2875)],
 'Collenberg → Mönchberg':[(49.7733,9.3267),(49.7922,9.2669)],
 'Kaltenbrunn → Reinhardsachsen → Gerolzahn (BW)':[(49.648,9.3589),(49.6407,9.3443),(49.6258,9.3189)],
 'Miltenberg → Wenschdorf → Geisenhof (St 2309)':[(49.7018,9.2560),(49.7006,9.2755),(49.6572,9.3069)]}
Ct=RG.C.copy(); Ct.data=E.len_m.values[RG.keid]+1e-6; refs={'type':'FeatureCollection','features':[]}
for nm,wps in REFS.items():
    nodes=[RG.nearest(la,lo,tf) for la,lo in wps]; eids=[]
    for a,b in zip(nodes[:-1],nodes[1:]):
        D,Pr=dijkstra(Ct,indices=a,return_predecessors=True); path=[b]
        while path[-1]!=a: path.append(Pr[path[-1]])
        path=path[::-1]; eids+=list(RG.edge_ids(np.array(path[:-1]),np.array(path[1:])))
    m=shapely.line_merge(shapely.MultiLineString([E.geometry.iat[e] for e in eids])); parts=list(m.geoms) if hasattr(m,'geoms') else [m]
    refs['features'].append(dict(type='Feature',properties=dict(n=nm),geometry=dict(type='MultiLineString',coordinates=[ll(LineString(xy(list(p.coords))).simplify(10)) for p in parts])))
pz={'type':'FeatureCollection','features':[dict(type='Feature',properties=dict(n=p['n'],k=p['k'],c=p['c']),geometry=dict(type='Point',coordinates=p['ll'])) for p in sorted(pauses.values(),key=lambda p:-p['c'])]}
J(dict(levels=LEVELS,start=START,loops=meta),f'{OUT}/meta.json'); J(lines,f'{OUT}/lines.json'); J(dict(heat=heat,refs=refs,pauses=pz),f'{OUT}/layers.json')
print(len(meta),'Runden', {f:os.path.getsize(f'{OUT}/{f}')//1024 for f in ('meta.json','lines.json','layers.json')})
