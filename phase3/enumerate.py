"""Systematische Auswertung: alle 1-/2-Wegpunkt-Kombinationen + Stichprobe 3er, mit Reparatur (Reuse-Strafe)."""
import sys,time,pickle
sys.path.insert(0,'/workspace/scenic')
from scenic import api,profiles
from scenic.settings import START,CACHE
p=profiles.get(min_min=40,max_min=65)
t=time.time(); E,RG=api.prepare(p); tp=time.time()-t
t=time.time(); E,RG,S,routes=api.evaluate_all(START,p,k_max=3,max_combos=60000,E_RG=(E,RG)); te=time.time()-t
print('prep',round(tp,1),'s  enum',round(te,1),'s  Routen',len(routes),'Anker',len(S.anchor_nodes),flush=True)
pickle.dump(dict(routes=routes,anchors=S.anchor_eids,timing=dict(prep=tp,enum=te)),open(f'{CACHE}/routes_all.pkl','wb'))
