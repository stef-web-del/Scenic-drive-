"""Runden-Charakteristik: Geometrie in Fahrtrichtung, Höhenprofil, Charakterabschnitte (Highlight/Transfer),
Flow-Unterbrechungen, Dramaturgie, Sichtweite (Viewshed-Prototyp), Tags, Text, Pausen-POIs, Tour-Score v2."""
import numpy as np, geopandas as gpd, rasterio, os, ast
from scipy.ndimage import uniform_filter1d
from scipy.spatial import cKDTree
from pyproj import Transformer
from .settings import ROOT, CACHE, DEM_PRIORITY
TF=Transformer.from_crs(4326,25832,always_xy=True)
_DEM=None; _POI=None; _PL=None
def dems():
    global _DEM
    if _DEM is None: _DEM=[rasterio.open(f'{ROOT}/{f}') for f in DEM_PRIORITY if os.path.exists(f'{ROOT}/{f}')]
    return _DEM
def elev(xy):
    z=np.full(len(xy),np.nan)
    for ds in dems():
        p=xy if ds.crs.to_epsg()==25832 else np.column_stack(Transformer.from_crs(25832,ds.crs,always_xy=True).transform(xy[:,0],xy[:,1]))
        m=np.isnan(z)
        if not m.any(): break
        v=np.array([a[0] for a in ds.sample(p[m])],float); v[(v<-50)|(v>3000)]=np.nan; z[m]=v
    return z
def pois():
    global _POI
    if _POI is None:
        P=gpd.read_file(f'{CACHE}/pois.gpkg').to_crs(25832); P['x']=P.geometry.x; P['y']=P.geometry.y; _POI=P
    return _POI
def places():
    global _PL
    if _PL is None:
        pl=gpd.read_file(f'{ROOT}/places.gpkg',bbox=(8.8,49.4,9.7,50)); pl=pl[pl.place.isin(['town','village','hamlet'])&pl.name.notna()].to_crs(25832)
        pl['geometry']=pl.geometry.centroid; _PL=(pl.reset_index(drop=True),cKDTree(np.c_[pl.geometry.x,pl.geometry.y]))
    return _PL
def ordered(E,eids,start_xy):
    """Kantengeometrien in Fahrtrichtung verketten -> lon/lat-Liste + Kantenindex je Punkt"""
    pts=[]; owner=[]; cur=None
    for e in eids:
        c=list(E.geometry.iat[e].coords)
        if cur is None:
            cur=c[0] if np.hypot(*np.subtract(c[0],start_xy))<np.hypot(*np.subtract(c[-1],start_xy)) else c[-1]
        if np.hypot(*np.subtract(c[-1],cur))<np.hypot(*np.subtract(c[0],cur)): c=c[::-1]
        pts+=c if not pts else c[1:]; owner+=[e]*(len(c) if len(owner)==0 else len(c)-1); cur=c[-1]
    return pts,np.array(owner)
def resample(pts,owner,step=25):
    xy=np.column_stack(TF.transform(*np.array(pts).T)); d=np.r_[0,np.cumsum(np.hypot(*np.diff(xy,axis=0).T))]
    s=np.arange(0,d[-1],step); x=np.interp(s,d,xy[:,0]); y=np.interp(s,d,xy[:,1])
    i=np.clip(np.searchsorted(d,s,side='right')-1,0,len(owner)-1)
    return np.c_[x,y],s,owner[i]
