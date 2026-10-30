"""Scenic Routes – lokaler Prototyp „Runde ab Punkt" (nur 127.0.0.1, nichts öffentlich).
  venv/bin/python -m server.server [--port 8765]
API
  POST   /jobs                         {"lat":..,"lon":..,"name":"..","minuten":45,"profil":"sportlich","via":{"lat":..,"lon":..}}
                                        -> {"id":..}; minuten, profil und via sind optional (Default 45 / sportlich / ohne)
  GET    /jobs/{id}?offset=0&limit=6   Status, Fortschritt, Vorschläge (bis zu 6 je Seite, "more" für „Mehr anzeigen")
  GET    /jobs/{id}/loops/{lid}        Geometrie + Höhenprofil einer Runde
  POST   /jobs/{id}/loops/{lid}/nav    Navi-Datei (Ferrostar/OSRM de-DE) nur für diese Runde erzeugen
  DELETE /jobs/{id}                    Abbrechen
  GET    /health
Gleiche Regeln wie scenic.generate: api.passes (Bundesstraße, Ort, Score, kein Hin-und-zurück; unbefestigte Wege sind schon
im Graph ausgeschlossen), Fenster 35–50 min auf der Valhalla-Zeit der reinen Runde, Überschneidung <= 25 % (gepufferte
Geometrie wie generate.gjac). Neu: Kandidaten werden nach Schätzwert sortiert in Blöcken parallel repariert, gültige
Runden sofort geprüft und veröffentlicht (erster Vorschlag früh), Abbruch sobald genug verschiedene Runden da sind."""
import json,os,sys,time,threading,uuid,subprocess,argparse,multiprocessing as mp
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from urllib.parse import urlparse,parse_qs
import numpy as np
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__))); sys.path.insert(0,ROOT)
from scenic import api,profiles,ranking
from scenic.search import Searcher,metrics,backtrack,midxy
from pyproj import Transformer
from shapely.geometry import LineString
from shapely.ops import transform as shp_tr
from shapely.geometry import Point as Point_
ZIEL=(35.0,50.0); VIA_M=2000; SELF_OV_M=1000; KAL=0.864; OV=0.25; PAGE=6; MAX_LOOPS=12; REPAIR_TOP=3000; CHUNK=24; PROCS=int(os.environ.get('SCENIC_PROCS','6')); TIME_BUDGET=90
# Anfahrt und Rückweg auf derselben Straße (außerhalb des Startbereichs): abwerten ab AR_SOFT, aussortieren ab AR_HARD,
# aber nur, wenn schon AR_KEEP Runden stehen. So gehen schwierige Startpunkte (Obernburg) nicht leer aus.
AR_SOFT=0.3; AR_HARD=0.6; AR_PEN=0.6; AR_KEEP=3; AR_ZONE_M=1500
import dataclasses
def window(minuten):
    """Zielzeit -> Fenster der reinen Runde: 45 min heißt 35–50 min (wie im Konzept), begrenzt auf 20–100 min"""
    t=max(30.0,min(90.0,float(minuten))); return (t-10.0,t+5.0)
def prof_score(x,profil):
    """Gewichtung ähnlicher Runden je Profil (entscheidet, welche von zwei ähnlichen bleibt)"""
    if profil=='geniesser': return x['tour']*(0.6+0.8*x['aussicht'])*(1-x['ort'])
    if profil=='entdecker': return x['tour']*(1+min(1.0,x['hm']/800))*(1+0.1*len(x['tags']))
    return x['_sc']
