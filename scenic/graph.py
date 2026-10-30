"""Gerichteter Straßengraph (Einbahnstraßen, Kreisverkehre) als CSR-Matrix für schnelle Dijkstra-Bäume."""
import numpy as np, scipy.sparse as sp
from .scoring import is_b, is_town
class RoadGraph:
    def __init__(self,E,p):
        self.E=E; self.p=p
        nodes={}; 
        for n in list(E.u)+list(E.v): nodes.setdefault(n,len(nodes))
        self.nodes=list(nodes); self.nid=nodes
        u=np.array([nodes[x] for x in E.u]); v=np.array([nodes[x] for x in E.v])
        ow=E.oneway.fillna('').astype(str).values; jn=E.junction.fillna('').astype(str).values
        fwd=~np.isin(ow,['-1','reverse']); bwd=~(np.isin(ow,['yes','true','1','-1','reverse'])|np.isin(jn,['roundabout','circular']))
        bwd|=np.isin(ow,['-1','reverse'])
        self.time=E.time_s.values/p.speed_factor; self.len=E.len_m.values; self.score=E.score.values
        self.isb=is_b(E); self.town=is_town(E)
        mult=np.where(self.isb,p.b_cost_mult,1.0)
        if p.avoid_eids: mult[list(p.avoid_eids)]*=p.avoid_mult
        if not p.allow_narrow:
            import pandas as pd
            nar=(E.lanes.astype(str).eq('1')|(pd.to_numeric(E.width,errors='coerce')<4.5)).values; mult[nar]*=50
        cost=self.time*(p.cost_base+p.cost_bad*(1-self.score))*mult
        a=np.r_[u[fwd],v[bwd]]; b=np.r_[v[fwd],u[bwd]]; eid=np.r_[np.where(fwd)[0],np.where(bwd)[0]]
        c=cost[eid]
        # Parallelkanten: günstigste behalten
        o=np.lexsort((c,b,a)); a,b,eid,c=a[o],b[o],eid[o],c[o]
        keep=np.r_[True,(a[1:]!=a[:-1])|(b[1:]!=b[:-1])]; a,b,eid,c=a[keep],b[keep],eid[keep],c[keep]
        n=len(nodes); self.n=n
        self.keys=a.astype(np.int64)*n+b; self.keid=eid   # sortiert (lexsort a,b)
        self.C=sp.csr_matrix((c+1e-6,(a,b)),shape=(n,n))
        self.EID=sp.csr_matrix((eid+1,(a,b)),shape=(n,n))   # +1 wegen 0=leer
        self.directed_edges=len(a); self.oneway_edges=int((~(fwd&bwd)).sum())
    def edge_id(self,a,b):
        return int(self.edge_ids(np.array([a]),np.array([b]))[0])
    def edge_ids(self,a,b):
        k=a.astype(np.int64)*self.n+b; i=np.searchsorted(self.keys,k); i=np.clip(i,0,len(self.keys)-1)
        return np.where(self.keys[i]==k,self.keid[i],-1)
    def nearest(self,lat,lon,tf):
        import numpy as np
        if not hasattr(self,'_xy'):
            import ast
            xy=np.array([ast.literal_eval(x) if isinstance(x,str) else x for x in self.nodes]); self._xy=np.column_stack(tf.transform(xy[:,0],xy[:,1]))
        if not hasattr(self,'_main'):   # nur Knoten im großen, stark zusammenhängenden Netz (keine abgeschnittenen Wirtschaftswege)
            from scipy.sparse.csgraph import connected_components
            _,lab=connected_components(self.C,directed=True,connection='strong'); cnt=np.bincount(lab); self._main=cnt[lab]>=1000
        x,y=tf.transform(lon,lat); d=((self._xy-[x,y])**2).sum(1); d[~self._main]=np.inf; return int(np.argmin(d))
