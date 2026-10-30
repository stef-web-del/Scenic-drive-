"""Exportiert Top-Runden: GPX, GeoJSON, Karten-PNG, Höhenprofile, folium-HTML, summary.json"""
import json, numpy as np, geopandas as gpd, os, folium, gpxpy, gpxpy.gpx, rasterio
import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
from shapely.geometry import LineString, Point
from shapely.ops import linemerge
from scipy.spatial import cKDTree
from config import *
import loops as L
D='..'; OUT=L.OUT; E=L.E; J=json.load(open(f'{OUT}/loops.json')); top=J['top']
pl=gpd.read_file(f'{D}/places.gpkg',bbox=(8.8,49.4,9.7,50)); pl=pl[pl.place.isin(['town','village','hamlet'])&pl.name.notna()].to_crs(25832)
pl['geometry']=pl.geometry.centroid; tp=cKDTree(np.c_[pl.geometry.x,pl.geometry.y])
def ordered_coords(eids):
    """Kanten in Fahrtreihenfolge verketten"""
    cur=tuple(J['start']); pts=[cur]
    for e in eids:
        u,v=L.EUV[e]; c=list(E.geometry[e].coords)
        if np.allclose(c[0],cur): pass
        elif np.allclose(c[-1],cur): c=c[::-1]
        elif np.allclose(c[0],pts[-1]) : pass
        else: c=c if np.hypot(*np.subtract(c[0],cur))<np.hypot(*np.subtract(c[-1],cur)) else c[::-1]
        pts+=c[1:]; cur=tuple(c[-1])
    return pts
dems=[rasterio.open(f'{D}/{f}') for f in DEM_PRIORITY if os.path.exists(f'{D}/{f}')]
def elev(xy):
    z=np.full(len(xy),np.nan)
    for ds in dems:
        if ds.crs.to_epsg()==25832: p=xy
        else: p=np.c_[L.Transformer.from_crs(25832,ds.crs,always_xy=True).transform(xy[:,0],xy[:,1])]
        m=np.isnan(z); v=np.array([a[0] for a in ds.sample(p[m])],float); v[(v<-50)|(v>3000)]=np.nan; z[m]=v
    return z
summ=[]; cols=['#e41a1c','#377eb8','#4daf4a','#984ea3','#ff7f00']
fm=folium.Map(location=START,zoom_start=11,tiles='OpenStreetMap')
H=E[E.score>0.05].to_crs(25832)
fig,ax=plt.subplots(figsize=(14,14))
E.to_crs(25832).plot(ax=ax,color='#dddddd',linewidth=0.3)
H.plot(ax=ax,column='score',cmap='YlOrRd',linewidth=0.8+H.score*2.5,legend=True,legend_kwds={'shrink':0.5,'label':'Kanten-Score'})
for i,t in enumerate(top):
    c=ordered_coords(t['eids']); ls=LineString(c); g=gpd.GeoSeries([ls],crs=4326).to_crs(25832)[0]
    n=int(g.length//25)+1; xy=np.array([g.interpolate(d).coords[0] for d in np.linspace(0,g.length,n)])
    z=elev(xy); dist=np.linspace(0,g.length,n)/1000
    # Hauptorte: Orte <400 m an der Strecke, in Reihenfolge
    d,ix=tp.query(xy); seen=[]
    for dd,ii in zip(d,ix):
        nm=pl.name.iloc[ii]
        if dd<400 and nm not in seen: seen.append(nm)
    names=E.loc[t['eids']].assign(l=E.len_m[t['eids']]).groupby(E.name.fillna(E.ref).fillna('?')).l.sum().sort_values(ascending=False)
    from scipy.ndimage import uniform_filter1d; zz=uniform_filter1d(np.nan_to_num(z,nan=np.nanmean(z)),9); up=np.clip(np.diff(zz),0,None).sum()
    s=dict(rank=i+1,len_km=round(t['len']/1000,1),time_min=round(t['time']/60),score=round(t['score'],3),overlap=round(t['overlap'],3),
           ascent_m=int(up),orte=seen,strassen=[f'{k} ({v/1000:.1f} km)' for k,v in names.head(8).items()])
    summ.append(s)
    nm=f'{OUT}/runde_{i+1}'
    gpd.GeoDataFrame([{k:(json.dumps(v,ensure_ascii=False) if isinstance(v,list) else v) for k,v in s.items()}],geometry=[ls],crs=4326).to_file(nm+'.geojson',driver='GeoJSON')
    gx=gpxpy.gpx.GPX(); tr=gpxpy.gpx.GPXTrack(name=f'Scenic Runde {i+1} ({s["len_km"]} km, {s["time_min"]} min, Score {s["score"]})'); gx.tracks.append(tr)
    sg=gpxpy.gpx.GPXTrackSegment(); tr.segments.append(sg)
    for (lo,la) in c: sg.points.append(gpxpy.gpx.GPXTrackPoint(la,lo))
    open(nm+'.gpx','w').write(gx.to_xml())
    f2,a2=plt.subplots(figsize=(10,3)); a2.fill_between(dist,np.nanmin(z)-10,z,color=cols[i],alpha=.4); a2.plot(dist,z,color=cols[i])
    a2.set_xlabel('km'); a2.set_ylabel('Höhe m'); a2.set_title(f'Runde {i+1}: {s["len_km"]} km, {s["time_min"]} min, ↑{s["ascent_m"]} m (DGM1 BY / GLO-30)')
    f2.tight_layout(); f2.savefig(nm+'_hoehenprofil.png',dpi=110); plt.close(f2)
    ax.plot(xy[:,0],xy[:,1],color=cols[i],linewidth=2.5,alpha=.85,label=f'#{i+1} {s["len_km"]} km {s["time_min"]} min S={s["score"]}')
    folium.PolyLine([(la,lo) for lo,la in c],color=cols[i],weight=5,opacity=.8,tooltip=f'Runde {i+1}: {s["len_km"]} km, {s["time_min"]} min, Score {s["score"]}<br>{" – ".join(seen[:8])}').add_to(fm)
sx,sy=L.sx,L.sy; ax.plot(sx,sy,'k*',ms=18,label='Start OBI Miltenberg')
ax.set_xlim(sx-22000,sx+22000); ax.set_ylim(sy-22000,sy+22000); ax.legend(loc='lower left'); ax.set_axis_off()
ax.set_title('Scenic Routes – Top-Runden über Kanten-Score (Daten © OpenStreetMap, Copernicus GLO-30, DGM1 © BVV CC BY 4.0)')
fig.savefig(f'{OUT}/karte_runden.png',dpi=120,bbox_inches='tight')
folium.Marker(START,tooltip='Start OBI Miltenberg').add_to(fm); fm.save(f'{OUT}/karte_runden.html')
json.dump(summ,open(f'{OUT}/summary.json','w'),ensure_ascii=False,indent=1); print(json.dumps(summ,ensure_ascii=False,indent=1))