GLO=None
def viewshed(xy,step=200,radius=4000,n_rays=24,h=1.5):
    """Prototyp Sichtweite: an Punkten alle `step` m 24 Strahlen bis 4 km auf GLO-30 (DSM, inkl. Wald!).
    Rückgabe je Punkt: Anteil sichtbarer Strahlzellen (0..1) und mittlere max. Sichtweite (m)."""
    global GLO
    if GLO is None:
        ds=rasterio.open(f'{ROOT}/dem/glo30_bbox.tif'); GLO=(ds,ds.read(1).astype(float))
    ds,A=GLO; inv=Transformer.from_crs(25832,4326,always_xy=True)
    pts=xy[::max(1,int(step/25))]; r=np.arange(60,radius,60); ang=np.linspace(0,2*np.pi,n_rays,endpoint=False)
    X=pts[:,0,None,None]+np.cos(ang)[None,:,None]*r[None,None,:]; Y=pts[:,1,None,None]+np.sin(ang)[None,:,None]*r[None,None,:]
    lon,lat=inv.transform(X.ravel(),Y.ravel()); rr,cc=rasterio.transform.rowcol(ds.transform,lon,lat)
    rr=np.clip(np.array(rr),0,A.shape[0]-1); cc=np.clip(np.array(cc),0,A.shape[1]-1)
    Z=A[rr,cc].reshape(X.shape)
    lo,la=inv.transform(pts[:,0],pts[:,1]); r0,c0=rasterio.transform.rowcol(ds.transform,lo,la)
    z0=A[np.clip(r0,0,A.shape[0]-1),np.clip(c0,0,A.shape[1]-1)]+h
    tan=(Z-z0[:,None,None])/r; vis=tan>=np.maximum.accumulate(np.concatenate([np.full(tan.shape[:2]+(1,),-9),tan[:,:,:-1]],2),axis=2)
    frac=vis.mean((1,2)); far=(vis*r).max(2).mean(1)
    return frac,far
def _nm(E,i):
    import pandas as pd
    r,n=E.ref.iat[i],E.name.iat[i]
    if isinstance(r,str) and r: return r
    if isinstance(n,str) and n: return n
    return 'namenlose '+{'tertiary':'Kreisstraße','unclassified':'Verbindungsstraße','secondary':'Staatsstraße'}.get(E.highway.iat[i],'Straße')
def merge_runs(runs,gap,min_len):
    """runs: [i0,i1,kind] mit kind good/normal/transfer (Einheit: Stützpunkte). Gute Läufe, zwischen denen nur kurze
    Unterbrechungen (<=gap, keine Ortsdurchfahrt >gap) liegen, werden zu einem Highlight verschmolzen; Highlights kürzer als
    min_len werden zu 'normal'. Benachbarte Läufe gleicher Art werden zusammengefasst."""
    out=[]
    for r in runs:
        if r[2]=='good' and out and out[-1][2]=='good': out[-1][1]=r[1]; continue
        if r[2]=='good' and len(out)>=2 and out[-2][2]=='good' and out[-1][1]-out[-1][0]<=gap:
            g=out.pop(); out[-1][1]=r[1]; continue
        out.append(list(r))
    res=[]
    for r in out:
        k='highlight' if r[2]=='good' and r[1]-r[0]>=min_len else ('normal' if r[2]=='good' else r[2])
        if res and res[-1][2]==k and k!='highlight': res[-1][1]=r[1]
        else: res.append([r[0],r[1],k])
    return res
_RH={}
def _rhythm(E):
    """Rhythmus je Kante = Konstanz der Kurvigkeit im Abschnitt (wie scoring.section_score: 1-std(c_curv)/0.4)"""
    k=id(E)
    if k not in _RH:
        sd=E.groupby('sec_id').c_curv.std().fillna(0)
        _RH[k]=(1-np.clip(sd/0.4,0,1)).reindex(E.sec_id).values
    return _RH[k]
