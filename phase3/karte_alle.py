"""Interaktive Karte aller gültigen Runden -> out/karte_alle_runden.html (eigenständig, Leaflet inline, Canvas-Renderer)."""
import sys,json,pickle,numpy as np,pandas as pd,shapely
from shapely.geometry import LineString
sys.path.insert(0,'/workspace/scenic')
from scipy.sparse.csgraph import dijkstra
from pyproj import Transformer
from scenic import api,profiles
from scenic.search import cluster
from scenic.settings import OUTDIR
CACHE='/workspace/scenic/phase3/cache'; START=(49.7009613,9.2248586); LEVELS=[0.15,0.25,0.35,0.5,0.7]
E,RG=api.prepare(profiles.get()); R=pickle.load(open(f'{CACHE}/routes_all.pkl','rb'))['routes']
V=pd.read_csv(f'{OUTDIR}/gueltige_runden.csv'); C=pickle.load(open(f'{CACHE}/char_all.pkl','rb'))
tf=Transformer.from_crs(4326,25832,always_xy=True); inv=Transformer.from_crs(25832,4326,always_xy=True)
def simp(coords,tol=35):
    xs,ys=tf.transform(*zip(*coords)); g=LineString(zip(xs,ys)).simplify(tol)
    lo,la=inv.transform(*zip(*g.coords)); return [[round(b,5),round(a,5)] for a,b in zip(lo,la)]
routes=[R[i] for i in V.i]
fam={}
for t in LEVELS:
    lab,reps=cluster(RG,routes,t); rs=set(reps)
    fam[t]=(lab,[k in rs for k in range(len(routes))]); print(t,len(reps),'Familien')
loops=[]; pauses={}
for k,row in enumerate(V.itertuples()):
    c=C[row.i]
    if 'err' in c: print('Fehler',row.i,c['err']); continue
    for p in (c.get('pauses') or []):
        key=p.get('name') or p.get('kind'); lo,la=p['lonlat']
        q=pauses.setdefault(key,dict(n=key,k=p.get('kind',''),ll=[round(la,5),round(lo,5)],c=0)); q['c']+=1
    loops.append(dict(id=int(row.i),km=round(row.len_km,1),min=round(row.time_min),s=round(row.score,3),t=round(c.get('tour_score') or 0,3),
        kl=row.klasse.split()[0],rg=row.region,hm=int(round(c.get('ascent_m') or row.ascent_est)),tags=c.get('tags') or [],tx=c.get('text') or '',
        f=[int(fam[t][0][k]) for t in LEVELS],r=[int(fam[t][1][k]) for t in LEVELS],g=simp(c['coords'])))
# Heatmap Straßenscore (Haupt-/Nebenstraßen, Score > 0.2)
maj=E.highway.isin(['primary','secondary','tertiary','unclassified','trunk']).values
H=E[maj&(E.score.values>0.2)]
heat=[]
for g,s in zip(H.geometry,H.score):
    cs=list(g.coords); xy=simp(cs,25) if len(cs)>2 else [[round(b,5),round(a,5)] for a,b in cs]
    heat.append([round(float(s),2),xy])
# Referenzstrecken (längen-kürzester Pfad über Stützpunkte, wie refcheck.py; Geisenhof korrigiert)
REFS={'Miltenberg → Mainbullau (Anstieg)':[(49.7003,9.2390),(49.6908,9.1861)],
 'Windischbuchener Steige → Beuchen':[(49.6581,9.3407),(49.6002,9.2305)],
 'Windischbuchen → Wenschdorf':[(49.6581,9.3407),(49.6791,9.2875)],
 'Collenberg → Mönchberg':[(49.7733,9.3267),(49.7922,9.2669)],
 'Kaltenbrunn → Reinhardsachsen → Gerolzahn (BW)':[(49.648,9.3589),(49.6407,9.3443),(49.6258,9.3189)],
 'Miltenberg → Wenschdorf → Geisenhof (St 2309)':[(49.7018,9.2560),(49.7006,9.2755),(49.6572,9.3069)]}
Ct=RG.C.copy(); Ct.data=E.len_m.values[RG.keid]+1e-6; refs=[]
for nm,wps in REFS.items():
    nodes=[RG.nearest(la,lo,tf) for la,lo in wps]; eids=[]
    for a,b in zip(nodes[:-1],nodes[1:]):
        D,P=dijkstra(Ct,indices=a,return_predecessors=True); path=[b]
        while path[-1]!=a: path.append(P[path[-1]])
        path=path[::-1]; eids+=list(RG.edge_ids(np.array(path[:-1]),np.array(path[1:])))
    lines=shapely.line_merge(shapely.MultiLineString([E.geometry.iat[e] for e in eids]))
    parts=list(lines.geoms) if hasattr(lines,'geoms') else [lines]
    refs.append(dict(n=nm,g=[simp(list(p.coords),15) for p in parts]))
D=dict(levels=LEVELS,start=START,loops=loops,heat=heat,refs=refs,pauses=sorted(pauses.values(),key=lambda p:-p['c']))
js=json.dumps(D,ensure_ascii=False,separators=(',',':'))
html=open('/workspace/scenic/phase3/karte_template.html').read()
html=html.replace('/*LEAFLETCSS*/',open(f'{CACHE}/leaflet/leaflet.css').read()).replace('/*LEAFLETJS*/',open(f'{CACHE}/leaflet/leaflet.js').read()).replace('/*DATA*/',js)
out=f'{OUTDIR}/karte_alle_runden.html'; open(out,'w').write(html); print(out,len(html)//1024,'KB',len(loops),'Runden')
