"""Scenic-Routes Navigations-Backend: Valhalla (pyvalhalla) als HTTP-Dienst + Endpunkt 'feste Runde / Wiedereinstieg'.
Start:  /workspace/scenic/vh/bin/python app/server/navserver.py [--host 127.0.0.1] [--port 8002]
Endpunkte:
  GET  /health                       -> {"ok":true,"loops":N}
  POST /route | /trace_route | /locate   Valhalla-API 1:1 (z.B. format=osrm für Ferrostar)
  GET  /scenic/loop/<id>             -> {"route":OSRM-Route,"waypoints":[...]}  (ganze Runde, map-matched)
  POST /scenic/rejoin                {"loopId":384,"lat":..,"lon":..,"heading":deg?,"alongM":gefahrene m?,"blocked":[{"lat","lon"}]}
                                     -> {"route":...,"waypoints":...,"rejoinAlongM":..,"skippedM":..}
"""
import json,glob,math,os,sys,threading,argparse
from http.server import HTTPServer,BaseHTTPRequestHandler
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from osrm_merge import merge
import valhalla
ROOT=os.environ.get('SCENIC_ROOT','/workspace/scenic')
CFG=os.environ.get('VALHALLA_CONFIG',f'{ROOT}/valhalla/valhalla.json')
DATA=os.environ.get('SCENIC_DATA',f'{ROOT}/webapp/public/data')
NAV={'costing':'auto','format':'osrm','banner_instructions':True,'voice_instructions':True,'language':'de-DE','units':'kilometers'}
actor=valhalla.Actor(CFG); lock=threading.Lock()
LOOPS={}
for f in glob.glob(f'{DATA}/r/*.json'):
    for k,v in json.load(open(f)).items(): LOOPS[int(k)]=v['g']
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
    if along is None:  # nächster Rundenpunkt als Fortschritt
        along=c[min(range(len(g)),key=lambda i:hav(g[i],pos))]
    minA=along+300
    for b in blocked:  # Wiedereinstieg erst hinter der Sperrung
        j=min(range(len(g)),key=lambda i:hav(g[i],b))
        if c[j]>=along-500: minA=max(minA,c[j]+400)
    cands=[i for i in range(len(g)) if c[i]>=minA]
    if not cands: cands=[len(g)-1]
    picks=[];targets=[minA,minA+1500,minA+3500,minA+7000]
    for t in targets:
        i=next((i for i in cands if c[i]>=t),cands[-1])
        if i not in picks: picks.append(i)
    best=None
    for i in picks:
        body=dict(NAV,locations=[dict(lon=pos[0],lat=pos[1],type='break',**({'heading':q['heading'],'heading_tolerance':60} if q.get('heading') is not None else {})),
                                {'lon':g[i][0],'lat':g[i][1],'type':'break'}])
        if blocked: body['exclude_locations']=[{'lon':b[0],'lat':b[1]} for b in blocked]
        try: r=call('route',body)['routes'][0]
        except Exception: continue
        skipped=max(0.0,c[i]-along)
        cost=r['duration']+0.09*skipped  # 90 s pro übersprungenem km Runde
        if best is None or cost<best[0]: best=(cost,i,r,skipped)
    if best is None: raise ValueError('kein Wiedereinstieg gefunden')
    _,i,r,skipped=best
    rest=g[i:]
    route=merge([r,trace(rest)]) if len(rest)>1 else r
    return {'route':route,'waypoints':wps(pos,g[-1]),'rejoinAlongM':round(c[i]),'skippedM':round(skipped),'rejoinPoint':g[i]}
class H(BaseHTTPRequestHandler):
    def _send(self,code,obj):
        b=json.dumps(obj,ensure_ascii=False).encode(); self.send_response(code)
        self.send_header('Content-Type','application/json; charset=utf-8'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        try:
            if self.path=='/health': return self._send(200,{'ok':True,'loops':len(LOOPS)})
            if self.path.startswith('/scenic/loop/'):
                g=LOOPS[int(self.path.rsplit('/',1)[1])]; return self._send(200,{'route':trace(g),'waypoints':wps(g[0],g[-1],'Start OBI Miltenberg')})
            self._send(404,{'error':'not found'})
        except Exception as e: self._send(400,{'error':str(e)})
    def do_POST(self):
        try:
            q=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
            p=self.path.split('?')[0].rstrip('/')
            if p=='/scenic/rejoin': return self._send(200,rejoin(q))
            if p in('/route','/trace_route','/locate','/height'): return self._send(200,call(p[1:],q))
            self._send(404,{'error':'not found'})
        except Exception as e: self._send(400,{'error':str(e)})
    def log_message(self,f,*a): sys.stderr.write('%s %s\n'%(self.address_string(),f%a))
if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('--host',default='127.0.0.1'); ap.add_argument('--port',type=int,default=8002); a=ap.parse_args()
    print(f'Scenic navserver auf http://{a.host}:{a.port} ({len(LOOPS)} Runden)',flush=True)
    HTTPServer((a.host,a.port),H).serve_forever()
