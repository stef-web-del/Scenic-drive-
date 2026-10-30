"""Systematische Rundensuche: Dijkstra-Bäume ab Start/zu Start und ab jedem Wegpunkt (scipy), dann
Kombination Start->A->B(->C)->Start ohne erneutes Routing; Filter nach Kriterien; Clusterung."""
import numpy as np, itertools
from scipy.sparse.csgraph import dijkstra
from pyproj import Transformer
TF=Transformer.from_crs(4326,25832,always_xy=True)
def midxy(RG):
    """Kantenmitten (EPSG:25832) einmal je Graph berechnen und merken (vorher bei jedem Start neu, ~1,5 s)."""
    if not hasattr(RG,'_midxy'):
        m=RG.E.geometry.to_crs(25832).interpolate(0.5,normalized=True); RG._midxy=(m.x.values,m.y.values)
    return RG._midxy
class Searcher:
    def __init__(self,RG,start):
        self.RG=RG; p=RG.p; E=RG.E
        self.s=RG.nearest(start[0],start[1],TF)
        mx,my=midxy(RG); sx,sy=RG._xy[self.s]
        d=np.hypot(mx-sx,my-sy)
        cand=np.where((d>p.anchor_min_m)&(d<p.anchor_max_m)&(E.len_m.values>250)&(~RG.isb)&(~RG.town))[0]
        cand=cand[np.argsort(-E.score.values[cand])]
        sel=[]
        for i in cand:
            if all((mx[i]-mx[j])**2+(my[i]-my[j])**2>p.anchor_spacing_m**2 for j in sel): sel.append(i)
            if len(sel)>=p.n_anchors: break
        self.anchor_eids=np.array(sel); self.anchor_nodes=np.array([RG.nid[E.u.iat[i]] for i in sel])
        self.ang=np.arctan2(my[sel]-sy,mx[sel]-sx)
        src=np.r_[self.s,self.anchor_nodes]
        self.D,self.P=dijkstra(RG.C,directed=True,indices=src,return_predecessors=True)
        self.Dr,self.Pr=dijkstra(RG.C.T.tocsr(),directed=True,indices=[self.s],return_predecessors=True)  # zu Start
        # Zeit/Länge entlang der Bäume
        self.T=np.array([self._accum(P,D,RG.time) for P,D in zip(self.P,self.D)])
        self.Tr=self._accum(self.Pr[0],self.Dr[0],RG.time,rev=True)
    def _accum(self,P,D,w,rev=False):
        """Summe w entlang des Dijkstra-Baums bis zur Wurzel (Pointer-Jumping, vektorisiert)"""
        RG=self.RG; n=np.arange(len(P)); ok=P>=0
        e=np.full(len(P),-1); e[ok]=RG.edge_ids(n[ok],P[ok]) if rev else RG.edge_ids(P[ok],n[ok])
        val=np.where(e>=0,w[np.maximum(e,0)],0.0); a=np.where(ok,P,n)
        while np.any(a!=a[a]):
            val=val+val[a]; a=a[a]
        return np.where(np.isinf(D),np.inf,val)
    def _pe(self,i):
        """Vorgänger-Kanten-ID je Knoten für Baum i (einmalig vektorisiert, -1 = keine)."""
        if not hasattr(self,'_PE'): self._PE={}
        if i not in self._PE:
            P=self.Pr[0] if i=='home' else self.P[i]; n=np.arange(len(P)); ok=P>=0; e=np.full(len(P),-1)
            e[ok]=self.RG.edge_ids(n[ok],P[ok]) if i=='home' else self.RG.edge_ids(P[ok],n[ok]); self._PE[i]=e
        return self._PE[i]
    def leg(self,i,target):
        """Kanten von Quelle i (0=Start, k=Anker k-1) zu Knoten target"""
        P=self.P[i]; E=self._pe(i); out=[]; n=target
        while P[n]>=0: out.append(E[n]); n=P[n]
        return out[::-1]
    def leg_home(self,src):
        P=self.Pr[0]; E=self._pe('home'); out=[]; n=src
        while P[n]>=0: out.append(E[n]); n=P[n]
        return out
    def candidates(self,k_max=3,max_combos=None,seed=0):
        p=self.RG.p; A=len(self.anchor_nodes); lo,hi=p.min_min*60*0.8,p.max_min*60*1.02; out=[]  # Baum-Schätzung; Reparatur verlängert
        rng=np.random.default_rng(seed)
        for k in range(1,k_max+1):
            combos=list(itertools.combinations(range(A),k))
            if max_combos and len(combos)>max_combos: combos=[combos[i] for i in rng.choice(len(combos),max_combos,replace=False)]
            for c in combos:
                c=sorted(c,key=lambda j:self.ang[j])
                if k>1:
                    span=np.degrees(abs((self.ang[c[-1]]-self.ang[c[0]]+np.pi)%(2*np.pi)-np.pi))
                    if span<30: continue
                nodes=[self.s]+[self.anchor_nodes[j] for j in c]
                t=sum(self.T[0 if a==0 else c[a-1]+1][nodes[a+1]] for a in range(k))+self.Tr[nodes[-1]]
                if not(lo<=t<=hi): continue
                for order in ([c,c[::-1]] if k>1 else [c]):   # beide Umlaufrichtungen
                    nodes=[self.s]+[self.anchor_nodes[j] for j in order]
                    t=sum(self.T[0 if a==0 else order[a-1]+1][nodes[a+1]] for a in range(k))+self.Tr[nodes[-1]]
                    if lo<=t<=hi: out.append(tuple(order))
        return out
    def build(self,order):
        nodes=[self.s]+[self.anchor_nodes[j] for j in order]; e=[]
        for a in range(len(order)): e+=self.leg(0 if a==0 else order[a-1]+1,nodes[a+1])
        e+=self.leg_home(nodes[-1]); return e
    def _zone(self):
        if not hasattr(self,'_Z'):
            RG=self.RG; mx,my=midxy(RG); sx,sy=RG._xy[self.s]
            self._Z=np.hypot(mx-sx,my-sy)<=RG.p.start_zone_m
        return self._Z
    def _anchor_dir(self,j):
        """Ankerkante als gerichtete Durchfahrt (ein,aus,eid): Wegpunkt wird durchfahren statt angefahren und gewendet."""
        RG=self.RG; e=int(self.anchor_eids[j]); a=RG.nid[RG.E.u.iat[e]]; b=RG.nid[RG.E.v.iat[e]]
        return (a,b,e) if RG.edge_id(a,b)==e else (b,a,e)
    def repair(self,order,reuse=8.0):
        """Neu-Routing der Beine nacheinander. no_backtrack (Default): bereits befahrene Kanten (beide Richtungen) sind
        außerhalb der Startzone gesperrt, jeder Wegpunkt wird als Kante durchfahren -> keine Stichfahrt, keine Wende.
        Sonst (Altverhalten): Kostenaufschlag reuse für befahrene Kanten. Rückgabe None, wenn keine Runde ohne Stich existiert."""
        RG=self.RG; p=RG.p; C=RG.C.copy(); base=C.data.copy(); used=np.zeros(len(RG.len),bool); hard=p.no_backtrack
        zone=self._zone() if hard else None
        if hard:
            legs=[('A',j) for j in order]+[('H',None)]
        else:
            nodes=[self.s]+[self.anchor_nodes[j] for j in order]+[self.s]; legs=[(a,b,None) for a,b in zip(nodes[:-1],nodes[1:])]
        out=[]; lim=self.D[0][self.s]+1
        ref=max(max(self.D[0][self.anchor_nodes[j]] for j in order),1.0)
        cur=self.s
        for leg in legs:
            if hard:
                ae=None
                if leg[0]=='H': a,b=cur,self.s
            else: a,b,ae=leg
            if hard: C.data=base*np.where(used[RG.keid]&~zone[RG.keid],1e7,1.0)
            else: C.data=base*np.where(used[RG.keid],reuse,1.0)
            if hard: a=cur
            D,P=dijkstra(C,directed=True,indices=a,return_predecessors=True,limit=ref*p.leg_limit_mult+1)
            if hard and leg[0]=='A':
                # Einfahrt in die Ankerkante von der Seite, die zuerst erreicht wird (beide Richtungen, wenn erlaubt)
                ae=int(self.anchor_eids[leg[1]]); x=RG.nid[RG.E.u.iat[ae]]; y=RG.nid[RG.E.v.iat[ae]]
                opts=[(D[i],i,o) for i,o in ((x,y),(y,x)) if RG.edge_id(i,o)==ae]
                if not opts: return None
                _,b,nxt=min(opts)
            if np.isinf(D[b]) or D[b]>=1e6: return None
            path=[b]
            while P[path[-1]]>=0: path.append(P[path[-1]])
            path=np.array(path[::-1]); e=list(RG.edge_ids(path[:-1],path[1:]))
            if ae is not None:
                if hard and (used[ae] and not zone[ae] or ae in e): return None
                e.append(ae)
            used[e]=True; out+=e
            if hard: cur=nxt if leg[0]=='A' else b
        if hard and backtrack(RG,out,zone)['bad']: return None
        return out
