"""Runden für beliebigen Start + Fahrzeit erzeugen (Grundlage der späteren Such-API).
  venv/bin/python -m scenic.generate --start 49.7067,9.2641 --minutes 45 --name "McDonald's Bürgstadt" --out starts/buergstadt-mcd-45
Schritte: 1) Suche+Bewertung (gleiche Score-Formel/Regeln wie Phase 3) 2) Charakterisierung aller gültigen Runden (parallel)
3) Rohdaten im web_raw-Format + GPX + summary.json 4) Navi (vh-venv: scenic/generate_nav.py -> Lenkpunkte nav.json + Ferrostar nav/v1)
5) webapp/scripts/compact.py RAW->OUT/data (gleiches Katalogformat, Webapp bleibt unberührt) 6) overview.png (OSM-Kacheln)."""
import argparse,json,os,re,sys,time,subprocess,pickle,multiprocessing as mp
import numpy as np
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEVELS=[0.15,0.25,0.35,0.5,0.7]
BINS=[0.15,0.18,0.21,0.24,9]; KL=['C','B','A','A+']
_G={}
def _char(i):
    from .character import analyze
    a=analyze(_G['RG'],_G['E'],_G['ok'][i],with_view=True)
    keep=('coords','tags','text','ascent_m','orte','pauses','tour_score','highlights','contrib','route_score','topk','curv_mean','tight_mean',
          'rhythm_mean','relief_mean','view_frac','view_far_m','forest','open','town_share','town_entries','b_share','highlight_share',
          'transfer_share','interrupts_per10km','signals','dramaturgy','segments','profile')
    return i,{k:a.get(k) for k in keep}