def analyze(RG,E,route,with_view=True):
    eids=route['eids']; sxy=ast.literal_eval(RG.nodes[0]) if False else None
    start=E.geometry.iat[eids[0]].coords[0]
    # Startpunkt = gemeinsamer Punkt von erster und letzter Kante
    a=E.geometry.iat[eids[0]].coords; b=E.geometry.iat[eids[-1]].coords
    cands=[a[0],a[-1]]; start=min(cands,key=lambda p:min(np.hypot(*np.subtract(p,b[0])),np.hypot(*np.subtract(p,b[-1]))))
    pts,own=ordered(E,eids,start); xy,s,eo=resample(pts,own)
    z=elev(xy); z=np.where(np.isnan(z),np.nanmean(z),z); zs=uniform_filter1d(z,9)
    dz=np.diff(zs); up=np.clip(dz,0,None).sum(); down=-np.clip(dz,None,0).sum()
    sc=E.score.values[eo]; cv=E.c_curv.values[eo]; sec=E.sec_id.values[eo]
    town=RG.town[eo]; isb=RG.isb[eo]; tight=E.tight_deg_km.values[eo]; forest=E.forest.values[eo]; opn=E.open.values[eo]
    L=s[-1] if len(s) else 1
    rel=E.c_relief.values[eo]; rhy=_rhythm(E)[eo]
    # Charakterabschnitte: zusammenhängende Läufe gleicher Abschnitts-ID
    brk=np.r_[0,np.where(np.diff(sec)!=0)[0]+1,len(sec)]; segs=[]
    pp=RG.p; runs=[]
    for i0,i1 in zip(brk[:-1],brk[1:]):
        m=slice(i0,i1); tk=(town[m].mean()>0.5 or isb[m].mean()>0.5 or sc[m].mean()<0.08)
        runs.append([int(i0),int(i1),'transfer' if tk else ('good' if sc[m].mean()>=pp.hl_min_score else 'normal')])
    runs=merge_runs(runs,pp.hl_gap_m/25,pp.hl_min_m/25)
    for i0,i1,kind in runs:
        ln=(i1-i0)*25; m=slice(i0,i1); gr=(zs[i1-1]-zs[i0])/max(ln,1)*100
        segs.append(dict(i0=int(i0),i1=int(i1),km0=s[i0]/1000,len_m=ln,score=float(sc[m].mean()),curv=float(cv[m].mean()),
             grade=float(gr),kind=kind,forest=float(forest[m].mean()),open=float(opn[m].mean()),tight=float(tight[m].mean()),
             name=_nm(E,eo[i0]),relief=float(rel[m].mean()),rhythm=float(rhy[m].mean()),
             town=float(town[m].mean()),bstr=float(isb[m].mean())))
    hl=[g for g in segs if g['kind']=='highlight']; tr=[g for g in segs if g['kind']=='transfer']
    top3=sorted(hl,key=lambda g:-g['score']*min(g['len_m'],4000))[:3]
    hl_len=sum(g['len_m'] for g in hl)/L; tr_len=sum(g['len_m'] for g in tr)/L
    topk=np.mean([g['score'] for g in top3]) if top3 else 0
    # Dramaturgie: Highlight-Länge auf Drittel verteilt (1=gleichmäßig/Spannungsbogen, 0=alles am Anfang)
    th=np.zeros(3)
    for g in hl: th[min(2,int(3*g['km0']*1000/L))]+=g['len_m']
    dram=1-np.abs(th/max(th.sum(),1)-1/3).sum()/(4/3) if th.sum() else 0
    # Flow-Unterbrechungen: Ampeln/Stopps <20 m, Ortseinfahrten, Abschnittswechsel an großen Kreuzungen
    P=pois(); tp=cKDTree(P[['x','y']].values)
    near=tp.query_ball_point(xy[::2],20); ids={i for l in near for i in l}
    sig=sum(P.kind.iat[i] in ('traffic_signals','stop') for i in ids)
    town_entries=int(np.sum(np.diff(town.astype(int))==1))
    interrupts=(sig+town_entries+len(segs)*0.1)/(L/10000)
    # Richtung: kurvig-bergauf Anteil (Sportwagen: kurvige Steigungen bergauf fahren)
    curvy=cv>0.45; upc=(curvy[1:]&(dz>0)).sum()/max(curvy.sum(),1)
    # Sicht
    vf,vd=viewshed(xy) if with_view else (np.zeros(1),np.zeros(1))
    # Pausen: Café/Biergarten/Restaurant <150 m im mittleren Drittel bis 70 %
    m0,m1=int(len(xy)*0.35),int(len(xy)*0.75); near=tp.query_ball_point(xy[m0:m1:4],150); pause=[]
    for k,l in enumerate(near):
        for i in l:
            if P.kind.iat[i] in ('cafe','biergarten','restaurant','pub','ice_cream') and P.name.iat[i] and P.name.iat[i] not in [p['name'] for p in pause]:
                pause.append(dict(name=P.name.iat[i],kind=P.kind.iat[i],km=round(s[m0+4*k]/1000,1),lonlat=Transformer.from_crs(25832,4326,always_xy=True).transform(P.x.iat[i],P.y.iat[i])))
    pl,tpl=places(); d,ix=tpl.query(xy); orte=[]
    for dd,ii in zip(d,ix):
        n=pl.name.iat[ii]
        if dd<400 and n not in orte: orte.append(n)
    out=dict(ascent_m=float(up),descent_m=float(down),z_min=float(zs.min()),z_max=float(zs.max()),highlight_share=hl_len,transfer_share=tr_len,
        topk=float(topk),dramaturgy=float(dram),interrupts_per10km=float(interrupts),signals=int(sig),town_entries=town_entries,
        uphill_curvy=float(upc),view_frac=float(vf.mean()),view_far_m=float(vd.mean()),view_p90=float(np.percentile(vf,90)),
        forest=float(forest.mean()),open=float(opn.mean()),tight_mean=float(tight.mean()),maintal=float((zs<160).mean()),
        segments=segs,highlights=top3,pauses=sorted(pause,key=lambda p:{'biergarten':0,'cafe':1,'ice_cream':2,'restaurant':3,'pub':4}.get(p['kind'],5))[:4],orte=orte,coords=pts,profile=(s.tolist(),zs.tolist()))
    # Zerlegung des Tour-Scores in additive Beiträge (Summe = tour_score)
    out['contrib']=dict(route=0.40*route['score'],topk=0.25*float(topk),flow=0.15*(1-tr_len),dram=0.10*float(dram),
        view=0.10*min(1,out['view_frac']*4),interrupts=-0.01*min(float(interrupts),10))
    out['tour_score']=float(sum(out['contrib'].values()))
    out.update(curv_mean=float(cv.mean()),relief_mean=float(rel.mean()),rhythm_mean=float(rhy.mean()),
        b_share=float(isb.mean()),town_share=float(town.mean()),route_score=float(route['score']))
    out['tags']=tags(out,route); out['text']=describe(out,route)
    return out