def backtrack(RG,eids,zone=None):
    """Prüft eine Runde auf Stichfahrten: Kante mehrfach (egal welche Richtung) außerhalb der Startzone, Wenden
    (gleiche Kante direkt nacheinander) und zu langes gemeinsames Startstück. zone: bool je Kante (None -> aus Params)."""
    e=np.asarray(eids)
    if zone is None:
        from pyproj import Transformer
        from .settings import START
        tf=Transformer.from_crs(4326,25832,always_xy=True); sx,sy=tf.transform(START[1],START[0])
        if not hasattr(RG,'_mid'):
            m=RG.E.geometry.to_crs(25832).interpolate(0.5,normalized=True); RG._mid=(m.x.values,m.y.values)
        zone=np.hypot(RG._mid[0]-sx,RG._mid[1]-sy)<=RG.p.start_zone_m
    u,c=np.unique(e,return_counts=True); rep=u[c>1]
    out_rep=rep[~zone[rep]]; in_rep=rep[zone[rep]]
    uturn=int(np.sum(e[1:]==e[:-1]))
    shared=float((RG.len[in_rep]*(c[c>1][zone[rep]]-1)).sum())
    rep_m=float(RG.len[out_rep].sum())
    return dict(bad=bool(len(out_rep) or uturn or shared>RG.p.max_start_shared_m),repeat_m=rep_m,uturns=uturn,start_shared_m=shared)
