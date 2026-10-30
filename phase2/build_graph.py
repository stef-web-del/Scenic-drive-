"""Baut den bewerteten Straßengraphen (networkx) um den Startpunkt. Ausgabe: out/edges.gpkg + graph.pkl"""
import numpy as np, geopandas as gpd, pandas as pd, rasterio, pickle, json, sys, os
from rasterio.features import rasterize
from scipy import ndimage
from shapely.geometry import LineString, Point
from pyproj import Transformer
import networkx as nx
from config import *
B=os.path.dirname(os.path.abspath(__file__)); D=os.path.join(B,'..'); OUT=os.path.join(B,'out')
tf=Transformer.from_crs(4326,25832,always_xy=True)
sx,sy=tf.transform(START[1],START[0])
bb=(START[1]-RADIUS_DEG_LON,START[0]-RADIUS_DEG_LAT,START[1]+RADIUS_DEG_LON,START[0]+RADIUS_DEG_LAT)
print('lade roads',bb)
r=gpd.read_file(f'{D}/roads.gpkg',bbox=bb)
r=r[r.highway.isin(SPEED.keys())]
r=r[~r.access.isin(['no','private','agricultural','forestry','destination_x'])]
r=r[~r.motor_vehicle.isin(['no','private','agricultural','forestry'])]
paved=r.surface.isna()|r.surface.isin(PAVED)
r=r[paved & ~((r.highway=='track')&(r.surface.isna()))].reset_index(drop=True)
sm=json.load(open(f'{OUT}/smoothness.json'))  # nachträglich aus PBF extrahiert (fehlte in roads.gpkg)
r['smoothness']=r.osm_id.astype(str).map(sm)
r=r[~r.smoothness.isin(['bad','very_bad','horrible','very_horrible','impassable','terrible','poor','rough'])].reset_index(drop=True)
print('ways',len(r))
# Topologie: Knoten an Endpunkten und an von >1 Way genutzten Stützpunkten
from collections import Counter
cnt=Counter()
coords=[list(g.coords) for g in r.geometry]
for c in coords:
    for i,p in enumerate(c):
        cnt[p]+= 2 if i in (0,len(c)-1) else 1
segs=[]
for wi,c in enumerate(coords):
    cur=[c[0]]
    for p in c[1:]:
        cur.append(p)
        if cnt[p]>=2 and len(cur)>=2:
            segs.append((wi,cur)); cur=[p]
    if len(cur)>=2: segs.append((wi,cur))
