"""Navi-Daten für einen generate-Ordner (läuft im Valhalla-venv vh/):
  vh/bin/python scenic/generate_nav.py <out> [Startname]
-> <out>/build/web_raw/nav.json (Lenkpunkte Google/Apple, Algorithmus scenic/navpoints.py, wie phase3/navpoints.py)
-> <out>/nav/v1/<id>.json + index.json (Ferrostar/OSRM-JSON de-DE, gleiches Format wie app/tools/build_nav.py)"""
import json,os,sys,glob,hashlib,math,time,numpy as np
from multiprocessing import Pool
ROOT='/workspace/scenic'; sys.path.insert(0,f'{ROOT}/phase3'); sys.path.insert(0,f'{ROOT}/app/server')
import navpoints as NPJ   # phase3/navpoints.py: route(), edges_s(), init(), NP
from osrm_merge import merge
from scipy.spatial import cKDTree
OUT=os.path.abspath(sys.argv[1]); NAME=sys.argv[2] if len(sys.argv)>2 else 'Start'; RAW=f'{OUT}/build/web_raw'
def dec(s,p=1e6):
    i=lat=lon=0;out=[]
    while i<len(s):
        for k in (0,1):
            r=sh=0
            while True:
                b=ord(s[i])-63;i+=1;r|=(b&31)<<sh;sh+=5
                if b<32:break
            d=~(r>>1) if r&1 else r>>1
            if k==0: lat+=d
            else: lon+=d
        out.append((lon/p,lat/p))
    return out
def xy(pts,lat0): P=np.array(pts);return np.c_[P[:,0]*111320*math.cos(math.radians(lat0)),P[:,1]*110540]
def dens(P,step=10):
    o=[P[0]]
    for i in range(1,len(P)):
        d=np.linalg.norm(P[i]-P[i-1]);n=max(1,int(d//step))
        for k in range(1,n+1): o.append(P[i-1]+(P[i]-P[i-1])*k/n)
    return np.array(o)
def match(g,rg,tol=25):
    A=dens(xy(g,g[0][1]));B=dens(xy(rg,g[0][1])); ta,tb=cKDTree(A),cKDTree(B)
    return float(min((tb.query(A)[0]<=tol).mean(),(ta.query(B)[0]<=tol).mean()))
def ferro(g,interp):
    b={'shape':[{'lon':p[0],'lat':p[1]} for p in g],'costing':'auto','shape_match':'map_snap','format':'osrm','banner_instructions':True,
       'voice_instructions':True,'language':'de-DE','units':'kilometers','trace_options':{'breakage_distance':10000,'interpolation_distance':interp}}
    t=json.loads(NPJ.A.trace_route(json.dumps(b))); R=merge(t['matchings']) if len(t['matchings'])>1 else t['matchings'][0]; R.pop('confidence',None); return R
def job(item):
    i,g=item; es=NPJ.edges_s(g); o={app:NPJ.NP.plan(NPJ.route,g,es,lim) for app,lim in NPJ.NP.LIMITS.items()}
    best=None
    for interp in (10,5,20):
        try: R=ferro(g,interp)
        except Exception: continue
        m=match(g,dec(R['geometry']))
        if not best or m>best[1]: best=(R,m)
        if m>=0.98: break
    return i,o,best
if __name__=='__main__':
    t=time.time(); det={}
    for f in glob.glob(f'{RAW}/r/s*.json'): det.update(json.load(open(f)))
    if os.path.exists(f'{OUT}/keep_ids.json'):   # nur ausgewählte Runden (Rang je Fahrstil) navigierbar machen
        K={str(x) for x in json.load(open(f'{OUT}/keep_ids.json'))}; det={k:v for k,v in det.items() if k in K}
    os.makedirs(f'{OUT}/nav/v1',exist_ok=True); nav={}; idx=[]
    with Pool(8,initializer=NPJ.init) as p:
        for i,o,best in p.imap_unordered(job,[(i,d['g']) for i,d in det.items()]):
            nav[i]=o
            if not best: continue
            R,m=best; g=det[i]['g']
            doc={'version':1,'loopId':int(i),'match':round(m,4),'route':R,'waypoints':[{'location':g[0],'name':f'Start {NAME}','distance':0},{'location':g[-1],'name':f'Ziel {NAME}','distance':0}]}
            s=json.dumps(doc,separators=(',',':'),ensure_ascii=False).encode(); open(f'{OUT}/nav/v1/{i}.json','wb').write(s)
            idx.append({'id':int(i),'match':round(m,4),'km':round(R['distance']/1000,2),'steps':len(R['legs'][0]['steps']),'bytes':len(s),'sha256':hashlib.sha256(s).hexdigest()})
    json.dump(nav,open(f'{RAW}/nav.json','w'),separators=(',',':'))
    ms=[x['match'] for x in idx]; idx.sort(key=lambda x:x['id'])
    json.dump({'version':1,'format':'osrm-valhalla-de','count':len(idx),'minMatch':min(ms or [0]),'medianMatch':float((np.median(ms) if ms else 0)),'totalBytes':sum(x['bytes'] for x in idx),'routes':idx},
              open(f'{OUT}/nav/v1/index.json','w'),separators=(',',':'))
    print('Navi',len(idx),'/',len(det),'min',min(ms or [0]),'median',(np.median(ms) if ms else 0),round(time.time()-t,1),'s')
