"""App-Katalog (loops.json) inkl. Score-Aufschlüsselung + Navi-Lenkpunkte; Routen = Kopie von webapp/public/nav/v1 (build_nav.py vorher).
Aufruf: /workspace/scenic/vh/bin/python app/tools/build_assets.py"""
import json,glob,os,shutil,hashlib
ROOT='/workspace/scenic'; D=f'{ROOT}/webapp/public/data'; NAV=f'{ROOT}/webapp/public/nav/v1'; OUT=f'{ROOT}/app/androidApp/src/main/assets'
shutil.rmtree(f'{OUT}/routes',ignore_errors=True); os.makedirs(f'{OUT}/routes')
meta=json.load(open(f'{D}/meta.json')); loops=meta['loops']+json.load(open(f'{D}/all.json'))
idx={r['id']:r for r in json.load(open(f'{NAV}/index.json'))['routes']}
det={}
for f in glob.glob(f'{D}/r/*.json'): det.update(json.load(open(f)))
cat=[]
for L in loops:
    x=det[str(L['id'])]; g=x['g']; n=idx[L['id']]
    shutil.copy(f'{NAV}/{L["id"]}.json',f'{OUT}/routes/{L["id"]}.json')
    cat.append({'id':L['id'],'title':L['ti'],'km':L['km'],'min':L['min'],'score':L['s'],'tour':L['t'],'klasse':L['kl'],'region':L['rg'],'hm':L['hm'],
      'tags':L['tags'],'strengths':L.get('st',[]),'rep':L['r'][1]==1,'family':L['f'][1],'familySize':L['fn'][1],'geom':g,'prof':x['prof'],'profStep':x['step'],'highlights':x['hl'],
      'pauses':x['pauses'],'orte':x['orte'],'text':x['tx'],'navKm':n['km'],'steps':n['steps'],'navMatch':n['match'],'navSha':n['sha256'],'navBytes':n['bytes'],
      'sb':x.get('sb'),'gmaps':x['nav']['g']['wp'] if x.get('nav') else []})
cat.sort(key=lambda c:-c['tour'])
s=json.dumps({'version':2,'start':meta['start'],'sbm':meta['sbm'],'loops':cat},separators=(',',':'),ensure_ascii=False)
open(f'{OUT}/loops.json','w').write(s); open(f'{NAV}/catalog.json','w').write(s)
print(len(cat),'loops',len(s)/1e6,'MB')
