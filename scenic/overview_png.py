"""Übersichtskarte PNG: ausgegebene Runden farbig, Start markiert; Hintergrund aus lokalen OSM-Daten gerendert
(Straßennetz features.gpkg, Wald/Landbedeckung landcover_osm.gpkg, Orte places.gpkg) – mit Attribution."""
import json,os,glob,numpy as np,geopandas as gpd
import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
from shapely.geometry import box
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COLS=['#e41a1c','#377eb8','#4daf4a','#984ea3','#ff7f00','#a65628','#f781bf','#17becf','#bcbd22','#333333']
W={'motorway':2.2,'trunk':2,'primary':1.6,'secondary':1.2,'tertiary':0.9,'unclassified':0.5,'residential':0.3}
def overview(out):
    S=json.load(open(f'{out}/summary.json')); det={}
    for f in glob.glob(f'{out}/build/web_raw/r/s*.json'): det.update(json.load(open(f)))
    meta={l['id']:l for l in json.load(open(f'{out}/build/web_raw/meta.json'))['loops']}
    gs=[det[str(i)]['g'] for i in S['selected']]
    A=np.array([p for g in gs for p in g]); bb=box(A[:,0].min()-0.03,A[:,1].min()-0.02,A[:,0].max()+0.03,A[:,1].max()+0.02)
    fig,ax=plt.subplots(figsize=(13,13)); ax.set_facecolor('#f2efe9')
    try:
        lc=gpd.read_file(f'{ROOT}/landcover_osm.gpkg',bbox=bb.bounds).to_crs(3857)
        col=next((c for c in ('landuse','natural','kind','class') if c in lc.columns),None)
        fo=lc[lc[col].astype(str).isin(['forest','wood'])] if col else lc
        fo.plot(ax=ax,color='#cfe3c0',lw=0)
    except Exception as ex: print('landcover',ex)
    R=gpd.read_file(f'{ROOT}/phase3/cache/features.gpkg',bbox=bb.bounds,columns=['highway']).to_crs(3857)
    for h,w in W.items():
        s=R[R.highway==h]
        if len(s): s.plot(ax=ax,color='#ffffff' if w<1 else '#f6c66b',lw=w,zorder=2)
    try:
        pl=gpd.read_file(f'{ROOT}/places.gpkg',bbox=bb.bounds).to_crs(3857)
        nc='name' if 'name' in pl.columns else None
        pl=pl[pl.place.isin(['town','village','city']) & pl.geometry.geom_type.eq('Point')]
        for _,r in pl.iterrows():
            c=r.geometry.centroid
            if nc and isinstance(r[nc],str): ax.text(c.x,c.y,r[nc],fontsize=7.5,color='#444',ha='center',zorder=3,clip_on=True)
    except Exception as ex: print('places',ex)
    for k,(i,g) in enumerate(zip(S['selected'],gs)):
        G=gpd.GeoSeries.from_xy([p[0] for p in g],[p[1] for p in g],crs=4326).to_crs(3857); m=meta[i]
        ax.plot(G.x,G.y,color=COLS[k%10],lw=4.2-0.35*k,alpha=.85,zorder=4,label=f"#{k+1} {S.get('names',{}).get(str(i),'')} · {m['km']:.0f} km · {m['min']} min · {m['kl']}")
    st=gpd.GeoSeries.from_xy([S['start']['lon']],[S['start']['lat']],crs=4326).to_crs(3857)
    ax.plot(st.x,st.y,'*',color='k',ms=24,mec='w',mew=1.5,zorder=5,label=f"Start {S['start']['name']}")
    b=gpd.GeoSeries([bb],crs=4326).to_crs(3857).total_bounds; ax.set_xlim(b[0],b[2]); ax.set_ylim(b[1],b[3]); ax.set_axis_off()
    ax.legend(loc='lower left',fontsize=9,framealpha=.92); ax.set_title(f"Scenic Routes – Runden ab {S['start']['name']}, ca. {S['minutes']:.0f} min")
    ax.text(.995,.005,'Kartendaten © OpenStreetMap-Mitwirkende (ODbL)',transform=ax.transAxes,ha='right',va='bottom',fontsize=8,bbox=dict(fc='w',alpha=.85,lw=0),zorder=6)
    fig.savefig(f'{out}/overview.png',dpi=110,bbox_inches='tight'); plt.close(fig)
