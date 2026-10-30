"""Referenz-Implementierung der Kurvenzählung (identisch zu shared/nav/DriveStats.kt (Curves)). Aufruf: python3 curves.py <loopId>..."""
import json,math,sys
A='/workspace/scenic/app/androidApp/src/main/assets'
def dec(s,p=1e6):
    i=lat=lon=0;out=[]
    while i<len(s):
        for k in (0,1):
            r=sh=0
            while True:
                b=ord(s[i])-63;i+=1;r|=(b&31)<<sh;sh+=5
                if b<32:break
            v=~(r>>1) if r&1 else r>>1
            if k==0: lat+=v
            else: lon+=v
        out.append((lat/p,lon/p))
    return out
STEP=10.0; NODE_M=25.0; RADIUS=120.0; MIN_TURN=30.0; HAIRPIN=135.0; SMOOTH=3
def xy(pts):
    la0=pts[0][0];k=math.cos(math.radians(la0))*111320
    return [(lo*k,la*110540) for la,lo in pts]
def resample(P):
    out=[P[0]];need=STEP
    for (x0,y0),(x1,y1) in zip(P,P[1:]):
        d=math.hypot(x1-x0,y1-y0);t=need
        while t<=d: out.append((x0+(x1-x0)*t/d,y0+(y1-y0)*t/d));t+=STEP
        need=t-d
    return out
def wrap(a):
    w=(a+180)%360-180
    return 180.0 if w<=-180+1e-6 else w  # Kehrtwende eindeutig +180 (wie Kotlin)
def curves(pts,excl=()):
    R=resample(xy(pts));h=[math.degrees(math.atan2(b[1]-a[1],b[0]-a[0])) for a,b in zip(R,R[1:])]
    d=[wrap(b-a) for a,b in zip(h,h[1:])]
    s=[sum(d[max(0,i-1):i+2])/len(d[max(0,i-1):i+2]) for i in range(len(d))]
    thr=math.degrees(STEP/RADIUS);res=[];i=0
    while i<len(s):
        if abs(s[i])<thr: i+=1;continue
        sg=1 if s[i]>0 else -1;j=i;tot=0.0
        while j<len(s) and s[j]*sg>=thr*0.5: tot+=d[j];j+=1
        if abs(tot)>=MIN_TURN:
            mx=max(abs(s[q]) for q in range(i,j));k=next(q for q in range(i,j) if abs(s[q])>=mx-1e-6);apex=(k+1)*STEP
            if not any(a-NODE_M<=apex<=b+NODE_M for a,b in excl):
                res.append(((i+1)*STEP,abs(tot),abs(tot)>=HAIRPIN,R[i+1]))
        i=j
    return res
def plen(pts):
    P=xy(pts) if pts else [];return sum(math.hypot(b[0]-a[0],b[1]-a[1]) for a,b in zip(P,P[1:]))
ROUND={'roundabout','rotary','exit roundabout','exit rotary','roundabout turn'}
def exclusions(route):
    """Abbiege-/Manöverpunkte als Intervalle (m ab Routenanfang): jedes Manöver außer depart/arrive;
    'new name'/'continue'/'notification' nur an Knoten mit Grad >=3; Kreisverkehre über ihre ganze Länge."""
    pos=0.0;out=[];la0=None
    for leg in route['legs']:
        for st in leg['steps']:
            g=dec(st['geometry']);
            if la0 is None and g: la0=g[0][0]
            L=plen0(g,la0);m=st['maneuver'];t=m['type'];ints=st.get('intersections') or [{}]
            deg=len(ints[0].get('bearings',[]))
            if t in ('roundabout','rotary','roundabout turn'): out.append((pos,pos+L))
            elif t in ('depart','arrive'): pass
            elif t in ('new name','continue','notification'):
                if deg>=3: out.append((pos,pos))
            else: out.append((pos,pos))
            pos+=L
    return out
def plen0(pts,la0):
    k=math.cos(math.radians(la0))*111320;P=[(lo*k,la*110540) for la,lo in pts]
    return sum(math.hypot(b[0]-a[0],b[1]-a[1]) for a,b in zip(P,P[1:]))
if __name__=='__main__':
    L={str(l['id']):l for l in json.load(open(f'{A}/loops.json'))['loops']}
    for id in sys.argv[1:]:
        r=json.load(open(f'{A}/routes/{id}.json'))['route'];g=dec(r['geometry']);c0=curves(g);c=curves(g,exclusions(r));km=L[id]['km']
        print(id,L[id]['title'],km,'km: vorher',len(c0),sum(x[2] for x in c0),'nachher Kurven',len(c),'davon Kehren',sum(x[2] for x in c),'je km %.1f'%(len(c)/km))

def latlon(p,pts):
    la0=pts[0][0];k=math.cos(math.radians(la0))*111320;return (round(p[1]/110540,4),round(p[0]/k,4))
