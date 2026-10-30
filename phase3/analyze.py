"""Auswertung B + Exporte. Liest cache/routes_all.pkl (enumerate.py)."""
import sys,time,pickle,json,itertools,numpy as np,pandas as pd
sys.path.insert(0,'/workspace/scenic')
import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
from scenic import api,profiles,character,export
from scenic.search import cluster,Searcher
from scenic.settings import START,CACHE,OUTDIR
from pyproj import Transformer
p=profiles.get(); E,RG=api.prepare(p)
D=pickle.load(open(f'{CACHE}/routes_all.pkl','rb')); R=D['routes']
tf=Transformer.from_crs(4326,25832,always_xy=True); sx,sy=tf.transform(START[1],START[0])
mid=E.geometry.to_crs(25832).interpolate(0.5,normalized=True); MX,MY=mid.x.values,mid.y.values
asc_e=E.relief_m_km.values*E.len_m.values/1000/2; low=(E.z_mean.values<160)
def region(b):
    if b<100 or b>=345: return 'Spessart (N/NO)'
    if b<190: return 'Bauland/Madonnenländchen (SO/S)'
    return 'Odenwald (SW/W/NW)'
rows=[]
for i,r in enumerate(R):
    e=np.array(r['eids']); L=RG.len[e]; cx=(MX[e]*L).sum()/L.sum(); cy=(MY[e]*L).sum()/L.sum()
    b=(np.degrees(np.arctan2(cx-sx,cy-sy))+360)%360
    rows.append(dict(i=i,**{k:r[k] for k in ('len_km','time_min','score','overlap','b_share','town_share')},
        ascent_est=asc_e[e].sum(),maintal=(L*low[e]).sum()/L.sum(),bearing=b,radius_km=np.hypot(cx-sx,cy-sy)/1000,n_wp=len(r['anchors'])))
d=pd.DataFrame(rows); d['region']=d.bearing.map(region)
d.loc[d.maintal>0.35,'region']='Maintal (>35 % Tallage)'
def ok(d,p): return d[(d.time_min.between(p.min_min,p.max_min))&(d.overlap<=p.max_overlap)&(d.b_share<=p.max_b_share)&(d.town_share<=p.max_town_share)&(d.score>=p.min_score)]
V=ok(d,p); print('Kandidaten',len(d),'im Zeitfenster',d.time_min.between(45,60).sum(),'gültig',len(V))
Vr=[R[i] for i in V.i]
lab50,rep50=cluster(RG,Vr,0.5); lab25,rep25=cluster(RG,Vr,0.25)
V=V.assign(cl50=lab50,cl25=lab25)
res=dict(n_candidates=len(d),n_window=int(d.time_min.between(45,60).sum()),n_valid=len(V),n_cl50=len(rep50),n_cl25=len(rep25),timing=D['timing'])
# Qualitätsstufen
bins=[0.15,0.18,0.21,0.24,1]; labs=['C 0,15–0,18','B 0,18–0,21','A 0,21–0,24','A+ ≥0,24']
V['klasse']=pd.cut(V.score,bins,labels=labs,right=False)
res['klassen']={str(k):dict(runden=int((V.klasse==k).sum()),cluster25=int(V[V.klasse==k].cl25.nunique())) for k in labs}
res['regionen']={k:dict(runden=int(v),cluster25=int(V[V.region==k].cl25.nunique())) for k,v in V.region.value_counts().items()}
res['verteilung']={c:{q:round(float(V[c].quantile(x)),3) for q,x in (('p10',.1),('p50',.5),('p90',.9))} for c in ['len_km','time_min','score','ascent_est','town_share','b_share','overlap','radius_km']}
# Sensitivität
sens=[]
for ov,b,t,ms in itertools.product([0.05,0.08,0.15],[0.05,0.12,0.25],[0.2,0.25,0.3],[0.12,0.15,0.18]):
    q=profiles.get(max_overlap=ov,max_b_share=b,max_town_share=t,min_score=ms); n=len(ok(d,q)); sens.append(dict(overlap=ov,b=b,town=t,score=ms,n=n))
S=pd.DataFrame(sens); res['sens']=S.to_dict('records')
for k,v in dict(dauer_40_65=profiles.get(min_min=40,max_min=65),dauer_50_60=profiles.get(min_min=50,max_min=60)).items(): res[k]=len(ok(d,v))
# Cluster-Anzahl Sensitivität (Jaccard-Schwelle)
# Kern-Jaccard: ohne die ersten/letzten 6 km (Ausfahrt aus Miltenberg ist zwangsläufig gemeinsam)
def core(r):
    e=np.array(r['eids']); c=np.cumsum(RG.len[e]); return dict(r,eids=list(e[(c>6000)&(c<c[-1]-6000)]))