def tags(c,r):
    T=[]
    if c['highlight_share']>0.35: T.append('Kurvenrhythmus')
    if any(h['grade']>5 or h['grade']<-5 for h in c['segments'] if h['kind']=='highlight' and h['len_m']>1000): T.append('Steige')
    if c['tight_mean']>150: T.append('Kehren')
    if c['forest']>0.5: T.append('Waldpassage')
    if c['view_p90']>0.25 or c['view_far_m']>2000: T.append('Panorama')
    if c['z_max']-c['z_min']>250: T.append('großer Höhenunterschied')
    if c['maintal']>0.2: T.append('Talfahrt Main')
    if r['town_share']<0.08 and c['town_entries']<=6: T.append('einsam')
    if c['interrupts_per10km']<1.5: T.append('flüssig')
    if r['b_share']<0.02: T.append('ohne Bundesstraße')
    if c['pauses']: T.append('Einkehr möglich')
    return T
def de_num(x,d=0):
    """Deutsches Zahlenformat: Dezimalkomma, Tausenderpunkt (z. B. 1.234 / 5,2)."""
    return f"{x:,.{d}f}".replace(',','\u2009').replace('.',',').replace('\u2009','.')
def describe(c,r):
    o=c['orte']; mid=[x for x in o[1:-1]]
    via=', '.join(mid[:3]) if mid else ''
    h=c['highlights']
    s1=f"{de_num(r['len_km'])} km in etwa {de_num(r['time_min'])} Minuten" + (f" über {via}" if via else "") + f", {de_num(c['ascent_m'])} Höhenmeter zwischen {de_num(c['z_min'])} und {de_num(c['z_max'])} m."
    if h:
        g=h[0]; art='bergauf' if g['grade']>2 else ('bergab' if g['grade']<-2 else 'auf gleichbleibender Höhe')
        s2=f"Stärkster Abschnitt ist die {g['name'] or 'Nebenstraße'} ab km {de_num(g['km0'])} ({de_num(g['len_m']/1000,1)} km, {art}"+(", durch Wald" if g['forest']>0.5 else (", offen" if g['open']>0.5 else ""))+")."
    else: s2="Keine ausgeprägten Highlight-Abschnitte; eher ruhige Landstraßen."
    tr=c['transfer_share']
    s3=f"Rund {tr*100:.0f} % der Strecke sind Transfer (Orte, Bundesstraße oder schwache Straßen), {c['signals']} Ampeln/Stoppstellen."
    s4=f"Pause bietet sich bei km {de_num(c['pauses'][0]['km'])} an ({c['pauses'][0]['name']})." if c['pauses'] else ""
    return ' '.join(x for x in (s1,s2,s3,s4) if x)
