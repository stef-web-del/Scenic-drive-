"""Score-Aufschlüsselung aller gültigen Runden -> cache/parts.pkl (Teilwerte je Runde und je Abschnitt).
Nutzt character.analyze (identische Formeln wie Tour-Score); danach python webapp/scripts/compact.py"""
import sys,pickle,multiprocessing as mp,pandas as pd; sys.path.insert(0,'/workspace/scenic')
from scenic import api,profiles,character
from scenic.settings import OUTDIR
CACHE='/workspace/scenic/phase3/cache'
E,RG=api.prepare(profiles.get()); R=pickle.load(open(f'{CACHE}/routes_all.pkl','rb'))['routes']
V=pd.read_csv(f'{OUTDIR}/gueltige_runden.csv')
KEYS=('contrib','tour_score','route_score','topk','curv_mean','tight_mean','rhythm_mean','relief_mean','ascent_m','view_frac','view_far_m',
      'forest','open','town_share','town_entries','b_share','highlight_share','transfer_share','interrupts_per10km','signals','dramaturgy','segments')
def one(i):
    try:
        a=character.analyze(RG,E,R[i],with_view=True); o={k:a[k] for k in KEYS}; o['len_m']=float(a['profile'][0][-1]); return i,o
    except Exception as ex: return i,{'err':repr(ex)}
if __name__=='__main__':
    n=int(sys.argv[1]) if len(sys.argv)>1 else None; ids=V.i.tolist()[:n]
    with mp.get_context('fork').Pool(3) as pool: out=dict(pool.imap_unordered(one,ids,chunksize=4))
    pickle.dump(out,open(f'{CACHE}/parts.pkl' if n is None else '/tmp/parts_test.pkl','wb')); print('fertig',len(out),sum('err' in v for v in out.values()))
