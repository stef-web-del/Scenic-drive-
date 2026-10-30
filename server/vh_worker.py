"""Valhalla-Helfer für den lokalen Server (läuft im vh-venv). Liest JSON-Zeilen von stdin, antwortet je Zeile.
 {"op":"time","items":{id:[[lon,lat],..]}} -> {id: Minuten|null}   (gleiche Einstellung wie scenic/vh_time.py)
 {"op":"nav","id":..,"coords":[[lon,lat],..],"name":".."} -> Ferrostar/OSRM-Dokument (wie generate_nav.py, nur diese eine Runde)"""
import json,sys,os
OUT=os.fdopen(os.dup(1),"w"); os.dup2(2,1); sys.stdout=sys.stderr   # Valhalla-Logs nicht in den Antwortkanal
sys.path.insert(0,'/workspace/scenic/phase3'); sys.path.insert(0,'/workspace/scenic/app/server')
import navpoints as NPJ
from multiprocessing import Pool
from osrm_merge import merge
def tjob(it):
    i,g=it
    b={'shape':[{'lon':p[0],'lat':p[1]} for p in g],'costing':'auto','shape_match':'map_snap','format':'osrm',
       'trace_options':{'breakage_distance':10000,'interpolation_distance':10}}
    try: t=json.loads(NPJ.A.trace_route(json.dumps(b))); return i,round(sum(m['duration'] for m in t['matchings'])/60,2)
    except Exception: return i,None
def ferro(g,interp):
    b={'shape':[{'lon':p[0],'lat':p[1]} for p in g],'costing':'auto','shape_match':'map_snap','format':'osrm','banner_instructions':True,
       'voice_instructions':True,'language':'de-DE','units':'kilometers','trace_options':{'breakage_distance':10000,'interpolation_distance':interp}}
    t=json.loads(NPJ.A.trace_route(json.dumps(b))); R=merge(t['matchings']) if len(t['matchings'])>1 else t['matchings'][0]; R.pop('confidence',None); return R
if __name__=='__main__':
    NPJ.init(); pool=Pool(4,initializer=NPJ.init)
    for line in sys.stdin:
        q=json.loads(line)
        try:
            if q['op']=='time': r=dict(pool.imap_unordered(tjob,q['items'].items()))
            elif q['op']=='nav':
                g=q['coords']; r=None
                for interp in (10,5,20):
                    try: r=ferro(g,interp); break
                    except Exception: continue
                if r: r={'version':1,'loopId':q['id'],'route':r,'waypoints':[{'location':g[0],'name':f"Start {q.get('name','')}",'distance':0},{'location':g[-1],'name':f"Ziel {q.get('name','')}",'distance':0}]}
            else: r={'error':'op'}
        except Exception as ex: r={'error':str(ex)}
        OUT.write(json.dumps(r,separators=(',',':'))+'\n'); OUT.flush()
