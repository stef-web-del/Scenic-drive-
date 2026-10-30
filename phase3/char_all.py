"""Charakterisiert alle gültigen Runden (Tags, Text, Pausen, Geometrie) -> cache/char_all.pkl"""
import sys,pickle,multiprocessing as mp,pandas as pd; sys.path.insert(0,'/workspace/scenic')
from scenic import api,profiles,character
from scenic.settings import OUTDIR
CACHE='/workspace/scenic/phase3/cache'
E,RG=api.prepare(profiles.get()); R=pickle.load(open(f'{CACHE}/routes_all.pkl','rb'))['routes']
V=pd.read_csv(f'{OUTDIR}/gueltige_runden.csv')
def one(i):
    try:
        a=character.analyze(RG,E,R[i],with_view=True)
        return i,{k:a.get(k) for k in ('coords','tags','text','ascent_m','orte','pauses','tour_score','highlights')}
    except Exception as ex: return i,{'err':str(ex)}
if __name__=='__main__':
    with mp.get_context('fork').Pool(6) as pool:
        out=dict(pool.imap_unordered(one,V.i.tolist(),chunksize=8))
    pickle.dump(out,open(f'{CACHE}/char_all.pkl','wb')); print('fertig',len(out),sum('err' in v for v in out.values()))
