"""Scenic-Routes Navigations-Backend auf Vercel (Testgebiet Miltenberg). Kompatibel zu app/server/navserver.py."""
import json,math,os,sys,threading,time,collections
from http.server import BaseHTTPRequestHandler
HERE=os.path.dirname(os.path.abspath(__file__)); ROOT=os.path.dirname(HERE)
sys.path.insert(0,HERE)
from osrm_merge import merge
T0=time.time()
import valhalla
def prep_tiles(src=f'{ROOT}/tiles',dst='/tmp/tiles'):
    # große Kacheln liegen gesplittet vor (*.partN) -> in /tmp zusammensetzen, Rest verlinken
    if os.path.exists(dst+'/.ok'): return dst
    for d,_,fs in os.walk(src):
        o=d.replace(src,dst,1); os.makedirs(o,exist_ok=True)
        for f in sorted(fs):
            if '.part' in f:
                with open(f"{o}/{f.split('.part')[0]}",'ab') as w: w.write(open(f'{d}/{f}','rb').read())
            elif not os.path.exists(f'{o}/{f}'): os.symlink(f'{d}/{f}',f'{o}/{f}')
    open(dst+'/.ok','w').close(); return dst
# Selbst-Hosting (deploy/ubuntu): SCENIC_NAV_TILE_DIR = fertiges Kachelverzeichnis (ohne .part-Zusammensetzen),
# SCENIC_NAV_CACHE = mjolnir.max_cache_size in Bytes, SCENIC_NAV_TMP = Arbeitsverzeichnis. Ohne Variablen: Verhalten wie auf Vercel.
_TMP=os.environ.get('SCENIC_NAV_TMP','/tmp')
cfg=json.load(open(f'{HERE}/valhalla.json'))
cfg['mjolnir']['tile_dir']=os.environ.get('SCENIC_NAV_TILE_DIR') or prep_tiles(dst=f'{_TMP}/tiles')
if os.environ.get('SCENIC_NAV_CACHE'): cfg['mjolnir']['max_cache_size']=int(os.environ['SCENIC_NAV_CACHE'])
open(f'{_TMP}/valhalla.json','w').write(json.dumps(cfg))
actor=valhalla.Actor(f'{_TMP}/valhalla.json'); lock=threading.Lock()
LOOPS={int(k):v for k,v in json.load(open(f'{ROOT}/data/loops.json')).items()}
INIT_S=round(time.time()-T0,2)
NAV={'costing':'auto','format':'osrm','banner_instructions':True,'voice_instructions':True,'language':'de-DE','units':'kilometers'}
BBOX=(8.50,49.21,10.03,50.20)  # Testgebiet lon/lat
MAX_LOC=25; RATE=(60,300)  # max 60 Anfragen / 5 min je IP und Instanz
HITS=collections.defaultdict(collections.deque)
def inbox(lon,lat): return BBOX[0]<=lon<=BBOX[2] and BBOX[1]<=lat<=BBOX[3]
def check(q):
    pts=[]
    for key in('locations','shape','exclude_locations','sources','targets'):
        v=q.get(key) or []
        if len(v)>(5000 if key=='shape' else MAX_LOC): raise ValueError(f'zu viele {key}')
        pts+=v
    if 'lat' in q: pts.append(q)
    pts+=q.get('blocked',[])
    for p in pts:
        if not inbox(float(p['lon']),float(p['lat'])): raise ValueError('außerhalb des Testgebiets Miltenberg')
def hav(a,b):
    R=6371000;p1,p2=math.radians(a[1]),math.radians(b[1]);dp=p2-p1;dl=math.radians(b[0]-a[0])
    return 2*R*math.asin(math.sqrt(math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2))
def cum(g):
    c=[0.0]
    for i in range(1,len(g)): c.append(c[-1]+hav(g[i-1],g[i]))
    return c
def call(fn,body):
    with lock: return json.loads(getattr(actor,fn)(json.dumps(body)))
def trace(g):
    b=dict(NAV,shape=[{'lon':p[0],'lat':p[1]} for p in g],shape_match='map_snap',trace_options={'breakage_distance':10000,'interpolation_distance':10})
    t=call('trace_route',b); return merge(t['matchings']) if len(t['matchings'])>1 else t['matchings'][0]
def wps(a,b,na='Position',nb='Ziel OBI Miltenberg'):
    return [{'location':a,'name':na,'distance':0},{'location':b,'name':nb,'distance':0}]
