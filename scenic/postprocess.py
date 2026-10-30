"""Nachbearbeitung eines generate-Ordners (früher /tmp/post.py): Namen, Google-Link, Highlights, paarweise Überschneidung
der Top-Runden, Überschneidung mit den Miltenberg-Vertretern -> runden.json; dazu web.json (kompaktes Web-/App-Format
'Runde ab hier': alle behaltenen Runden mit Rang je Fahrstil, Gründen, Geometrie, Höhenprofil)."""
import json,glob,os,pickle
from shapely.geometry import LineString
from shapely.ops import transform
from pyproj import Transformer
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_tf=Transformer.from_crs(4326,25832,always_xy=True).transform
def shards(d):
    R={}
    for f in glob.glob(f'{d}/r/s*.json'): R.update({int(k):v for k,v in json.load(open(f)).items()})
    return R
def share(a,b): return a.intersection(b.buffer(25)).length/a.length
def jac(a,b):
    s=(share(a,b)*a.length+share(b,a)*b.length)/2; return s/(a.length+b.length-s)
def gurl(S,wp):
    if not wp: return None
    return ('https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&waypoints=%s&travelmode=driving&dir_action=navigate'
            %(S['start']['lat'],S['start']['lon'],'|'.join('%.6f,%.6f'%(b,a) for a,b in wp)))
def finish(O,compare_miltenberg=True):
    S=json.load(open(f'{O}/summary.json')); sel=S['selected']; rank=S.get('ranking',{})
    R=shards(f'{O}/build/web_raw'); M={l['id']:l for l in json.load(open(f'{O}/data/meta.json'))['loops']}
    if os.path.exists(f'{O}/data/all.json'): M.update({l['id']:l for l in json.load(open(f'{O}/data/all.json')) if l['id'] not in M})
    N=json.load(open(f'{O}/build/web_raw/nav.json')) if os.path.exists(f'{O}/build/web_raw/nav.json') else {}
    lines=json.load(open(f'{O}/build/web_raw/lines.json')); P=pickle.load(open(f'{O}/cache/parts.pkl','rb'))
    G={i:transform(_tf,LineString(R[i]['g'])) for i in sel}
    pw=[(sel[i],sel[j],round(jac(G[sel[i]],G[sel[j]]),3)) for i in range(len(sel)) for j in range(i+1,len(sel))]
    MG={}
    if compare_miltenberg and os.path.exists(f'{ROOT}/phase3/out/web_raw/meta.json'):
        mm=json.load(open(f'{ROOT}/phase3/out/web_raw/meta.json')); reps=[l['id'] for l in mm['loops'] if l['r'][1]]
        MR=shards(f'{ROOT}/phase3/out/web_raw'); MG={i:transform(_tf,LineString(MR[i]['g'])) for i in reps if i in MR}
    def name(m): return m['ti'] if 'km)' in m['ti'] else f"{m['ti']} ({round(m['km'])} km)"
    def wp(i):
        g=(N.get(str(i)) or {}).get('google') or {}; return (g.get('single') or {}).get('wp'), (g.get('single') or {}).get('f')
    out=[]
    for n,i in enumerate(sel,1):
        m=M[i]; w,f=wp(i)
        best=max(((share(G[i],v),k) for k,v in MG.items()),default=(0,None))
        hl=sorted(R[i]['hl'],key=lambda h:-h['s'])[:3]
        out.append(dict(n=n,id=i,name=name(m),km=m['km'],min=m['min'],s=m['s'],t=m['t'],kl=m['kl'],hm=m['hm'],rg=m['rg'],tags=m['tags'],
          hl=[[h['km'],h['len'],h['s']] for h in hl],gf=f,url=gurl(S,w),ovM=[round(best[0],2),best[1]],orte=R[i]['orte']))
    json.dump(out,open(f'{O}/runden.json','w'),ensure_ascii=False,indent=1)
    S['names']={str(r['id']):r['name'] for r in out}; S['pairwise_overlap']=pw
    json.dump(S,open(f'{O}/summary.json','w'),ensure_ascii=False,indent=1)
    # web.json
    ids=sorted({d['id'] for L in rank.values() for d in L}); navidx={}
    if os.path.exists(f'{O}/nav/v1/index.json'): navidx={x['id']:x for x in json.load(open(f'{O}/nav/v1/index.json'))['routes']}
    loops={}
    for i in ids:
        m=M[i]; q=P[i]; d=R[i]; w,_=wp(i)
        loops[str(i)]=dict(id=i,ti=name(m),km=m['km'],min=m['min'],t=m['t'],s=m['s'],kl=m['kl'],hm=m['hm'],rg=m['rg'],tags=m['tags'],
            g=lines[str(i)],prof=d['prof'][::5],pstep=500,hl=d['hl'],orte=d['orte'][:12],pausen=[dict(n=p['n'],k=p['k'],km=p['km'],ll=p['ll']) for p in d['pauses'][:8] if isinstance(p['n'],str)],
            m=dict(kurven=round(q['curv_mean'],3),aussicht=round(q['view_frac'],3),ort=round(q['town_share'],3),b=round(q['b_share'],3),
                   hl=round(float(q['highlight_share']),3),unterbr=round(q['interrupts_per10km'],2)),
            nav=bool(i in navidx),navMatch=(navidx.get(i) or {}).get('match'),gurl=gurl(S,w))
    W=dict(v=1,start=dict(lat=S['start']['lat'],lon=S['start']['lon'],name=S['start']['name']),node=S['start']['node_latlon'],minutes=S['minutes'],
           window=S['window'],maxOverlap=S['max_jaccard'],valid=S['valid'],candidates=S['candidates'],
           profiles={k:[{x:d[x] for x in ('id','rank','score','maxov','why') if x in d} for d in L] for k,L in rank.items()},loops=loops)
    json.dump(W,open(f'{O}/web.json','w'),ensure_ascii=False,separators=(',',':'))
    return W
if __name__=='__main__':
    import sys; finish(sys.argv[1])
