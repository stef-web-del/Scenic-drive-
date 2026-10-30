"""Valhalla-Fahrzeit für Rundenkandidaten (läuft im Valhalla-venv vh/), gleiche Map-Matching-Einstellung wie die Navi-Dateien
(generate_nav.ferro, interpolation 10 m), damit die angezeigte Dauer der Navi-Dauer entspricht.
  vh/bin/python scenic/vh_time.py in.json out.json   in: {id: [[lon,lat],...]}  out: {id: Minuten|null}"""
import json,sys,os
from multiprocessing import Pool
_SR=os.environ.get('SCENIC_ROOT') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0,f'{_SR}/phase3')
import navpoints as NPJ
def job(it):
    i,g=it
    b={'shape':[{'lon':p[0],'lat':p[1]} for p in g],'costing':'auto','shape_match':'map_snap','format':'osrm',
       'trace_options':{'breakage_distance':10000,'interpolation_distance':10}}
    try:
        t=json.loads(NPJ.A.trace_route(json.dumps(b))); return i,round(sum(m['duration'] for m in t['matchings'])/60,2)
    except Exception: return i,None
if __name__=='__main__':
    D=json.load(open(sys.argv[1]))
    with Pool(8,initializer=NPJ.init) as p: out=dict(p.imap_unordered(job,D.items(),chunksize=4))
    json.dump(out,open(sys.argv[2],'w'))
