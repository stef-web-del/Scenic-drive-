import numpy as np, pandas as pd
from config import W
def sat(x,a): return 1-np.exp(-np.asarray(x)/a)   # sättigende Normierung
def score(E):
    c=sat(E.curv_deg_km,350)*0.6+sat(E.bends_km,6)*0.4
    rel=sat(E.relief_m_km,45)
    pos=(W['curv']*c+W['relief']*rel+W['view']*E.viewpoint+W['edge']*np.clip(E.forest_edge*2,0,1)
         +W['open']*E.open+W['forest']*E.forest)
    pos=pos/sum(W[k] for k in ['curv','relief','view','edge','open','forest'])
    ref=E['ref'].fillna('').astype(str)
    pen=(W['pen_town']*np.clip(1-E.d_town/2500,0,1)+W['pen_village']*np.clip(1-E.d_village/600,0,1)
         +W['pen_bldg']*sat(E.bldg_250,40)
         +W['pen_primary']*(E.highway.isin(['primary','primary_link'])|ref.str.startswith('B'))
         +W['pen_trunk']*E.highway.isin(['trunk','trunk_link'])
         +W['pen_residential']*E.highway.isin(['residential','living_street'])
         +W['pen_track']*E.highway.isin(['track','service'])
         +W['pen_surface']*(E.surface.notna()&(E.surface!='asphalt'))
         +W['pen_minor']*((E.highway=='unclassified')&E.name.isna()&E.ref.isna()&E.surface.isna())
         +W['pen_narrow']*(E.lanes.astype(str).eq('1')|(pd.to_numeric(E.width,errors='coerce')<4.5))
         +W['pen_surface']*E.smoothness.eq('intermediate')
         +W['pen_hairpin']*sat(E.tight_deg_km,600))
    E['c_curv']=c; E['c_relief']=rel; E['penalty']=pen
    E['score']=np.clip(pos-pen,0,1)
    return E
