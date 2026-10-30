"""Simulationsfahrt gegen das lokale Nav-Backend: Abweichung + Sperrung -> Rejoin."""
import json, sys, urllib.request, math
B = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8002"
cat = json.load(open("androidApp/src/main/assets/loops.json"))
loops = cat["loops"] if isinstance(cat, dict) else cat
lid = loops[0]["id"]
r = json.load(open(f"androidApp/src/main/assets/routes/{lid}.json"))
def dec(s, p=6):
    i=lat=lon=0; out=[]
    while i < len(s):
        for k in (0,1):
            sh=res=0
            while True:
                b=ord(s[i])-63; i+=1; res|=(b&31)<<sh; sh+=5
                if b<32: break
            d=~(res>>1) if res&1 else res>>1
            if k==0: lat+=d
            else: lon+=d
        out.append((lat/10**p, lon/10**p))
    return out
g = dec(r["route"]["geometry"])
def post(q):
    req=urllib.request.Request(B+"/scenic/rejoin",json.dumps(q).encode(),{"Content-Type":"application/json"})
    return json.load(urllib.request.urlopen(req,timeout=60))
km=r["route"]["distance"]/1000
print(f"Runde {lid}: {km:.1f} km, {len(g)} Punkte")
# 1) Abweichung: Punkt bei ~30 %, 400 m seitlich versetzt
p=g[int(len(g)*.3)]; off=(p[0]+0.0036,p[1])
a=post({"loopId":lid,"lat":off[0],"lon":off[1],"alongM":km*300})
print(f"Abweichung: Rejoin nach {a['route']['distance']/1000:.1f} km Restroute, rejoinAlongM={a['rejoinAlongM']}, ausgelassen={a['skippedM']} m")
# 2) Sperrung 150 m voraus bei ~50 %
q=g[int(len(g)*.5)]; bl=g[int(len(g)*.5)+5]
b=post({"loopId":lid,"lat":q[0],"lon":q[1],"alongM":km*500,"blocked":[{"lat":bl[0],"lon":bl[1]}]})
print(f"Sperrung: neue Route {b['route']['distance']/1000:.1f} km, rejoinAlongM={b['rejoinAlongM']}, ausgelassen={b['skippedM']} m")
assert a["route"]["distance"]>0 and b["skippedM"]>0
print("OK")
