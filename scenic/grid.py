"""Startraster 'Runde ab hier' im Testgebiet vorberechnen.
  venv/bin/python -m scenic.grid --radius-km 12 --spacing-km 5 --minutes 45 --out starts/grid-v1
Dichte: Sechseckraster 5 km -> jeder Ort liegt höchstens 2,9 km Luftlinie vom nächsten Rasterpunkt (≈ 4–6 min Anfahrt),
das ist die Spanne, die im Konzept als 'Anfahrt + Runde' noch ehrlich wirkt. Jeder Punkt läuft durch scenic.generate
(--light), anschließend fasst build() alles ins Web-/App-Format grid/v1 zusammen. Bereits fertige Punkte werden übersprungen."""
import argparse,json,math,os,subprocess,sys,time,shutil
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CENTER=(49.7030,9.2575)   # Miltenberg Marktplatz
def hexgrid(c,r_km,s_km):
    lat0,lon0=c; kx=111.32*math.cos(math.radians(lat0)); ky=110.54; pts=[]; n=int(r_km/s_km)+2
    for j in range(-n,n+1):
        for i in range(-n,n+1):
            x=(i+0.5*(j%2))*s_km; y=j*s_km*math.sqrt(3)/2
            if math.hypot(x,y)<=r_km+1e-9: pts.append((round(lat0+y/ky,5),round(lon0+x/kx,5)))
    return pts
def place_name(pts):
    import geopandas as gpd, numpy as np
    P=gpd.read_file(f'{ROOT}/places.gpkg'); P=P[P.place.isin(['village','town','city','hamlet','suburb'])&P.name.notna()]
    P=P.set_geometry(P.geometry.centroid) if P.geom_type.ne('Point').any() else P
    X=np.c_[P.geometry.x.values,P.geometry.y.values]; out=[]
    for la,lo in pts:
        d=np.hypot((X[:,0]-lo)*math.cos(math.radians(la)),X[:,1]-la); out.append(str(P.name.values[d.argmin()]))
    return out
def main():
    a=argparse.ArgumentParser(); a.add_argument('--radius-km',type=float,default=12); a.add_argument('--spacing-km',type=float,default=5)
    a.add_argument('--minutes',type=float,default=45); a.add_argument('--out',default=f'{ROOT}/starts/grid-v1'); a.add_argument('--procs',type=int,default=6)
    a.add_argument('--extra',default=f"{ROOT}/starts/buergstadt-mcd-45",help='zusätzlicher Start (Bürgstadt) als Punkt 0')
    o=a.parse_args(); os.makedirs(o.out,exist_ok=True)
    pts=hexgrid(CENTER,o.radius_km,o.spacing_km); names=place_name(pts)
    plan=[dict(pid=0,lat=49.7067,lon=9.2641,name="McDonald's Bürgstadt")]+[dict(pid=k+1,lat=la,lon=lo,name=f'bei {nm}') for k,((la,lo),nm) in enumerate(zip(pts,names))]
    json.dump(dict(center=CENTER,radius_km=o.radius_km,spacing_km=o.spacing_km,minutes=o.minutes,points=plan),open(f'{o.out}/plan.json','w'),ensure_ascii=False,indent=1)
    print(len(plan),'Rasterpunkte',flush=True); log=[]
    for q in plan:
        d=f"{o.out}/p{q['pid']:03d}"
        if os.path.exists(f'{d}/web.json'): continue
        t=time.time()
        r=subprocess.run([sys.executable,'-m','scenic.generate','--start',f"{q['lat']},{q['lon']}",'--minutes',str(o.minutes),'--name',q['name'],
            '--out',d,'--light','--procs',str(o.procs),'--id-offset',str((100000 if q['pid']==0 else 200000+q['pid']*10000))],cwd=ROOT,capture_output=True,text=True)
        q['s']=round(time.time()-t,1); q['ok']=r.returncode==0
        if not q['ok']: open(f'{d}.err','w').write(r.stdout[-3000:]+r.stderr[-5000:])
        print(q,flush=True)
if __name__=='__main__': main()