def metrics(RG,eids,E=None):
    e=np.array(eids); L=RG.len[e]; Lt=L.sum()
    u,cnt=np.unique(e,return_counts=True); rep=(RG.len[u]*(cnt-1)).sum()/Lt
    m=dict(len_km=Lt/1000,time_min=RG.time[e].sum()/60,score=(RG.score[e]*L).sum()/Lt,overlap=rep,
           b_share=(L*RG.isb[e]).sum()/Lt,town_share=(L*RG.town[e]).sum()/Lt)
    return m
def jaccard_len(RG,a,b):
    a,b=set(a),set(b); i=sum(RG.len[x] for x in a&b); return i/max(1,sum(RG.len[x] for x in a|b))
def cluster(RG,routes,thr):
    """Greedy (nach Score absteigend): Runde gehört zu Cluster, wenn längengew. Jaccard >= thr mit Repräsentant."""
    reps=[]; lab=[]
    sets=[set(r['eids']) for r in routes]; lens=[sum(RG.len[x] for x in s) for s in sets]
    for i in np.argsort([-r['score'] for r in routes]):
        for ci,j in enumerate(reps):
            inter=sum(RG.len[x] for x in sets[i]&sets[j])
            if inter/(lens[i]+lens[j]-inter)>=thr: lab.append((i,ci)); break
        else: reps.append(i); lab.append((i,len(reps)-1))
    L=np.zeros(len(routes),int)
    for i,c in lab: L[i]=c
    return L,reps