TF=Transformer.from_crs(4326,25832,always_xy=True).transform
class VH:
    """dauerhaft laufender Valhalla-Helfer (vh-venv), spart den Start je Anfrage"""
    def __init__(s):
        s.p=subprocess.Popen([f'{ROOT}/vh/bin/python',f'{ROOT}/server/vh_worker.py'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,
                             stderr=subprocess.DEVNULL,text=True,bufsize=1); s.l=threading.Lock()
    def ask(s,q):
        with s.l: s.p.stdin.write(json.dumps(q)+'\n'); s.p.stdin.flush(); return json.loads(s.p.stdout.readline())
class Engine:
    def __init__(s):
        t=time.time(); s.S={}; s.p=profiles.get('auto_sport',min_min=ZIEL[0]/KAL-3,max_min=ZIEL[1]/KAL+2,max_jaccard=OV)
        s.E,s.RG=api._prep_cached(s.p); midxy(s.RG); s._warm()
        s.vh=VH(); s.vh.ask({'op':'time','items':{}}); s.warm_s=round(time.time()-t,1)
    def _warm(s):
        """Index der Kantenmitten, Knoten-Lookup, Höhenmodell, POIs und Orte einmal laden (sonst ~1,7 s beim ersten Vorschlag)"""
        from scenic.character import analyze
        S=s.searcher(49.7845,9.1795)
        for o in S.candidates(1,50):
            e=S.repair(o)
            if e: m=metrics(s.RG,e); m['eids']=e; analyze(s.RG,s.E,m,with_view=True); break
    def searcher(s,lat,lon):
        k=(round(lat,4),round(lon,4))
        if k not in s.S:
            if len(s.S)>10: s.S.pop(next(iter(s.S)))
            s.S[k]=Searcher(s.RG,(lat,lon))
        return s.S[k]
ENG=None; JOBS={}; RESULT_CACHE={}; RUN=threading.Lock()
_W={}
def _est(so):
    si,o=so; S=_W['SS'][si]; e=S.build(o); m=metrics(S.RG,e) if e is not None else None
    return -1 if m is None else m['score']*(1-m['b_share'])*(1-0.5*m['town_share'])*(1-_W['pen'][si])
def _repair(so):
    si,o=so; S=_W['SS'][si]; e=S.repair(o); return si,e
N_ENTRY=3; ENTRY_MIN=(3.0,14.0); ENTRY_SPACING=3000; ENTRY_TOP=1200
def stem(e):
    """Länge des gemeinsamen Hin-/Rückwegs am Start. Früher nur exakt gespiegelt (e[k]==e[-1-k]) – das scheitert an
    Einbahn-Paaren und kleinen Abweichungen und lieferte fast immer 0. Jetzt: Präfix, dessen Kanten (bis auf 2) auch im
    gleich langen Endstück vorkommen."""
    k=0; A=set(); B=set()
    for i in range(len(e)//2):
        A.add(e[i]); B.add(e[-1-i])
        if len(A-B)<=2 and len(B-A)<=2: k=i+1
        elif i-k>3: break
    return k
def trim_bad(RG,e,k_in,k_out):
    """Anfahrt bis zur ersten Kante außerhalb von Ort und Bundesstraße verlängern (vorne und hinten)."""
    bad=RG.town|RG.isb; n=len(e)
    while k_in<n-k_out-1 and bad[e[k_in]]: k_in+=1
    while k_out<n-k_in-1 and bad[e[n-1-k_out]]: k_out+=1
    return k_in,k_out
_PL=None
def nearest_place(lon,lat):
    """Nächster Ort/Ortsteil (OSM place) zu einem Punkt, für verständliche Einstiegsnamen"""
    global _PL
    if _PL is None:
        import geopandas as gpd
        P=gpd.read_file(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),'places.gpkg'))
        P=P[P.place.isin(['city','town','village','hamlet','suburb'])&P.name.notna()]
        c=P.geometry.representative_point(); _PL=(c.x.values,c.y.values,P.name.values)
    x,y,n=_PL; d=(x-lon)**2*0.42+(y-lat)**2
    return str(n[int(np.argmin(d))])
