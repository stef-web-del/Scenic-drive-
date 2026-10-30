"""Kanten- und Abschnitts-Score (profilabhängig, schnell, ohne Neuaufbau der Merkmale)."""
import numpy as np, pandas as pd, networkx as nx
def sat(x,a): return 1-np.exp(-np.asarray(x,float)/a)
def is_b(E):
    ref=E['ref'].fillna('').astype(str)
    return (E.highway.isin(['primary','primary_link','trunk','trunk_link'])|ref.str.match(r'^B\s?\d')).values
def is_town(E):
    """Ortsdurchfahrt: Gebäudedichte >=40/250m-Zelle (Ortskern), Stadtnähe <1,2 km oder Wohnstraße"""
    return ((E.bldg_250>=40)|(E.d_town<1200)|E.highway.isin(['residential','living_street'])).values
def curv_component(E,p):
    flow=np.clip(E.curv_deg_km-0.5*E.tight_deg_km,0,None)           # flüssige Anteile
    c=flow+p.hairpin_factor*E.tight_deg_km
    return sat(c,350)*0.6+sat(E.bends_km,6)*0.4
def edge_score(E,p):
    c=curv_component(E,p); rel=sat(E.relief_m_km,45)
    ws=p.w_curv+p.w_relief+p.w_view+p.w_edge+p.w_open+p.w_forest
    pos=(p.w_curv*c+p.w_relief*rel+p.w_view*E.viewpoint+p.w_edge*np.clip(E.forest_edge*2,0,1)+p.w_open*E.open+p.w_forest*E.forest)/ws
    narrow=(E.lanes.astype(str).eq('1')|(pd.to_numeric(E.width,errors='coerce')<4.5))
    pen=(p.pen_town*np.clip(1-E.d_town/2500,0,1)+p.pen_village*np.clip(1-E.d_village/600,0,1)+p.pen_bldg*sat(E.bldg_250,40)
         +p.pen_bstrasse*is_b(E)+(p.pen_trunk-p.pen_bstrasse)*E.highway.isin(['trunk','trunk_link'])
         +p.pen_residential*E.highway.isin(['residential','living_street'])+p.pen_track*E.highway.isin(['track','service'])
         +p.pen_minor*((E.highway=='unclassified')&E.name.isna()&E.ref.isna()&E.surface.isna())
         +p.pen_narrow*narrow+p.pen_hairpin*sat(E.tight_deg_km,600))
    E['c_curv']=c; E['c_relief']=rel; E['penalty']=pen; E['edge_score']=np.clip(pos-pen,0,1)
    return E
MAJOR={'trunk','primary','secondary','tertiary','unclassified','primary_link','secondary_link','tertiary_link','trunk_link'}
def sections(E):
    """Abschnitte = Ketten von Kanten zwischen relevanten Kreuzungen (Grad>=3 im Netz ohne Wohn/Service-Stichstraßen)
    bzw. Wechsel von Straßenname/ref. Gibt sec_id je Kante zurück."""
    G=nx.MultiGraph()
    maj=E.highway.isin(MAJOR).values
    for i,(u,v) in enumerate(zip(E.u,E.v)):
        G.add_edge(u,v,key=i,maj=maj[i])
    deg={n:sum(1 for _,_,d in G.edges(n,data=True) if d['maj']) for n in G}
    ref=E['ref'].fillna('').astype(str).values; nm=E['name'].fillna('').astype(str).values
    key=np.where(ref!='',ref,nm)
    big=E.highway.isin(['trunk','primary','secondary','tertiary']).values; tw=is_town(E)
    sec=np.full(len(E),-1); sid=0
    for i in range(len(E)):
        if sec[i]>=0: continue
        sec[i]=sid; stack=[i]
        while stack:
            j=stack.pop()
            if not maj[j]: continue
            for n in (E.u.iat[j],E.v.iat[j]):
                ks=[k for _,_,k in G.edges(n,keys=True) if k!=j and maj[k]]
                same=[k for k in ks if key[k]==key[j] and key[j]!='']
                cross=any(big[k] and key[k]!=key[j] for k in ks)
                nxt=[] if cross else (same if len(same)==1 else (ks if deg[n]==2 else []))
                nxt=[k for k in nxt if tw[k]==tw[j]]   # relevante Kreuzung: Abschnitt endet
                for k in nxt:
                    if sec[k]<0: sec[k]=sid; stack.append(k)
        sid+=1
    return sec
def section_score(E,p):
    """Rhythmus/Qualität je Abschnitt: längengewichteter Kantenscore, Kurvenanteil (Länge mit Kurven), Konstanz
    (geringe Streuung der Kurvigkeit), Längenbonus (lange zusammenhängende Abschnitte)."""
    g=E.assign(w=E.len_m, es=E.edge_score*E.len_m, cl=E.c_curv*E.len_m, curvy=(E.c_curv>0.35)*E.len_m)
    a=g.groupby('sec_id').agg(L=('w','sum'),es=('es','sum'),cl=('cl','sum'),curvy=('curvy','sum'),cstd=('c_curv','std'))
    a['mean']=a.es/a.L; a['curv_share']=a.curvy/a.L; a['rhythm']=1-np.clip(a.cstd.fillna(0)/0.4,0,1)
    a['len_bonus']=sat(a.L,3000)
    a['sec_score']=np.clip(a['mean']*(0.7+0.3*a.len_bonus)+0.15*a.curv_share*a.rhythm*a.len_bonus,0,1)
    E['sec_score']=a.sec_score.reindex(E.sec_id).values; E['sec_len']=a.L.reindex(E.sec_id).values
    E['score']=(1-p.w_section)*E.edge_score+p.w_section*E.sec_score
    return E
def score_all(E,p):
    E=edge_score(E,p)
    if 'sec_id' not in E: E['sec_id']=sections(E)
    return section_score(E,p)
