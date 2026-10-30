"""Offline-Ortsindex für die App aus OSM (places.gpkg = place-Knoten aus miltenberg_bbox.osm.pbf).
  ../venv/bin/python tools/build_places.py  ->  androidApp/src/main/assets/places.tsv
Zeile: name<TAB>art<TAB>lat<TAB>lon<TAB>bei   (bei = nächstgelegener Ort/Stadt für Weiler, Flurnamen usw.; leer bei Städten/Dörfern)
Nur Punkte bis 60 km um OBI Miltenberg (Testgebiet 50 km + Rand). Daten © OpenStreetMap-Mitwirkende (ODbL)."""
import os,numpy as np,geopandas as gpd
from scipy.spatial import cKDTree
R=os.path.dirname(os.path.dirname(os.path.abspath(__file__))); ROOT=os.path.dirname(R)
KEEP={'city':0,'town':1,'municipality':2,'village':3,'suburb':4,'quarter':5,'hamlet':6,'neighbourhood':7,'isolated_dwelling':8,'farm':9,'locality':10,'island':11,'islet':12}
d=gpd.read_file(f'{ROOT}/places.gpkg'); d=d[d.place.isin(list(KEEP))&d.name.notna()].copy()
rp=d.to_crs(4326).geometry.representative_point(); d['lon']=rp.x; d['lat']=rp.y; lat0,lon0=49.7009613,9.2248586
xy=lambda a,b: np.c_[(b-lon0)*71500,(a-lat0)*111200]
d=d[np.hypot(*xy(d.lat.values,d.lon.values).T)<=60000]
d['name']=d.name.str.replace('\t',' ').str.strip(); d=d.sort_values('place',key=lambda s:s.map(KEEP)).assign(k=lambda x:(x.lat*50).round().astype(str)+(x.lon*30).round().astype(str)).drop_duplicates(['name','k']).copy()
big=d[d.place.isin(['city','town','municipality','village'])]; t=cKDTree(xy(big.lat.values,big.lon.values))
_,i=t.query(xy(d.lat.values,d.lon.values)); d['bei']=np.where(d.place.isin(['city','town','municipality','village']),'',big.name.values[i])
d.loc[d.bei==d.name,'bei']=''
out=f'{R}/androidApp/src/main/assets/places.tsv'
with open(out,'w') as f:
    for r in d.itertuples(): f.write(f'{r.name}\t{r.place}\t{r.lat:.5f}\t{r.lon:.5f}\t{r.bei}\n')
print(len(d),'Orte',os.path.getsize(out),'Bytes ->',out); print(d.place.value_counts().to_dict())