def entry_label(E,core,si,app,co):
    """'ab Start' oder 'Einstieg bei <Ort>, <Straße>' nach erster Kante der reinen Runde"""
    if si==0 and not app: return 'ab Start'
    from scenic.character import _nm
    lon,lat=co[0]
    r=_nm(E,core[0]); return f"Einstieg bei {nearest_place(lon,lat)}"+('' if r.startswith('namenlose') else f", {r}")
def path_coords(E,eids):
    """Kanten (offene Strecke) in Fahrtrichtung als lon/lat-Liste"""
    from scenic.character import ordered
    if not len(eids): return []
    a=E.geometry.iat[eids[0]].coords
    if len(eids)>1:
        b=E.geometry.iat[eids[1]].coords; ends=[b[0],b[-1]]
        st=max([a[0],a[-1]],key=lambda p:min(np.hypot(*np.subtract(p,q)) for q in ends))
    else: st=a[0]
    return [list(map(float,q)) for q in ordered(E,list(eids),st)[0]]
def pick_entries(RG,S):
    """Einstiege: gute Kanten außerhalb Ort/Bundesstraße, 3–14 min Fahrt vom Start, mindestens 3 km auseinander"""
    import ast
    E=RG.E; mx,my=midxy(RG); sx,sy=RG._xy[S.s]; T0=S.T[0]
    u=np.array([RG.nid[x] for x in E.u.values]) if not hasattr(RG,'_un') else RG._un; RG._un=u
    t=T0[u]/60; d=np.hypot(mx-sx,my-sy)
    ok=np.where((t>=ENTRY_MIN[0])&(t<=ENTRY_MIN[1])&(d>2000)&(~RG.town)&(~RG.isb)&(E.len_m.values>300))[0]
    ok=ok[np.argsort(-(E.score.values[ok]-0.01*t[ok]))]; sel=[]
    for i in ok:
        if all(np.hypot(mx[i]-mx[j],my[i]-my[j])>ENTRY_SPACING for j in sel): sel.append(i)
        if len(sel)>=N_ENTRY: break
    out=[]
    for i in sel:
        n=int(u[i]); lon,lat=ast.literal_eval(RG.nodes[n]) if isinstance(RG.nodes[n],str) else RG.nodes[n]; out.append((float(lat),float(lon),round(float(t[i]),1)))
    return out
