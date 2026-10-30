"""Fasst die Rasterpunkte (starts/grid-v1/pNNN) zum Web-/App-Format grid/v1 zusammen.
  venv/bin/python -m scenic.grid_build [--src starts/grid-v1] [--web webapp/public/grid/v1] [--nav build/grid-nav/v1]
grid/v1/index.json : Punkte (pid, Name, Lage, Anzahl Runden) – klein, für 'nächster Rasterpunkt'.
grid/v1/pNNN.json  : web.json des Punktes (Profile-Rangfolgen + bis zu 20 Runden je Profil) + sha256 je Navigationsdatei.
nav/v1/<id>.json   : Ferrostar-/OSRM-Navigation je Runde (gleiches Format/gleicher Blob-Pfad wie die bestehenden Runden)."""
import argparse,glob,hashlib,json,os,shutil,time
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def main():
    a=argparse.ArgumentParser(); a.add_argument('--src',default=f'{ROOT}/starts/grid-v1')
    a.add_argument('--web',default=f'{ROOT}/webapp/public/grid/v1'); a.add_argument('--nav',default=f'{ROOT}/build/grid-nav/v1')
    o=a.parse_args(); plan=json.load(open(f'{o.src}/plan.json')); os.makedirs(o.web,exist_ok=True); os.makedirs(o.nav,exist_ok=True)
    pts=[]; tot=dict(web=0,nav=0,loops=0,navfiles=0)
    for q in plan['points']:
        d=f"{o.src}/p{q['pid']:03d}"
        if not os.path.exists(f'{d}/web.json'): continue
        w=json.load(open(f'{d}/web.json')); sha={}
        for lid,l in w['loops'].items():
            f=f'{d}/nav/v1/{lid}.json'
            if os.path.exists(f):
                b=open(f,'rb').read(); l['min']=int(round(json.loads(b)['route']['duration']/60)); sha[lid]=hashlib.sha256(b).hexdigest(); shutil.copy(f,f'{o.nav}/{lid}.json'); tot['nav']+=len(b); tot['navfiles']+=1
            l['sha']=sha.get(lid,'')
        win=plan.get('window',[35,50]); bad={lid for lid,l in w['loops'].items() if not (win[0]-0.5<=l['min']<=win[1]+0.5)}
        for lid in bad: w['loops'].pop(lid); sha.pop(lid,None); os.remove(f'{o.nav}/{lid}.json')
        if bad: w['profiles']={k:[dict(d,rank=r+1) for r,d in enumerate(x for x in L if str(x['id']) not in bad)] for k,L in w['profiles'].items()}
        tot['dropped']=tot.get('dropped',0)+len(bad)
        w['pid']=q['pid']; w['name']=q['name']; w['grid']=[q['lat'],q['lon']]
        s=json.dumps(w,ensure_ascii=False,separators=(',',':')); open(f"{o.web}/p{q['pid']:03d}.json",'w').write(s); tot['web']+=len(s.encode())
        n=len(w['loops']); tot['loops']+=n
        pts.append(dict(pid=q['pid'],name=q['name'],lat=q['lat'],lon=q['lon'],node=w.get('node'),n=n,valid=w.get('valid'),
                        minutes=w.get('minutes'),top={p:[r['id'] for r in v[:1]] for p,v in w['profiles'].items()}))
    idx=dict(v=1,generated=time.strftime('%Y-%m-%dT%H:%M:%S%z'),center=plan['center'],radius_km=plan['radius_km'],spacing_km=plan['spacing_km'],
             minutes=plan['minutes'],window=plan.get('window',[35,50]),points=pts)
    open(f'{o.web}/index.json','w').write(json.dumps(idx,ensure_ascii=False,separators=(',',':')))
    tot['points']=len(pts); tot['planned']=len(plan['points']); print(json.dumps(tot))
if __name__=='__main__': main()
