"""Rundensuche: Start -> A -> B (-> C) -> Start über score-gewichtete Kosten, Wiederbefahrung bestraft."""
import pickle, numpy as np, geopandas as gpd, networkx as nx, itertools, random, json, os
from pyproj import Transformer
from config import *
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),'out')
COST_BASE=0.3; COST_BAD=1.7; REUSE_PEN=8.0; MAX_OVERLAP=0.08
ANCH_MIN=3000; ANCH_MAX=15000; ANCH_SPACING=1500
E=gpd.read_file(f'{OUT}/edges.gpkg'); M=pickle.load(open(f'{OUT}/graph.pkl','rb'))
sc=E.score.values; tm=E.time_s.values; ln=E.len_m.values
G=nx.Graph(); EUV={}
for u,v,k,d in M.edges(keys=True,data=True):
    EUV[d['eid']]=(u,v)
    c=d['time']*(COST_BASE+COST_BAD*(1-d['score']))
    if not G.has_edge(u,v) or G[u][v]['cost']>c: G.add_edge(u,v,cost=c,eid=d['eid'])
G=G.subgraph(max(nx.connected_components(G),key=len)).copy()
nodes=np.array(list(G.nodes)); tf=Transformer.from_crs(4326,25832,always_xy=True)
nx_,ny_=tf.transform(nodes[:,0],nodes[:,1])
sx,sy=tf.transform(START[1],START[0])
# Startknoten: nächster Knoten an Straße (nicht residential-Sackgasse)
start=tuple(nodes[np.argmin((nx_-sx)**2+(ny_-sy)**2)])
def path_edges(p): return [G[a][b]['eid'] for a,b in zip(p[:-1],p[1:])]
def route(a,b,used):
    def w(x,y,d): return d['cost']*(REUSE_PEN if d['eid'] in used else 1)
    return nx.dijkstra_path(G,a,b,weight=w)
def evaluate(eids):
    eids=list(eids); t=tm[eids].sum(); L=ln[eids].sum()
    cnt={}; [cnt.__setitem__(e,cnt.get(e,0)+1) for e in eids]
    rep=sum(ln[e]*(c-1) for e,c in cnt.items())/L
    s=(sc[eids]*ln[eids]).sum()/L
    return dict(time=t,len=L,score=s,overlap=rep)
def anchors(n=90):
    P=E.to_crs(25832); mid=P.geometry.interpolate(0.5,normalized=True)
    d=np.hypot(mid.x-sx,mid.y-sy)
    c=E[(d>ANCH_MIN)&(d<ANCH_MAX)&(E.len_m>300)].copy(); c['nu']=[EUV.get(i,(None,None))[0] for i in c.index]; c['mx']=mid.x[c.index]; c['my']=mid.y[c.index]
    c=c.sort_values('score',ascending=False); sel=[]
    for r in c.itertuples():
        if r.nu not in G: continue
        if all(np.hypot(r.mx-s.mx,r.my-s.my)>ANCH_SPACING for s in sel): sel.append(r)
        if len(sel)>=n: break
    return sel
def find(n_try=600, seed=1):
    A=anchors(); random.seed(seed); res=[]
    ang={a.Index:np.arctan2(a.my-sy,a.mx-sx) for a in A}
    combos=[c for c in itertools.combinations(A,2)]+[c for c in itertools.combinations(A,3)]
    random.shuffle(combos)
    for combo in combos[:n_try]:
        combo=sorted(combo,key=lambda a:ang[a.Index])   # im Kreis anordnen
        if len(combo)>1:
            da=np.degrees(abs((ang[combo[-1].Index]-ang[combo[0].Index]+np.pi)%(2*np.pi)-np.pi))
            if da<35 or da>200: continue
        used=set(); full=[]; pts=[start]+[a.nu for a in combo]+[start]
        try:
            for a,b in zip(pts[:-1],pts[1:]):
                p=route(a,b,used); e=path_edges(p); used|=set(e); full+=e
        except nx.NetworkXNoPath: continue
        ev=evaluate(full)
        if LOOP_MIN<=ev['time']<=LOOP_MAX and ev['overlap']<=MAX_OVERLAP:
            ev['eids']=full; ev['anchors']=[int(a.Index) for a in combo]; res.append(ev)
    return res
def pick(res,k=5,jac=0.25):
    out=[]
    for r in sorted(res,key=lambda r:-(r['score']-0.5*r['overlap'])):
        s=set(r['eids'])
        if all(len(s&set(o['eids']))/len(s|set(o['eids']))<jac for o in out): out.append(r)
        if len(out)>=k: break
    return out
if __name__=='__main__':
    import sys
    res=find(int(sys.argv[1]) if len(sys.argv)>1 else 600)
    print('gültige Runden',len(res))
    top=pick(res)
    for r in top: print(round(r['score'],3),round(r['len']/1000,1),'km',round(r['time']/60),'min overlap',round(r['overlap'],3))
    json.dump(dict(start=start,top=top,all=[{k:v for k,v in r.items() if k!='eids'} for r in res]),open(f'{OUT}/loops.json','w'),default=float)
