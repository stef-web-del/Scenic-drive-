"""Exporte: GPX, GeoJSON, Höhenprofil-PNG, Übersichtskarte PNG, interaktive HTML (folium)."""
import json, numpy as np, geopandas as gpd, folium, gpxpy, gpxpy.gpx
import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
from shapely.geometry import LineString
COLS=['#e41a1c','#377eb8','#4daf4a','#984ea3','#ff7f00','#a65628','#f781bf']
def route_files(r,i,out):
    c=r['char']; ls=LineString(c['coords']); nm=f'{out}/runde_{i+1}'
    props={k:v for k,v in r.items() if k in('len_km','time_min','score','overlap','b_share','town_share','valhalla_min')}
    props.update(rank=i+1,tour_score=c['tour_score'],tags=', '.join(c['tags']),text=c['text'],orte=' – '.join(c['orte']))
    gpd.GeoDataFrame([props],geometry=[ls],crs=4326).to_file(nm+'.geojson',driver='GeoJSON')
    g=gpxpy.gpx.GPX(); t=gpxpy.gpx.GPXTrack(name=f"Scenic Runde {i+1}: {r['len_km']:.0f} km, {r['time_min']:.0f} min",description=c['text']); g.tracks.append(t)
    s=gpxpy.gpx.GPXTrackSegment(); t.segments.append(s)
    for lo,la in c['coords']: s.points.append(gpxpy.gpx.GPXTrackPoint(la,lo))
    for p in c['pauses'][:2]: pass
    open(nm+'.gpx','w').write(g.to_xml())
    d,z=np.array(c['profile'][0])/1000,np.array(c['profile'][1])
    f,a=plt.subplots(figsize=(10,3)); a.fill_between(d,z.min()-10,z,color=COLS[i%7],alpha=.35); a.plot(d,z,color=COLS[i%7])
    for h in c['highlights']: a.axvspan(h['km0'],h['km0']+h['len_m']/1000,color='gold',alpha=.35)
    a.set_xlabel('km'); a.set_ylabel('m ü. NN'); a.set_title(f"Runde {i+1}: {r['len_km']:.0f} km, {r['time_min']:.0f} min, ↑{c['ascent_m']:.0f} m – gelb: Highlight-Abschnitte")
    f.tight_layout(); f.savefig(nm+'_hoehenprofil.png',dpi=110); plt.close(f)
def overview(E,routes,start,out,fname='karte_runden'):
    P=E.to_crs(25832); H=P[P.score>0.05]
    fig,ax=plt.subplots(figsize=(13,13)); P.plot(ax=ax,color='#dddddd',linewidth=0.3)
    H.plot(ax=ax,column='score',cmap='YlOrRd',linewidth=0.6+H.score*3,legend=True,legend_kwds={'shrink':0.5,'label':'Score (Kante+Abschnitt)'})
    from pyproj import Transformer; tf=Transformer.from_crs(4326,25832,always_xy=True)
    m=folium.Map(location=start,zoom_start=11,tiles='OpenStreetMap')
    for i,r in enumerate(routes):
        c=np.array(r['char']['coords']); x,y=tf.transform(c[:,0],c[:,1])
        ax.plot(x,y,color=COLS[i%7],lw=2.6,alpha=.85,label=f"#{i+1} {r['len_km']:.0f} km {r['time_min']:.0f} min T={r['char']['tour_score']:.2f}")
        fg=folium.FeatureGroup(name=f'Runde {i+1}')
        folium.PolyLine([(la,lo) for lo,la in c],color=COLS[i%7],weight=5,opacity=.85,
            popup=folium.Popup(f"<b>Runde {i+1}</b> {r['len_km']:.0f} km, {r['time_min']:.0f} min<br>{', '.join(r['char']['tags'])}<br>{r['char']['text']}",max_width=420)).add_to(fg)
        for p in r['char']['pauses'][:3]:
            folium.Marker((p['lonlat'][1],p['lonlat'][0]),tooltip=f"Pause: {p['name']} ({p['kind']}, km {p['km']})",icon=folium.Icon(color='green',icon='cutlery',prefix='fa')).add_to(fg)
        fg.add_to(m)
    sx,sy=tf.transform(start[1],start[0]); ax.plot(sx,sy,'k*',ms=18,label='Start OBI Miltenberg')
    ax.set_xlim(sx-23000,sx+23000); ax.set_ylim(sy-23000,sy+23000); ax.legend(loc='lower left'); ax.set_axis_off()
    ax.set_title('Top-Runden über Straßen-Score · © OpenStreetMap, Copernicus GLO-30, DGM1 © BVV')
    fig.savefig(f'{out}/{fname}.png',dpi=110,bbox_inches='tight'); plt.close(fig)
    folium.Marker(start,tooltip='Start OBI Miltenberg').add_to(m); folium.LayerControl().add_to(m); m.save(f'{out}/{fname}.html')
