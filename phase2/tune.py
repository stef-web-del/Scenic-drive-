"""Sensitivität: Referenz-Perzentile (längengewichtet, ohne Wohnstraßen) für Gewichtsvarianten, ohne Neuaufbau."""
import json, numpy as np, geopandas as gpd, config, scoring
E=gpd.read_file('out/edges.gpkg'); R=json.load(open('out/refcheck.json'))
m=~E.highway.isin(['residential','living_street']).values; ln=E.len_m.values
V={'basis':{},'dorf_milder':dict(pen_village=0.08),'kurve+':dict(curv=0.45),'relief+':dict(relief=0.3),'dorf_milder+kurve+':dict(pen_village=0.08,curv=0.45)}
base=dict(config.W)
for k,v in V.items():
    config.W.clear(); config.W.update(base); config.W.update(v); s=scoring.score(E.copy()).score.values
    out=[]
    for n,r in R.items():
        e=r['eids']; x=(s[e]*ln[e]).sum()/ln[e].sum(); out.append(round((ln[m]*(s[m]<x)).sum()/ln[m].sum()*100))
    print(f'{k:22s}',out,'Mittel',np.mean(out))