Vc=[core(r) for r in Vr]
res['cluster_core']={str(t):len(cluster(RG,Vc,t)[1]) for t in (0.25,0.5)}
# Korridor-Heatmap: wie oft wird jede Kante von gültigen Runden genutzt
use=np.zeros(len(E)); 
for r in Vr: use[np.unique(r['eids'])]+=1
G=E.assign(use=use/len(Vr)).to_crs(25832); G=G[G.use>0.02]
f,a=plt.subplots(figsize=(11,11)); E.to_crs(25832).plot(ax=a,color='#e5e5e5',lw=.3)
G.plot(ax=a,column='use',cmap='inferno_r',lw=0.5+G.use*6,legend=True,legend_kwds={'shrink':.5,'label':'Anteil der gültigen Runden'})
a.set_xlim(sx-20000,sx+20000); a.set_ylim(sy-20000,sy+20000); a.plot(sx,sy,'c*',ms=16); a.set_axis_off(); a.set_title('Korridore: Nutzung je Straße durch die 45–60-min-Runden')
f.savefig(f'{OUTDIR}/korridor_heatmap.png',dpi=100,bbox_inches='tight')
res['cluster_thr']={str(t):len(cluster(RG,Vr,t)[1]) for t in (0.15,0.25,0.35,0.5,0.7)}
# Diagramme
f,ax=plt.subplots(2,3,figsize=(15,8))
for a,(c,l) in zip(ax.ravel(),[('time_min','Fahrzeit [min]'),('len_km','Länge [km]'),('ascent_est','Höhenmeter (Schätzung) [m]'),('town_share','Anteil Ortsdurchfahrt'),('b_share','Anteil B-Straße'),('score','Ø Score')]):
    a.hist(V[c],bins=30,color='#c0392b',alpha=.8); a.set_title(l)
f.suptitle(f'Verteilung der {len(V)} gültigen Runden ab OBI Miltenberg (45–60 min)'); f.tight_layout(); f.savefig(f'{OUTDIR}/verteilungen.png',dpi=100)
# Polar: Richtung
f=plt.figure(figsize=(6,6)); a=f.add_subplot(projection='polar'); a.set_theta_zero_location('N'); a.set_theta_direction(-1)
a.hist(np.radians(V.bearing),bins=24,color='#2c7fb8'); a.set_title('Hauptrichtung der gültigen Runden'); f.savefig(f'{OUTDIR}/richtungen.png',dpi=100)
# Charakterisierung Cluster-Repräsentanten (25 %) – beste 25 nach Score
t=time.time(); reps=sorted([Vr[j] for j in rep25],key=lambda r:-r['score'])[:25]
for r in reps: r['char']=character.analyze(RG,E,r)
res['char_time_s_per_route']=(time.time()-t)/len(reps)
reps.sort(key=lambda r:-r['char']['tour_score']); top=reps[:6]
# Valhalla
from scenic import valhalla_check
t=time.time(); vh=valhalla_check.check([r['char']['coords'] for r in reps]); res['valhalla_s']=time.time()-t
for r,v in zip(reps,vh): r['valhalla']=v; r['valhalla_min']=v.get('trace_s',np.nan)/60
for i,r in enumerate(top): export.route_files(r,i,OUTDIR)
export.overview(E,top,START,OUTDIR)
res['top']=[dict(rank=i+1,len_km=r['len_km'],time_min=r['time_min'],valhalla_min=r['valhalla_min'],valhalla_route_min=r['valhalla'].get('route_s',np.nan)/60,
    score=r['score'],tour_score=r['char']['tour_score'],b_share=r['b_share'],town_share=r['town_share'],overlap=r['overlap'],
    **{k:r['char'][k] for k in ('ascent_m','highlight_share','transfer_share','topk','dramaturgy','interrupts_per10km','signals','view_frac','view_far_m','uphill_curvy','tags','text','orte','pauses')},
    highlights=[{k:h[k] for k in ('name','km0','len_m','score','grade')} for h in r['char']['highlights']]) for i,r in enumerate(top)]
res['calib']=[dict(own=r['time_min'],vh=r['valhalla_min'],vh_route=r['valhalla'].get('route_s',np.nan)/60,len=r['len_km'],vh_km=r['valhalla'].get('trace_km')) for r in reps]
json.dump(res,open(f'{OUTDIR}/auswertung.json','w'),ensure_ascii=False,indent=1,default=float)
V.to_csv(f'{OUTDIR}/gueltige_runden.csv',index=False)
print(json.dumps({k:v for k,v in res.items() if k not in('sens','top','calib')},ensure_ascii=False,default=float,indent=0))