def region(b):
    if b<100 or b>=345: return 'Spessart (N/NO)'
    if b<190: return 'Bauland/Madonnenländchen (SO/S)'
    return 'Odenwald (SW/W/NW)'
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--start',required=True,help='lat,lon'); ap.add_argument('--minutes',type=float,default=45)
    ap.add_argument('--tol',type=float,default=5); ap.add_argument('--name',default='Start'); ap.add_argument('--out',required=True)
    ap.add_argument('--profil',default='auto_sport'); ap.add_argument('-n','--max-results',dest='n',type=int,default=6)
    ap.add_argument('--ueberschneidung',type=float,default=0.25); ap.add_argument('--kombis',type=int,default=20000)
    ap.add_argument('--repair-top',type=int,default=3000); ap.add_argument('--id-offset',type=int,default=100000)
    ap.add_argument('--procs',type=int,default=6); ap.add_argument('--skip-nav',action='store_true')
    ap.add_argument('--keep',type=int,default=20,help='Runden je Fahrstil behalten (15–20)'); ap.add_argument('--light',action='store_true',help='Raster: ohne GPX/Übersichtsbild')
    ap.add_argument('--n6',type=int,default=6)
    ap.add_argument('--ziel',default='35,50',help='Fenster der reinen Rundendauer in Valhalla-Minuten (Stefan 29.10.: 35–50); leer = altes Graph-Fenster minutes±tol')
    ap.add_argument('--kal',type=float,default=0.864,help='Valhalla-/Graph-Zeit (Median grid-v1, 410 Runden), nur für das Suchfenster')
    o=ap.parse_args(); lat,lon=map(float,o.start.split(',')); out=os.path.abspath(o.out); raw=f'{out}/build/web_raw'; os.makedirs(f'{raw}/r',exist_ok=True)
    ov=o.ueberschneidung/100 if o.ueberschneidung>1 else o.ueberschneidung
    from . import api,profiles,export
    from .search import cluster
    from pyproj import Transformer
    from shapely.geometry import LineString
    T={}; t0=t=time.time()
    Z=tuple(map(float,o.ziel.split(','))) if o.ziel else None
    if Z: gw=(Z[0]/o.kal-3,Z[1]/o.kal+2)   # Graph-Suchfenster großzügig, die Valhalla-Zeit entscheidet unten hart
    else: gw=(o.minutes-o.tol,o.minutes+o.tol)
    p=profiles.get(o.profil,min_min=gw[0],max_min=gw[1],max_jaccard=ov)
    E,RG=api._prep_cached(p); T['prepare_s']=time.time()-t; t=time.time()
    E,RG,S,routes=api.evaluate_all((lat,lon),p,k_max=3,max_combos=o.kombis,E_RG=(E,RG),repair_top=o.repair_top); T['search_s']=time.time()-t; t=time.time()
    win=[r for r in routes if p.min_min<=r['time_min']<=p.max_min]
    ok=[r for r in routes if api.passes(r,p)]; VH={}; VHF=None
    fam={th:cluster(RG,ok,th) for th in LEVELS}; T['cluster_s']=time.time()-t; t=time.time()
    _G.update(RG=RG,E=E,ok=ok)
    with mp.get_context('fork').Pool(o.procs) as pool: C=dict(pool.imap_unordered(_char,range(len(ok)),chunksize=4))
    T['character_s']=time.time()-t; t=time.time()
    if Z and ok:   # harte Grenze auf der echten Navi-Dauer (Valhalla), nicht auf der Graph-Schätzung
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            json.dump({str(k):[list(map(float,c)) for c in C[k]['coords']] for k in range(len(ok))},open(f'{td}/i.json','w'))
            subprocess.run([f'{ROOT}/vh/bin/python',f'{ROOT}/scenic/vh_time.py',f'{td}/i.json',f'{td}/o.json'],check=True)
            vt=json.load(open(f'{td}/o.json'))
        n0=len(ok); keep=[k for k in range(n0) if vt.get(str(k)) is not None and Z[0]<=vt[str(k)]<=Z[1]]
        ok=[ok[k] for k in keep]; C={k2:C[k] for k2,k in enumerate(keep)}; VH={k2:vt[str(k)] for k2,k in enumerate(keep)}; VHF=[len(ok),n0]; p.vh_window=list(Z); fam={th:cluster(RG,ok,th) for th in LEVELS}
    # Fahrstile + Auswahl (scenic/ranking.py): je Stil bis zu --keep Runden mit Rang, Top 6 mit <= ov Überschneidung
    from . import ranking
    from pyproj import Transformer as _T
    from shapely.ops import transform as _tr
    _tf=_T.from_crs(4326,25832,always_xy=True).transform
    GEO=[_tr(_tf,LineString(C[i]['coords'])) for i in range(len(ok))]; BUF={}
    def gjac(a,b):
        for x in (a,b):
            if x not in BUF: BUF[x]=GEO[x].buffer(25)
        sa=GEO[a].intersection(BUF[b]).length; sb=GEO[b].intersection(BUF[a]).length; s=(sa+sb)/2
        return s/(GEO[a].length+GEO[b].length-s)
    RANK=ranking.select(ok,[C[i] for i in range(len(ok))],gjac,ov=ov,keep=o.keep) if ok else {k:[] for k in ranking.PROFILES}
    sel=[d['i'] for d in RANK.get('sportlich',[])][:o.n]
    T['ranking_s']=time.time()-t; t=time.time()
    # Rohdaten im web_raw-Format (wie phase3/export_web.py)
    tf=Transformer.from_crs(4326,25832,always_xy=True); inv=Transformer.from_crs(25832,4326,always_xy=True); sx,sy=tf.transform(lon,lat)
    mid=E.geometry.to_crs(25832).interpolate(0.5,normalized=True); MX,MY=mid.x.values,mid.y.values; low=E.z_mean.values<160
    from .character import elev
    def ll(g): a,b=inv.transform(*zip(*g.coords)); return [[round(x,5),round(y,5)] for x,y in zip(a,b)]
    meta=[];lines={};SH={};pauses={};parts={}
    for k,r in enumerate(ok):
        c=C[k]; rid=o.id_offset+k; P=np.column_stack(tf.transform(*zip(*c['coords']))); g=LineString(P)
        e=np.array(r['eids']); L=RG.len[e]; cx=(MX[e]*L).sum()/L.sum(); cy=(MY[e]*L).sum()/L.sum()
        rg=region((np.degrees(np.arctan2(cx-sx,cy-sy))+360)%360)
        if (L*low[e]).sum()/L.sum()>0.35: rg='Maintal (>35 % Tallage)'
        lines[rid]=[[round(a,4),round(b,4)] for a,b in ll(g.simplify(35))]
        d=np.arange(0,g.length,100.0); z=elev(np.array([g.interpolate(x).coords[0] for x in d]))
        import pandas as pd; z=pd.Series(z).interpolate().bfill().ffill().round().astype(int).tolist()
        ps=[]
        for q in (c.get('pauses') or []):
            lo,la=q['lonlat']; ps.append(dict(n=q['name'],k=q['kind'],km=float(q['km']),ll=[round(lo,5),round(la,5)]))
            pp=pauses.setdefault(str(q['name']),dict(n=q['name'],k=q['kind'],ll=[round(lo,5),round(la,5)],c=0)); pp['c']+=1
        hl=[dict(km=round(float(h['km0']),1),len=int(h['len_m']),s=round(float(h['score']),3)) for h in (c.get('highlights') or [])]
        SH.setdefault(rid%32,{})[str(rid)]=dict(id=rid,g=ll(g.simplify(8)),prof=z,step=100,pauses=ps,hl=hl,orte=c.get('orte') or [])
        kl=KL[int(np.searchsorted(BINS,r['score'],side='right'))-1]
        meta.append(dict(id=rid,km=round(r['len_km'],1),min=int(round(VH.get(k,r['time_min']))),s=round(r['score'],3),t=round(c['tour_score'],3),kl=kl,rg=rg,
            hm=int(round(c['ascent_m'])),tags=c.get('tags') or [],tx=c.get('text') or '',f=[int(fam[th][0][k]) for th in LEVELS],
            r=[int(k in set(fam[th][1])) for th in LEVELS],bb=[round(float(x),4) for x in (lambda a:[a[:,0].min(),a[:,1].min(),a[:,0].max(),a[:,1].max()])(np.array(lines[rid]))]))
        q={kk:c[kk] for kk in c if kk not in ('coords','tags','text','orte','pauses','highlights','profile')}; q['len_m']=float(c['profile'][0][-1]); parts[rid]=q
    J=lambda ob,f: json.dump(ob,open(f,'w'),ensure_ascii=False,separators=(',',':'))
    for kk in range(32): J(SH.get(kk,{}),f'{raw}/r/s{kk}.json')
    pz={'type':'FeatureCollection','features':[dict(type='Feature',properties=dict(n=q['n'],k=q['k'],c=q['c']),geometry=dict(type='Point',coordinates=q['ll'])) for q in sorted(pauses.values(),key=lambda q:-q['c'])]}
    J(dict(levels=LEVELS,start=[lon,lat],startName=o.name,loops=meta),f'{raw}/meta.json'); J(lines,f'{raw}/lines.json')
    J(dict(heat={'type':'FeatureCollection','features':[]},refs={'type':'FeatureCollection','features':[]},pauses=pz),f'{raw}/layers.json')
    os.makedirs(f'{out}/cache',exist_ok=True); pickle.dump(parts,open(f'{out}/cache/parts.pkl','wb'))
    os.makedirs(f'{out}/gpx',exist_ok=True); top=[]
    for n,i in enumerate(sel):
        if not o.light: r=dict(ok[i]); r['char']=C[i]; export.route_files(r,n,f'{out}/gpx')
        top.append(o.id_offset+i)
    T['export_s']=time.time()-t; t=time.time()
    summ=dict(start=dict(lat=lat,lon=lon,name=o.name,node=int(S.s),node_latlon=[round(float(v),6) for v in inv.transform(*RG._xy[S.s])[::-1]]),
        minutes=o.minutes,window=list(Z) if Z else [p.min_min,p.max_min],graph_window=[round(p.min_min,1),round(p.max_min,1)],min_source='valhalla' if Z else 'graph',vh_filter=VHF,max_jaccard=ov,candidates=len(routes),in_window=len(win),valid=len(ok),
        families={str(th):len(fam[th][1]) for th in LEVELS},selected=top,
        ranking={st:[dict(d,id=o.id_offset+d.pop('i')) for d in [dict(x) for x in L]] for st,L in RANK.items()},params={k:v for k,v in p.__dict__.items() if k!='avoid_eids'})
    J(summ,f'{out}/summary.json')
    keep_ids=sorted({d['id'] for L in summ['ranking'].values() for d in L}); J(keep_ids,f'{out}/keep_ids.json')
    if not o.skip_nav:
        subprocess.run([f'{ROOT}/vh/bin/python',f'{ROOT}/scenic/generate_nav.py',out,o.name],check=True); T['nav_s']=time.time()-t; t=time.time()
    os.makedirs(f'{out}/data',exist_ok=True)
    subprocess.run([sys.executable,f'{ROOT}/webapp/scripts/compact.py',raw,f'{out}/data'],check=True,cwd=out)
    from .postprocess import finish; finish(out)
    if not o.light:
      try:
        from .overview_png import overview; overview(out)
      except Exception as ex: print('overview fehlgeschlagen',ex)
    T['total_s']=time.time()-t0
    summ['timing']={k:round(v,1) for k,v in T.items()}; J(summ,f'{out}/summary.json'); print(json.dumps(summ['timing']))
if __name__=='__main__': main()
