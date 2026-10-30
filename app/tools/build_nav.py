"""Vorberechnete Ferrostar-Navigation (OSRM-JSON, de-DE) für alle Runden -> webapp/public/nav/v1/<id>.json + index.json
Aufruf: /workspace/scenic/vh/bin/python app/tools/build_nav.py"""
import json,glob,os,sys,math,hashlib,valhalla,numpy as np
sys.path.insert(0,os.path.dirname(__file__)+'/../server'); from osrm_merge import merge
ROOT='/workspace/scenic'; D=f'{ROOT}/webapp/public/data'; OUT=f'{ROOT}/webapp/public/nav/v1'
os.makedirs(OUT,exist_ok=True)
a=valhalla.Actor(f'{ROOT}/valhalla/valhalla.json')
meta=json.load(open(f'{D}/meta.json')); loops=meta['loops']+json.load(open(f'{D}/all.json'))
det={}
for f in glob.glob(f'{D}/r/*.json'): det.update(json.load(open(f)))
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
def xy(pts,lat0):
    P=np.array(pts);return np.c_[P[:,0]*111320*math.cos(math.radians(lat0)),P[:,1]*110540]
def dens(P,step=10):
    o=[P[0]]
    for i in range(1,len(P)):
        d=np.linalg.norm(P[i]-P[i-1]);n=max(1,int(d//step))
        for k in range(1,n+1): o.append(P[i-1]+(P[i]-P[i-1])*k/n)
    return np.array(o)
from scipy.spatial import cKDTree
def match(g,rg,tol=25):
    A=dens(xy(g,g[0][1]));B=dens(xy(rg,g[0][1]))
    ta,tb=cKDTree(A),cKDTree(B)
    return float(min((tb.query(A)[0]<=tol).mean(),(ta.query(B)[0]<=tol).mean()))
def nav(g,interp=10):
    b={'shape':[{'lon':p[0],'lat':p[1]} for p in g],'costing':'auto','shape_match':'map_snap','format':'osrm',
       'banner_instructions':True,'voice_instructions':True,'language':'de-DE','units':'kilometers',
       'trace_options':{'breakage_distance':10000,'interpolation_distance':interp}}
    t=json.loads(a.trace_route(json.dumps(b)))
    R=merge(t['matchings']) if len(t['matchings'])>1 else t['matchings'][0]; R.pop('confidence',None); return R
idx=[];bad=[]
for L in loops:
    i=str(L['id']);g=det[i]['g']
    best=None
    for interp in (10,5,20):
        try: R=nav(g,interp)
        except Exception as e: continue
        m=match(g,dec(R['geometry']))
        if not best or m>best[1]: best=(R,m)
        if m>=0.98: break
    if not best: bad.append(i);continue
    R,m=best
    doc={'version':1,'loopId':L['id'],'match':round(m,4),'route':R,
         'waypoints':[{'location':g[0],'name':'Start OBI Miltenberg','distance':0},{'location':g[-1],'name':'Ziel OBI Miltenberg','distance':0}]}
    s=json.dumps(doc,separators=(',',':'),ensure_ascii=False).encode()
    open(f'{OUT}/{i}.json','wb').write(s)
    idx.append({'id':L['id'],'rep':L['r'][1]==1,'match':round(m,4),'km':round(R['distance']/1000,2),'steps':len(R['legs'][0]['steps']),
                'bytes':len(s),'sha256':hashlib.sha256(s).hexdigest()})
ms=[x['match'] for x in idx]
json.dump({'version':1,'format':'osrm-valhalla-de','count':len(idx),'minMatch':min(ms),'medianMatch':float(np.median(ms)),
  'totalBytes':sum(x['bytes'] for x in idx),'routes':idx},open(f'{OUT}/index.json','w'),separators=(',',':'))
print(len(idx),'ok bad',bad,'min',min(ms),'median',np.median(ms),'<98%',sum(x<0.98 for x in ms),'MB',sum(x['bytes'] for x in idx)/1e6)
