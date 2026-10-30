import geopandas as gpd, rasterio, numpy as np, matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
from rasterio.enums import Resampling
from shapely.geometry import Point
with rasterio.open('dem/glo30_bbox.tif') as s:
    z=s.read(1,out_shape=(s.height//4,s.width//4),resampling=Resampling.average); b=s.bounds
dy=30*4; dx=dy*np.cos(np.radians(49.7)); gy,gx=np.gradient(z,dy,dx)
sl=np.arctan(np.hypot(gx,gy)); asp=np.arctan2(-gx,gy); az,alt=np.radians(315),np.radians(45)
hs=np.sin(alt)*np.cos(sl)+np.cos(alt)*np.sin(sl)*np.cos(az-asp)
fig,ax=plt.subplots(figsize=(14,12),dpi=150)
ax.imshow(hs,cmap='gray',extent=(b.left,b.right,b.bottom,b.top),alpha=.6); ax.imshow(z,cmap='terrain',extent=(b.left,b.right,b.bottom,b.top),alpha=.3)
pl=gpd.read_file('places.gpkg'); pl[pl.landuse=='residential'].plot(ax=ax,color='#c0392b',alpha=.5,lw=0)
r=gpd.read_file('roads.gpkg',where="highway IN ('motorway','trunk','primary','secondary','tertiary','unclassified')")
for h,c,w in [('unclassified','#7f8c8d',.3),('tertiary','#27ae60',.5),('secondary','#f39c12',.7),('primary','#e67e22',.9),('trunk','#8e44ad',1),('motorway','#2c3e50',1.2)]:
    r[r.highway==h].plot(ax=ax,color=c,lw=w,label=h)
vp=gpd.read_file('viewpoints.gpkg'); vp.plot(ax=ax,color='blue',markersize=3,label='viewpoint')
c=gpd.GeoSeries([Point(9.2575,49.7030)],crs=4326).to_crs(25832).buffer(50000).to_crs(4326); c.boundary.plot(ax=ax,color='k',ls='--')
ax.plot(9.2575,49.703,'k*',ms=12); ax.legend(loc='lower left'); ax.set_title('Miltenberg 50 km: OSM-Straßen, Siedlungen, Copernicus GLO-30 Hillshade')
plt.savefig('overview.png',bbox_inches='tight')