def rejoin(q):
    g=LOOPS[int(q['loopId'])]; c=cum(g); pos=[float(q['lon']),float(q['lat'])]
    blocked=[[float(b['lon']),float(b['lat'])] for b in q.get('blocked',[])]
    along=q.get('alongM')
    if along is None: along=c[min(range(len(g)),key=lambda i:hav(g[i],pos))]
    minA=along+300
    for b in blocked:
        j=min(range(len(g)),key=lambda i:hav(g[i],b))
        if c[j]>=along-500: minA=max(minA,c[j]+400)
    cands=[i for i in range(len(g)) if c[i]>=minA] or [len(g)-1]
    picks=[]
    for t in [minA,minA+1500,minA+3500,minA+7000]:
        i=next((i for i in cands if c[i]>=t),cands[-1])
        if i not in picks: picks.append(i)
    best=None
    for i in picks:
        body=dict(NAV,locations=[dict(lon=pos[0],lat=pos[1],type='break',**({'heading':q['heading'],'heading_tolerance':60} if q.get('heading') is not None else {})),
                                {'lon':g[i][0],'lat':g[i][1],'type':'break'}])
        if blocked: body['exclude_locations']=[{'lon':b[0],'lat':b[1]} for b in blocked]
        try: r=call('route',body)['routes'][0]
        except Exception: continue
        skipped=max(0.0,c[i]-along); cost=r['duration']+0.09*skipped
        if best is None or cost<best[0]: best=(cost,i,r,skipped)
    if best is None: raise ValueError('kein Wiedereinstieg gefunden')
    _,i,r,skipped=best; rest=g[i:]
    route=merge([r,trace(rest)]) if len(rest)>1 else r
    return {'route':route,'waypoints':wps(pos,g[-1]),'rejoinAlongM':round(c[i]),'skippedM':round(skipped),'rejoinPoint':g[i]}
class handler(BaseHTTPRequestHandler):
    def _send(self,code,obj,cache=None):
        b=json.dumps(obj,ensure_ascii=False).encode(); self.send_response(code)
        self.send_header('Content-Type','application/json; charset=utf-8')
        self.send_header('Access-Control-Allow-Origin','*'); self.send_header('Access-Control-Allow-Headers','Content-Type')
        self.send_header('Access-Control-Allow-Methods','GET, POST, OPTIONS')
        if cache: self.send_header('Cache-Control',cache)
        self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)
    def _limited(self):
        ip=(self.headers.get('x-forwarded-for') or '?').split(',')[0].strip(); now=time.time(); d=HITS[ip]
        while d and d[0]<now-RATE[1]: d.popleft()
        d.append(now); return len(d)>RATE[0]
    def _path(self):
        from urllib.parse import urlsplit,parse_qs
        u=urlsplit(self.path); q=parse_qs(u.query).get('__p')
        p=q[0] if q else u.path
        if p.startswith('/api/index'): p=p[len('/api/index'):]
        return p.rstrip('/') or '/'
    def do_OPTIONS(self): self._send(204,{})
    def do_GET(self):
        p=self._path()
        try:
            if p=='/health': return self._send(200,{'ok':True,'loops':len(LOOPS),'area':'Testgebiet Miltenberg','bbox':BBOX,'initS':INIT_S,'uptimeS':round(time.time()-T0)})
            if self._limited(): return self._send(429,{'error':'zu viele Anfragen'})
            if p.startswith('/scenic/loop/'):
                g=LOOPS[int(p.rsplit('/',1)[1])]
                return self._send(200,{'route':trace(g),'waypoints':wps(g[0],g[-1],'Start OBI Miltenberg')},'public, s-maxage=86400')
            self._send(404,{'error':'not found'})
        except Exception as e: self._send(400,{'error':str(e)})
    def do_POST(self):
        p=self._path()
        try:
            if self._limited(): return self._send(429,{'error':'zu viele Anfragen'})
            n=int(self.headers.get('Content-Length',0))
            if n>1_000_000: return self._send(413,{'error':'zu groß'})
            q=json.loads(self.rfile.read(n) or b'{}'); check(q)
            if p=='/scenic/rejoin': return self._send(200,rejoin(q))
            if p in('/route','/trace_route','/locate','/height'): return self._send(200,call(p[1:],q))
            self._send(404,{'error':'not found'})
        except Exception as e: self._send(400,{'error':str(e)})

if __name__=='__main__':  # Selbst-Hosting ohne Vercel: python api/index.py  (SCENIC_NAV_HOST/SCENIC_NAV_PORT)
    from http.server import ThreadingHTTPServer
    ThreadingHTTPServer((os.environ.get('SCENIC_NAV_HOST','127.0.0.1'),int(os.environ.get('SCENIC_NAV_PORT','8002'))),handler).serve_forever()
