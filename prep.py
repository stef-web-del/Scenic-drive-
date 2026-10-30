import json, numpy as np, geopandas as gpd, rasterio
from shapely.geometry import shape
from rasterio.transform import from_origin
from pyproj import Transformer
O='osm/'
def load(name, keep):
    rows=[]
    for l in open(O+name+'.geojsonl'):
        f=json.loads(l.lstrip('\x1e')); p=f['properties']
        d={k:p.get(k) for k in keep}; d['osm_id']=p.get('@id'); d['geometry']=shape(f['geometry']); rows.append(d)
    return gpd.GeoDataFrame(rows,crs=4326)
r=load('roads',['highway','name','ref','surface','tracktype','lanes','width','maxspeed','oneway','access','motor_vehicle','junction'])
r.to_file('roads.gpkg'); print('roads',len(r)); del r
pl=load('places',['place','landuse','name','population']); pl.to_file('places.gpkg'); del pl
lc=load('landcover',['landuse','natural','name']); lc.to_file('landcover_osm.gpkg'); del lc
vp=load('viewpoints',['tourism','name','ele']); vp['geometry']=vp.geometry.representative_point(); vp.to_file('viewpoints.gpkg'); print('viewpoints',len(vp))
t=Transformer.from_crs(4326,25832,always_xy=True)
xs,ys=t.transform([8.5,8.5,10.03,10.03],[49.21,50.2,49.21,50.2])
x0,x1,y0,y1=min(xs),max(xs),max(ys),min(ys)
res=250; W=int((x1-x0)/res)+1; H=int((y0-y1)/res)+1; g=np.zeros((H,W),np.uint16); n=0
for l in open(O+'buildings.geojsonl'):
    c=json.loads(l.lstrip('\x1e'))['geometry']['coordinates'][0]
    if isinstance(c[0][0],list): c=c[0]
    a=np.array(c); x,y=t.transform(a[:,0].mean(),a[:,1].mean())
    i=int((y0-y)/res); j=int((x-x0)/res)
    if 0<=i<H and 0<=j<W: g[i,j]+=1; n+=1
with rasterio.open('building_density_250m.tif','w',driver='GTiff',height=H,width=W,count=1,dtype='uint16',crs='EPSG:25832',transform=from_origin(x0,y0,res,res),compress='deflate') as d: d.write(g,1)
print('buildings',n)
