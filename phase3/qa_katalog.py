"""QA eines Katalogs (Nebenstraßen im Ort, Eichenbühl, Referenzen, unbefestigt): venv/bin/python phase3/qa_katalog.py <routes_all.pkl> <data-dir> <out.json>"""
import sys,json,pickle,numpy as np,geopandas as gpd
sys.path.insert(0,'/workspace/scenic')
from scenic import api,profiles
from scipy.sparse.csgraph import dijkstra
from pyproj import Transformer
p=profiles.get(town_minor_mult=1.0); E,RG=api.prepare(p)
R=pickle.load(open(sys.argv[1],'rb'))['routes']; D=sys.argv[2]
m=json.load(open(f'{D}/meta.json')); L=m['loops']+json.load(open(f'{D}/all.json'))
ids=[l['id'] for l in L]
MIN=E.highway.isin(['residential','living_street','unclassified','service']).values; T=RG.town; LEN=E.len_m.values
unp=(E.highway.eq('track')|~E.surface.fillna('asphalt').isin(['asphalt','paved','concrete','concrete:plates'])|E.tracktype.fillna('grade1').ne('grade1')).values
tf=Transformer.from_crs(4326,25832,always_xy=True)
mid=E.geometry.to_crs(25832).interpolate(0.5,normalized=True); MX,MY=mid.x.values,mid.y.values
P=gpd.read_file('/workspace/scenic/places.gpkg'); c=P[P['name']=='Eichenbühl'].geometry.iloc[0]; ex,ey=tf.transform(c.x,c.y)
inz=np.hypot(MX-ex,MY-ey)<700
tm=tt=em=et=0; ne=0; unpm=0; ov=[]; allE=set()
for i in ids:
    e=np.array(R[i]['eids']); allE|=set(e.tolist()); w=LEN[e]
    tm+=w[(T&MIN)[e]].sum(); tt+=w[T[e]].sum(); unpm+=w[unp[e]].sum(); ov.append(R[i]['overlap'])
    z=inz[e]
    if w[z].sum()>100: ne+=1; em+=w[(z&MIN[e])].sum(); et+=w[z].sum()
REFS={'Miltenberg–Mainbullau':[(49.7003,9.2390),(49.6908,9.1861)],'Windischbuchener Steige':[(49.6581,9.3407),(49.6002,9.2305)],
 'Windischbuchen–Wenschdorf':[(49.6581,9.3407),(49.6791,9.2875)],'Collenberg–Mönchberg':[(49.7733,9.3267),(49.7922,9.2669)]}
Ct=RG.C.copy(); Ct.data=LEN[RG.keid]+1e-6; ref={}
sets={i:set(R[i]['eids']) for i in ids}
for nm,wps in REFS.items():
    nodes=[RG.nearest(la,lo,tf) for la,lo in wps]; eids=[]
    for a,b in zip(nodes[:-1],nodes[1:]):
        _,Pr=dijkstra(Ct,indices=a,return_predecessors=True); path=[b]
        while path[-1]!=a: path.append(Pr[path[-1]])
        path=path[::-1]; eids+=list(RG.edge_ids(np.array(path[:-1]),np.array(path[1:])))
    w=LEN[eids]; best=0; n80=0
    for i,s in sets.items():
        f=w[[x in s for x in eids]].sum()/w.sum(); best=max(best,f); n80+=f>=0.8
    ref[nm]=dict(beste_abdeckung=round(best,2),runden_80pct=int(n80))
mins=[l['min'] for l in L]
out=dict(runden=len(L),vertreter=len(m['loops']),start=m['start'],min_min=min(mins),max_min=max(mins),runden_45_60=sum(45<=x<=60 for x in mins),
 tour_mittel=round(float(np.mean([l['t'] for l in L])),4),score_mittel=round(float(np.mean([l['s'] for l in L])),4),
 nebenstr_ort_anteil=round(tm/tt*100,1),eichenbuehl_runden=ne,eichenbuehl_nebenstr_anteil=round(em/max(et,1)*100,1),
 unbefestigt_m=round(float(unpm)),overlap_max=round(float(max(ov)),3),ref=ref)
print(json.dumps(out,ensure_ascii=False,indent=1)); json.dump(out,open(sys.argv[3],'w'),ensure_ascii=False,indent=1)