class Job:
    def __init__(j,lat,lon,name,minuten=45,profil='sportlich',via=None):
        j.id=uuid.uuid4().hex[:10]; j.lat,j.lon,j.name=lat,lon,name; j.ziel=window(minuten); j.profil=profil; j.via=via; j.verworfen=dict(ueberschneidung=0,via=0,an_rueck=0); j.cancel=False; j.status='queued'; j.t0=time.time()
        j.phase='wartet'; j.tried=0; j.total=0; j.valid=0; j.checked=0; j.first_s=None; j.done_s=None; j.loops=[]; j.err=None; j.final=False; j.nav={}; j.marks={}; j.entries=[]
    def view(j,off=0,lim=PAGE):
        L=j.loops[off:off+lim]
        return dict(id=j.id,status=j.status,final=j.final,start=dict(lat=j.lat,lon=j.lon,name=j.name),ziel=list(j.ziel),profil=j.profil,via=j.via,verworfen=j.verworfen,
            marks=j.marks,progress=dict(phase=j.phase,tried=j.tried,total=j.total,valid=j.valid,vh_checked=j.checked,elapsed_s=round((j.done_s or time.time()-j.t0),1)),
            entries=j.entries,first_s=j.first_s,count=len(j.loops),offset=off,more=off+lim<len(j.loops) or (j.status=='running' and off+lim>=len(j.loops)),
            loops=[{k:v for k,v in x.items() if k not in ('coords','eids','geo','prof','anfahrt_coords','rueckweg_coords')} for x in L],error=j.err)
    def run(j):
        with RUN:
            if j.cancel: j.status='cancelled'; return
            j.status='running'; p0=ENG.RG.p
            ENG.RG.p=dataclasses.replace(p0,min_min=j.ziel[0]/KAL-3,max_min=j.ziel[1]/KAL+2)   # Suchfenster je Auftrag
            try: j._run()
            except Exception as ex: import traceback; traceback.print_exc(); j.status='error'; j.err=str(ex)
            finally: ENG.RG.p=p0
            j.done_s=round(time.time()-j.t0,1)
    def _run(j):
        """Kandidaten aus dem Startpunkt selbst und aus bis zu 3 Einstiegen außerhalb von Ort/Bundesstraße. Regeln (api.passes,
        35–50 min) gelten für die reine Runde; Anfahrt und Rückweg werden getrennt gerechnet und ausgewiesen.
        Auswahl: veröffentlichte Runden behalten ihren Platz (keine Umsortierung). Ist eine neue Runde besser als genau eine
        ähnliche schon gezeigte, ersetzt sie diese auf demselben Platz."""
        E,RG=ENG.E,ENG.RG; p=dataclasses.replace(ENG.p,min_min=RG.p.min_min,max_min=RG.p.max_min); ZIEL=j.ziel
        vxy=TF(j.via['lon'],j.via['lat']) if j.via else None
        j.phase='Startpunkt + Bäume'; S0=ENG.searcher(j.lat,j.lon)
        sx,sy=RG._xy[S0.s]; px,py=TF(j.lon,j.lat); snap=float(np.hypot(px-sx,py-sy))
        SS=[S0]; pen=[0.0]; j.marks['baeume_s']=round(time.time()-j.t0,2)
        def ranked(srcs,cap):
            """Kandidaten der Quellen srcs, parallel geschätzt, je Quelle die besten cap[si]"""
            orders=[(si,o) for si in srcs for o in SS[si].candidates(3,20000 if si==0 else 8000)]
            _W['SS']=SS; _W['pen']=pen
            with mp.get_context('fork').Pool(PROCS) as pl: est=np.array(pl.map(_est,orders,chunksize=500))
            top=[]
            for si in srcs:   # je Quelle die besten, damit Einstiege nicht vom Start verdrängt werden
                ix=[i for i in range(len(orders)) if orders[i][0]==si]; top+=sorted(ix,key=lambda i:-est[i])[:cap[si]]
            return [orders[i] for i in sorted(top,key=lambda i:-est[i])]
        j.phase='Kandidaten'; o0=ranked([0],{0:REPAIR_TOP}); j.total=len(o0)
        from scenic.character import analyze
        seen=set(); acc=[]; geos=[]; bufs=[]; esets=[]
        def ejac(a,b):
            i=RG.len[list(a&b)].sum(); return i/max(1,RG.len[list(a|b)].sum())
        j.phase='Suche'; j.marks['kandidaten_s']=round(time.time()-j.t0,2); T={'A':0.0,'V':0.0}; nid=[0]
        def _h(si,e):
                if e is None: return
                S=SS[si]; key=hash(tuple(e))
                if key in seen: return
                seen.add(key)
                if si==0: ki,ko=trim_bad(RG,e,stem(e),stem(e)); app=list(e[:ki]); core=list(e[ki:len(e)-ko]); ret=list(e[len(e)-ko:])
                else: app=S0.leg(0,S.s); ret=S0.leg_home(S.s); core=list(e)
                if len(core)<3: return
                m=metrics(RG,core); m['backtrack']=backtrack(RG,core,S._zone()); m['eids']=core
                if not api.passes(m,p): return
                j.valid+=1; es=set(core)
                sc=m['score']*(1-m['b_share'])*(1-0.5*m['town_share'])
                near=[i for i,x in enumerate(esets) if ejac(es,x)>OV+0.1]
                if len(near)>1 or (near and j.profil=='sportlich' and acc[near[0]]['_sc']>=sc): return
                if 'erste_gueltige_s' not in j.marks: j.marks['erste_gueltige_s']=round(time.time()-j.t0,2)
                t1=time.time(); co=path_coords(E,core); T['A']+=time.time()-t1
                if len(co)<2: return
                g=shp_tr(TF,LineString(co)); b=g.buffer(25)
                if vxy is not None:
                    from shapely.geometry import Point
                    vd=g.distance(Point(vxy))
                    if vd>VIA_M: j.verworfen['via']+=1; return
                def gj(i):
                    sa=g.intersection(bufs[i]).length; sb=geos[i].intersection(b).length; s_=(sa+sb)/2; return s_/(g.length+geos[i].length-s_)
                ovs=[gj(i) for i in range(len(geos))]; hit=[i for i,v in enumerate(ovs) if v>OV]
                if len(hit)>1 or (hit and j.profil=='sportlich' and acc[hit[0]]['_sc']>=sc): return
                ca=path_coords(E,app); cr=path_coords(E,ret); it={'c':co}
                # Anfahrt/Rückweg dürfen sich am Start decken (Stichstück), aber nicht mit der reinen Runde: sonst fährt man
                # dieselbe Straße zweimal und die Navigation kann sich auf dem falschen Durchgang verorten (navsim „abkuerzung“).
                legs=[shp_tr(TF,LineString(x)) for x in (ca,cr) if len(x)>1]
                if legs:
                    from shapely.ops import unary_union
                    lb=unary_union(legs).buffer(25); sov=g.intersection(lb).length
                    sx0,sy0=TF(j.lon,j.lat); sz=Point_(sx0,sy0).buffer(1500)   # Startbereich bleibt erlaubt
                    sov-=g.intersection(lb).intersection(sz).length
                    if sov>SELF_OV_M: j.verworfen['ueberschneidung']+=1; return
                arf=0.0
                if len(ca)>1 and len(cr)>1:   # Anteil der kürzeren Strecke, der außerhalb des Startbereichs doppelt gefahren wird
                    ga,gr=shp_tr(TF,LineString(ca)),shp_tr(TF,LineString(cr)); sz2=Point_(*TF(j.lon,j.lat)).buffer(AR_ZONE_M)
                    ov_a=ga.intersection(gr.buffer(25)).difference(sz2).length; ov_r=gr.intersection(ga.buffer(25)).difference(sz2).length
                    arf=min(1.0,(ov_a+ov_r)/2/max(1.0,min(ga.length,gr.length)))
                    if arf>AR_HARD and len(acc)>=AR_KEEP: j.verworfen['an_rueck']+=1; return
                if len(ca)>1: it['a']=ca
                if len(cr)>1: it['r']=cr
                t1=time.time(); vt=ENG.vh.ask({'op':'time','items':it}); j.checked+=1; T['V']+=time.time()-t1; j.marks['valhalla_s']=round(T['V'],2)
                rein=vt.get('c')
                if rein is None or not ZIEL[0]<=rein<=ZIEL[1]: return
                am=vt.get('a') or 0.0; rm=vt.get('r') or 0.0
                full=list(app)+core+list(ret)
                t1=time.time(); mf=dict(m); mf['eids']=core; mf['time_min']=rein; c=analyze(RG,E,mf,with_view=True); T['A']+=time.time()-t1; j.marks['analyse_s']=round(T['A'],2)
                ak=float(RG.len[np.array(app)].sum())/1000 if app else 0.0; rk=float(RG.len[np.array(ret)].sum())/1000 if ret else 0.0
                nid[0]+=1
                x=dict(id=nid[0],min=round(rein),anfahrt_min=round(am,1),anfahrt_km=round(ak+snap/1000,1),rueckweg_min=round(rm,1),rueckweg_km=round(rk+snap/1000,1),
                    start_abstand_m=round(snap),einstieg=entry_label(E,core,si,app,co),
                    km=round(float(RG.len[np.array(core)].sum())/1000,1),gesamt_min=round(rein+am+rm),hm=int(round(c['ascent_m'])),score=round(m['score'],3),tour=round(c['tour_score'],3),
                    bstrasse=round(m['b_share'],3),ort=round(m['town_share'],3),aussicht=round(float(c.get('view_frac') or 0),3),tags=c.get('tags') or [],text=c.get('text') or '',
                    max_ueberschneidung=round(max([v for i,v in enumerate(ovs) if i not in hit] or [0.0]),3),gefunden_s=round(time.time()-j.t0,1),
                    coords=co,anfahrt_coords=ca,rueckweg_coords=cr,_sc=sc,ersetzt=None)
                if vxy is not None: x['via_m']=round(vd)
                x['an_rueck_ueberlappung']=round(arf,2)
                x['_ps']=prof_score(x,j.profil)*(1-AR_PEN*max(0.0,arf-AR_SOFT)/(1-AR_SOFT))
                if hit and acc[hit[0]]['_ps']>=x['_ps']: return
                if hit:   # bessere ähnliche Runde ersetzt die schwächere auf ihrem Platz
                    h=hit[0]; x['ersetzt']=acc[h]['id']; acc[h]=x; geos[h]=g; bufs[h]=b; esets[h]=es
                else: acc.append(x); geos.append(g); bufs.append(b); esets.append(es)
                for n,y in enumerate(acc): y['rang']=n+1
                j.loops=[{k2:v for k2,v in y.items() if k2 not in ('_sc','_ps')} for y in acc]
                if j.first_s is None: j.first_s=round(time.time()-j.t0,1)
        def scan(orders,stop=None):
            with mp.get_context('fork').Pool(PROCS) as pool:
                for si,e in pool.imap(_repair,orders,chunksize=CHUNK):
                    j.tried+=1
                    if j.cancel: pool.terminate(); return 'cancel'
                    if len(acc)>=MAX_LOOPS or time.time()-j.t0>TIME_BUDGET: pool.terminate(); return 'voll'
                    _h(si,e)
                    if stop and stop(): pool.terminate(); return 'stufe'
            return 'ende'
        # Stufe 1: nur ab Startpunkt, bis der erste Vorschlag steht (oder 600 Kandidaten); Stufe 2: Einstiege dazu
        r=scan(o0[:600],lambda: len(acc)>0)
        done0=j.tried
        if r=='cancel': j.status='cancelled'; return
        if r!='voll':
            j.phase='Einstiege'; ent=pick_entries(RG,S0)
            for (la,lo,tm) in ent:
                if j.cancel: j.status='cancelled'; return
                SS.append(ENG.searcher(la,lo)); pen.append(min(0.4,tm/40)); j.entries.append(dict(lat=la,lon=lo,anfahrt_min_graph=tm))
            j.marks['einstiege_s']=round(time.time()-j.t0,2)
            rest=ranked(list(range(1,len(SS))),{si:ENTRY_TOP for si in range(1,len(SS))})
            o1=sorted(o0[done0:],key=lambda x:0)+[]  # Start-Reihenfolge bleibt
            # Start- und Einstiegskandidaten abwechselnd, damit beide Quellen früh drankommen
            mix=[]; a_,b_=o1,rest
            for i in range(max(len(a_),len(b_))):
                if i<len(a_): mix.append(a_[i])
                if i<len(b_): mix.append(b_[i])
            j.total=done0+len(mix); j.phase='Suche'
            if scan(mix)=='cancel': j.status='cancelled'; return
        for n,y in enumerate(acc): y['rang']=n+1
        j.loops=[{k2:v for k2,v in y.items() if k2 not in ('_sc','_ps')} for y in acc]; j.final=True; j.status='done'; j.phase='fertig'
