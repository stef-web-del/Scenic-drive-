"""Fahrzeit-Gegenprüfung mit Valhalla (pyvalhalla im env vh/, Tiles valhalla/). Map-Matching der Rundengeometrie
(trace_route, auto) -> Valhalla-Zeit; zusätzlich 'route' über Stützpunkte. Läuft als Subprozess (eigenes venv)."""
import json, subprocess, tempfile, os
from .settings import ROOT
WORKER=r'''
import json,sys,valhalla
a=valhalla.Actor(sys.argv[2]); jobs=json.load(open(sys.argv[1])); out=[]
for j in jobs:
    r={}
    try:
        t=json.loads(a.trace_route(json.dumps({"shape":j["shape"],"costing":"auto","shape_match":"map_snap"})))
        r["trace_s"]=t["trip"]["summary"]["time"]; r["trace_km"]=t["trip"]["summary"]["length"]
    except Exception as e: r["trace_err"]=str(e)[:200]
    try:
        t=json.loads(a.route(json.dumps({"locations":[dict(p,type="through") if 0<i<len(j["via"])-1 else p for i,p in enumerate(j["via"])],"costing":"auto"})))
        r["route_s"]=t["trip"]["summary"]["time"]; r["route_km"]=t["trip"]["summary"]["length"]
    except Exception as e: r["route_err"]=str(e)[:200]
    out.append(r)
json.dump(out,open(sys.argv[1]+".out","w"))
'''
def check(coord_lists, step=8, via_every=25):
    """coord_lists: Liste von [(lon,lat),...] je Runde"""
    jobs=[]
    for c in coord_lists:
        sh=[{"lat":la,"lon":lo} for lo,la in c[::step]]+[{"lat":c[-1][1],"lon":c[-1][0]}]
        jobs.append({"shape":sh,"via":sh[::via_every]+[sh[-1]]})
    d=tempfile.mkdtemp(); f=f'{d}/jobs.json'; json.dump(jobs,open(f,'w')); w=f'{d}/w.py'; open(w,'w').write(WORKER)
    subprocess.run([f'{ROOT}/vh/bin/python',w,f,f'{ROOT}/valhalla/valhalla.json'],check=True,capture_output=True)
    return json.load(open(f+'.out'))