print('edges',len(segs))
attrs=r.drop(columns='geometry')
E=gpd.GeoDataFrame(attrs.iloc[[s[0] for s in segs]].reset_index(drop=True),geometry=[LineString(s[1]) for s in segs],crs=4326)
E['u']=[s[1][0] for s in segs]; E['v']=[s[1][-1] for s in segs]
E=E[E.u!=E.v].reset_index(drop=True)
P=E.to_crs(25832); E['len_m']=P.length.values
# Proben alle STEP m
def sample(g):
    n=max(2,int(g.length//STEP)+1); return np.array([g.interpolate(t,normalized=True).coords[0] for t in np.linspace(0,1,n)])
S=[sample(g) for g in P.geometry]
# Kurvigkeit: Summe Richtungsänderung (Grad/km), Kehren-/Kurvenanteil
def curv(a):
    """Kurvenfluss: Richtungsänderung je 20-m-Schritt -> Radius r=STEP/dh.
    flüssig (40-400 m Radius) voll, Spitzkehren (<40 m) halb gewertet."""
    d=np.diff(a,axis=0); h=np.arctan2(d[:,1],d[:,0])
    if len(h)<2: return 0.,0.,0.
    dh=np.abs((np.diff(h)+np.pi)%(2*np.pi)-np.pi)
    dh[dh<np.radians(3)]=0   # Rauschen/Digitalisierung
    r=STEP/np.maximum(dh,1e-6)
    flow=dh[(r>=40)&(r<=400)].sum(); tight=dh[r<40].sum()
    return float(np.degrees(flow+0.5*tight)), float((dh>np.radians(12)).sum()), float(np.degrees(tight))
cv=np.array([curv(a) for a in S]); L=np.maximum(E.len_m.values,1)
E['curv_deg_km']=cv[:,0]/L*1000; E['bends_km']=cv[:,1]/L*1000; E['tight_deg_km']=cv[:,2]/L*1000
# Höhen: DGM (Bayern 1m->10m) wo vorhanden, sonst GLO-30
allp=np.vstack(S); idx=np.cumsum([0]+[len(a) for a in S])
z=np.full(len(allp),np.nan); zs=np.zeros(len(allp),int)
for f in DEM_PRIORITY:
    f=f'{D}/{f}'
    if not os.path.exists(f): continue
    with rasterio.open(f) as ds:
        pts=allp if ds.crs.to_epsg()==25832 else np.array(Transformer.from_crs(25832,ds.crs,always_xy=True).transform(allp[:,0],allp[:,1])).T
        m=np.isnan(z)
        v=np.array([x[0] for x in ds.sample(pts[m])],float)
        if ds.nodata is not None: v[v==ds.nodata]=np.nan
        v[(v<-50)|(v>3000)]=np.nan
        z[m]=v; zs[m & ~np.isnan(z)]=DEM_PRIORITY.index(f[len(D)+1:])+1
    print(f,'abgedeckt',np.mean(~np.isnan(z)))
E['dem_src']=[ 'GLO30' if np.median(zs[idx[i]:idx[i+1]])==3 else 'DGM' for i in range(len(S))]
climb=[];grade=[];zmean=[]
for i in range(len(S)):
    zz=z[idx[i]:idx[i+1]]
    if np.isnan(zz).all(): climb.append(0);grade.append(0);zmean.append(np.nan);continue
    zz=pd.Series(zz).interpolate().bfill().ffill().values
    if len(zz)>=5: zz=ndimage.uniform_filter1d(zz,5,mode='nearest')  # DSM-Rauschen/Wald glätten
    dz=np.diff(zz); climb.append(np.abs(dz).sum()); grade.append(np.abs(zz[-1]-zz[0])/L[i]*100); zmean.append(zz.mean())
E['relief_m_km']=np.array(climb)/L*1000; E['grade_pct']=grade; E['z_mean']=zmean
# Landbedeckung als Raster (25 m)
res=25; x0,y0=sx-RADIUS_M*1.2,sy+RADIUS_M*1.2; W=H=int(2.4*RADIUS_M/res)
tr=rasterio.transform.from_origin(x0,y0,res,res)
lc=gpd.read_file(f'{D}/landcover_osm.gpkg',bbox=bb).to_crs(25832)
def cls(row):
    lu,na=row.landuse,row.natural
    if lu=='forest' or na=='wood': return 1
    if lu in ('residential','industrial','commercial','retail','construction') : return 3
    if na=='water' or lu=='reservoir': return 4
    if lu in ('farmland','meadow','grass','vineyard','orchard','allotments') or na in ('grassland','heath','scrub'): return 2
    return 0
lc['c']=lc.apply(cls,axis=1); lc=lc[lc.c>0].sort_values('c')
R=rasterize(((g,c) for g,c in zip(lc.geometry,lc.c)),out_shape=(H,W),transform=tr,fill=0,dtype='uint8')
forest=R==1; openl=(R==2)|(R==4)
edge=forest & ndimage.binary_dilation(openl,iterations=3) | openl & ndimage.binary_dilation(forest,iterations=3)
def frac(mask,rad):
    k=ndimage.uniform_filter(mask.astype(float),rad); return k
F_for=frac(forest,5); F_open=frac(openl,9); F_edge=frac(edge,5); F_urb=frac(R==3,5)
def rs(arr):
    rr=((y0-allp[:,1])/res).astype(int); cc=((allp[:,0]-x0)/res).astype(int)
    ok=(rr>=0)&(rr<H)&(cc>=0)&(cc<W); o=np.zeros(len(allp)); o[ok]=arr[rr[ok],cc[ok]]; return o
def emean(v): return np.array([v[idx[i]:idx[i+1]].mean() for i in range(len(S))])
E['forest']=emean(rs(F_for)); E['open']=emean(rs(F_open)); E['forest_edge']=emean(rs(F_edge)); E['urban_lc']=emean(rs(F_urb))
# Gebäudedichte
with rasterio.open(f'{D}/building_density_250m.tif') as ds:
    bd=np.array([x[0] for x in ds.sample(allp)],float)
E['bldg_250']=emean(bd)
# Orte: Nähe zu town/city (stark), village (leicht)
pl=gpd.read_file(f'{D}/places.gpkg',bbox=bb); pl=pl[pl.place.isin(['city','town','village','hamlet'])].to_crs(25832)
pl['geometry']=pl.geometry.centroid
from scipy.spatial import cKDTree
mids=np.array([a[len(a)//2] for a in S])
def near(kinds,rad):
    q=pl[pl.place.isin(kinds)]
    if len(q)==0: return np.full(len(mids),1e9)
    t=cKDTree(np.c_[q.geometry.x,q.geometry.y]); return t.query(mids)[0]
E['d_town']=near(['city','town'],1); E['d_village']=near(['village'],1)
# Aussichtspunkte
vp=gpd.read_file(f'{D}/viewpoints.gpkg',bbox=bb).to_crs(25832)
vp=vp[vp.tourism=='viewpoint'] if 'viewpoint' in set(vp.tourism) else vp
tv=cKDTree(np.c_[vp.geometry.x,vp.geometry.y])
dv=tv.query(allp)[0]; E['viewpoint']=emean(np.clip(1-dv/VIEW_RADIUS,0,1))
# Fahrzeit
def spd(row):
    s=SPEED[row.highway]
    try: s=min(s,float(str(row.maxspeed).split()[0]))
    except: pass
    if row.bldg_250>BLDG_TOWN: s=min(s,50)
    # Kurvigkeit bremst
    s=s/(1+row.curv_deg_km/CURV_SPEED_DIV)
    return max(s,15)
E['speed_kmh']=E.apply(spd,axis=1); E['time_s']=E.len_m/(E.speed_kmh/3.6)
from scoring import score
E=score(E)
E.to_file(f'{OUT}/edges.gpkg')
G=nx.MultiGraph()
for i,row in enumerate(E.itertuples()):
    G.add_edge(row.u,row.v,key=i,eid=i,time=row.time_s,len=row.len_m,score=row.score)
pickle.dump(G,open(f'{OUT}/graph.pkl','wb'))
print('fertig',G.number_of_nodes(),G.number_of_edges())