class H(BaseHTTPRequestHandler):
    def log_message(s,*a): pass
    def out(s,code,ob):
        b=json.dumps(ob,ensure_ascii=False,separators=(',',':')).encode(); s.send_response(code)
        s.send_header('Content-Type','application/json; charset=utf-8'); s.send_header('Content-Length',str(len(b))); s.end_headers(); s.wfile.write(b)
    def parts(s): u=urlparse(s.path); return [x for x in u.path.split('/') if x],parse_qs(u.query)
    def do_GET(s):
        P,q=s.parts()
        if P==['health']: return s.out(200,dict(ok=True,warm_s=ENG.warm_s,jobs=len(JOBS)))
        if len(P)>=2 and P[0]=='jobs' and P[1] in JOBS:
            j=JOBS[P[1]]
            if len(P)==2: return s.out(200,j.view(int(q.get('offset',[0])[0]),int(q.get('limit',[PAGE])[0])))
            if len(P)==4 and P[2]=='loops':
                x=next((x for x in j.loops if x['id']==int(P[3])),None)
                if x: return s.out(200,dict(id=x['id'],coords=x['coords'],anfahrt=x['anfahrt_coords'],rueckweg=x['rueckweg_coords']))
        s.out(404,dict(error='nicht gefunden'))
    def do_POST(s):
        P,q=s.parts(); n=int(s.headers.get('Content-Length') or 0); body=json.loads(s.rfile.read(n) or b'{}')
        if P==['jobs']:
            lat,lon=float(body['lat']),float(body['lon']); mi=float(body.get('minuten') or 45); pr=str(body.get('profil') or 'sportlich')
            if pr not in ('sportlich','geniesser','entdecker'): pr='sportlich'
            via=body.get('via'); via=dict(lat=float(via['lat']),lon=float(via['lon'])) if via else None
            k=(round(lat,4),round(lon,4),round(mi),pr,None if not via else (round(via['lat'],4),round(via['lon'],4)))
            if k in RESULT_CACHE and JOBS[RESULT_CACHE[k]].status=='done': return s.out(200,dict(id=RESULT_CACHE[k],cached=True))
            j=Job(lat,lon,body.get('name','Start'),mi,pr,via); JOBS[j.id]=j; RESULT_CACHE[k]=j.id
            threading.Thread(target=j.run,daemon=True).start(); return s.out(202,dict(id=j.id,cached=False))
        if len(P)==5 and P[0]=='jobs' and P[1] in JOBS and P[2]=='loops' and P[4]=='nav':
            j=JOBS[P[1]]; lid=int(P[3]); x=next((x for x in j.loops if x['id']==lid),None)
            if not x: return s.out(404,dict(error='Runde nicht gefunden'))
            if lid not in j.nav:
                t=time.time(); d=ENG.vh.ask({'op':'nav','id':lid,'coords':(x['anfahrt_coords'] or [])+x['coords']+(x['rueckweg_coords'] or []),'name':j.name})
                if not d or 'error' in d: return s.out(500,dict(error='Navi fehlgeschlagen'))
                d['erzeugt_s']=round(time.time()-t,1); j.nav[lid]=d
            return s.out(200,j.nav[lid])
        s.out(404,dict(error='nicht gefunden'))
    def do_DELETE(s):
        P,_=s.parts()
        if len(P)==2 and P[0]=='jobs' and P[1] in JOBS:
            j=JOBS[P[1]]; j.cancel=True
            if j.status=='queued': j.status='cancelled'
            for k,v in list(RESULT_CACHE.items()):
                if v==j.id: RESULT_CACHE.pop(k)
            return s.out(200,dict(id=j.id,cancel=True))
        s.out(404,dict(error='nicht gefunden'))
if __name__=='__main__':
    ap=argparse.ArgumentParser(); ap.add_argument('--port',type=int,default=int(os.environ.get('SCENIC_PORT','8765'))); ap.add_argument('--host',default=os.environ.get('SCENIC_HOST','127.0.0.1')); o=ap.parse_args()
    ENG=Engine(); print(f'bereit in {ENG.warm_s} s, http://{o.host}:{o.port}',flush=True)
    ThreadingHTTPServer((o.host,o.port),H).serve_forever()
