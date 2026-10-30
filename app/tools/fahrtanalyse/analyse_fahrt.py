#!/usr/bin/env python3
"""Analyse eines Scenic-Routes-Debug-Exports (fahrt.csv). Nutzung: analyse_fahrt.py <dir> [loops.json]
Gibt Kennzahlen als JSON aus und schreibt PNG-Diagramme ins Verzeichnis. Enthält keine Daten."""
import sys, json, math
import numpy as np, pandas as pd
import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
D=sys.argv[1]; LOOPS=sys.argv[2] if len(sys.argv)>2 else None
d=pd.read_csv(f'{D}/fahrt.csv',parse_dates=['time_utc'])
t=(d.time_utc-d.time_utc.iloc[0]).dt.total_seconds().values
lat,lon=np.radians(d.lat.values),np.radians(d.lon.values)
def hav(a1,o1,a2,o2):
    return 2*6371000*np.arcsin(np.sqrt(np.sin((a2-a1)/2)**2+np.cos(a1)*np.cos(a2)*np.sin((o2-o1)/2)**2))
ds=np.r_[0,hav(lat[:-1],lon[:-1],lat[1:],lon[1:])]; dt=np.r_[0,np.diff(t)]
v=d.speed_kmh.values; R={}
mov=v>=5
R['dauer_min']=t[-1]/60; R['strecke_km']=ds[mov].sum()/1000; R['strecke_gesamt_km']=ds.sum()/1000
R['fahrzeit_min']=dt[mov].sum()/60; R['avg_kmh']=R['strecke_km']/(R['fahrzeit_min']/60)
# vmax 0.6.6-Logik: med3 Doppler, Deckel max(med3 pos, dist/zeit über 3 Fixes)*1.25+2m/s
vs=v/3.6; vp=np.where(dt>0,ds/np.maximum(dt,1e-3),0)
med=lambda a:pd.Series(a).rolling(3,center=True).median().values
d3=pd.Series(ds).rolling(3).sum().values/pd.Series(dt).rolling(3).sum().values
cap=np.nanmax(np.c_[med(vp),d3],axis=1)*1.25+2
vm=np.fmin(med(vs),cap); R['vmax_kmh']=np.nanmax(vm)*3.6; R['vmax_roh_kmh']=v.max()
R['speed_verteilung_pct']={f'{a}-{b}':float(((v>=a)&(v<b))[mov].mean()*100) for a,b in [(5,30),(30,50),(50,70),(70,90),(90,200)]}
R['v_p50_p85_p95']=np.percentile(v[mov],[50,85,95]).round(1).tolist()
lg,qg=d.lon_g.values,d.lat_g.values; aq=np.abs(qg)
def pc(a):a=a[~np.isnan(a)];return np.percentile(a,[50,95,99]).round(3).tolist()
R['lat_g_abs_p50_95_99']=pc(aq); R['lon_g_p50_95_99_brems']=pc(-lg[lg<0]); R['lon_g_p50_95_99_beschl']=pc(lg[lg>0])
sm=lambda a:float(np.nanmax(np.fmin(a[:-1],a[1:])))
R['lat_g_max_gehalten']=sm(aq); R['brems_g_max_gehalten']=sm(np.nan_to_num(-lg)); R['beschl_g_max_gehalten']=sm(np.nan_to_num(lg))
R['lat_g_max_roh']=float(aq.max()); R['gpeak_max']=float(d.g_peak.max())
# GPS-Plausibilität Längs-g
dv=np.gradient(pd.Series(vs).rolling(3,center=True).mean().bfill().ffill().values,t)/9.81
R['lon_g_vs_gps_corr']=float(pd.Series(lg).corr(pd.Series(dv)))
# Kurven: |yaw|>5°/s
ka=np.abs(d.yaw_dps.values)>5; R['kurvenanteil_pct']=float(ka[mov].mean()*100)
bins=[(5,40),(40,60),(60,80),(80,200)]
R['kurve_latg_nach_speed']={f'{a}-{b}':(np.percentile(aq[ka&(v>=a)&(v<b)],[50,95]).round(3).tolist() if (ka&(v>=a)&(v<b)).sum()>10 else None) for a,b in bins}
R['kurve_v_p50']=float(np.median(v[ka&mov])); R['gerade_v_p50']=float(np.median(v[~ka&mov]))
# Kurven-Ereignisse: Bremsen davor / in / Beschleunigen danach
ev=[];i=0;n=len(v)
while i<n:
    if ka[i] and v[i]>15:
        j=i
        while j<n and ka[j]: j+=1
        if t[j-1]-t[i]>=2:
            pre=(t>=t[i]-6)&(t<t[i]); post=(t>=t[j-1])&(t<t[j-1]+6); inn=slice(i,j)
            ev.append(dict(v_ein=v[max(i-6,0)],v_min=v[inn].min(),latmax=aq[inn].max(),
                bremse_vor=np.nanmin(np.r_[lg[pre],0]),bremse_in=np.nanmin(np.r_[lg[inn],0]),beschl_nach=np.nanmax(np.r_[lg[post],0])))
        i=j
    else: i+=1
E=pd.DataFrame(ev); R['kurven_n']=len(E)
if len(E):
    R['kurven_bremst_vorher_pct']=float((E.bremse_vor<-0.05).mean()*100)
    R['kurven_bremst_in_kurve_pct']=float((E.bremse_in<-0.08).mean()*100)
    R['kurven_beschl_raus_pct']=float((E.beschl_nach>0.05).mean()*100)
    R['kurven_v_abbau_kmh_p50']=float((E.v_ein-E.v_min).median()); R['kurven_latmax_p50_p90']=E.latmax.quantile([.5,.9]).round(3).tolist()
# Höhe (geglättet)
alt=pd.Series(d.alt_m.values).rolling(15,center=True,min_periods=1).median().values
da=np.diff(alt); R['hm_auf']=float(da[da>0].sum()); R['hm_ab']=float(-da[da<0].sum()); R['alt_min_max']=[float(alt.min()),float(alt.max())]
gr=np.r_[0,np.where(ds[1:]>3,da/np.maximum(ds[1:],1)*100,np.nan)]; gr=pd.Series(gr).rolling(20,center=True,min_periods=5).mean().values
for nm,m in [('bergauf',gr>4),('bergab',gr<-4),('eben',np.abs(gr)<=4)]:
    m=m&mov; R[nm]=dict(anteil_pct=float(m.mean()*100/mov.mean()),v_p50=float(np.median(v[m])) if m.any() else None,
        brems_p95=float(np.percentile(-lg[m&(lg<0)],95)) if (m&(lg<0)).sum()>10 else None, lat_p95=float(np.percentile(aq[m],95)) if m.any() else None)
R['fix_quote_1hz_pct']=float(len(t)/(t[-1]+1)*100); R['luecken_gt3s']=int((dt>3).sum()); R['acc_p50_p90_p99']=np.percentile(d.accuracy_m,[50,90,99]).round(1).tolist()
# Schleifen-Abgleich
best=None
if LOOPS:
    L=json.load(open(LOOPS))['loops']
    from math import cos
    def near(g):
        g=np.array(g); gl,ga=np.radians(g[:,0]),np.radians(g[:,1])
        # Track->Loop Abdeckung und Loop->Track
        def mind(A1,O1,A2,O2):
            out=np.empty(len(A1))
            for k in range(len(A1)): out[k]=hav(A1[k],O1[k],A2,O2).min()
            return out
        s=slice(None,None,5)
        tl=mind(lat[s],lon[s],ga,gl); lt=mind(ga,gl,lat[s],lon[s])
        return float((tl<60).mean()),float((lt<60).mean())
    cand=[]
    for lp in L:
        g=np.array(lp['geom'])
        if np.abs(g[:,1].mean()-d.lat.mean())>0.15 or np.abs(g[:,0].mean()-d.lon.mean())>0.2: continue
        a,b=near(lp['geom']); cand.append((a*b,a,b,lp['id'],lp['title'],lp['km'],lp['min']))
    cand.sort(reverse=True); R['loop_top3']=cand[:3]; best=[lp for lp in L if lp['id']==cand[0][3]][0] if cand else None
json.dump(R,open(f'{D}/kennzahlen.json','w'),indent=1,default=float); print(json.dumps(R,indent=1,default=float))
# Plots
tm=t/60
fig,ax=plt.subplots(3,1,figsize=(13,9),sharex=True)
ax[0].plot(tm,v,lw=.6,color='0.6',label='Doppler roh');ax[0].plot(tm,vm*3.6,lw=.8,label='vmax-Logik 0.6.6');ax[0].set_ylabel('km/h');ax[0].legend();ax[0].grid(alpha=.3)
ax[1].plot(tm,d.alt_m,lw=.5,color='0.6');ax[1].plot(tm,alt,lw=1);ax[1].set_ylabel('Höhe m');ax[1].grid(alpha=.3)
ax[2].plot(tm,qg,lw=.5,label='quer g');ax[2].plot(tm,lg,lw=.5,label='längs g');ax[2].axhline(0,c='k',lw=.5);ax[2].set_ylabel('g');ax[2].set_xlabel('min');ax[2].legend();ax[2].grid(alpha=.3)
plt.tight_layout();plt.savefig(f'{D}/zeitverlauf.png',dpi=110);plt.close()
plt.figure(figsize=(8,6));plt.scatter(v[mov],aq[mov],s=3,alpha=.4,c=ka[mov],cmap='coolwarm');plt.xlabel('km/h');plt.ylabel('|quer g|');plt.title('Quer-g vs. Tempo (rot = Kurve)');plt.grid(alpha=.3);plt.savefig(f'{D}/querg_vs_tempo.png',dpi=110);plt.close()
plt.figure(figsize=(7,7));plt.scatter(qg,lg,s=3,alpha=.4);plt.gca().set_aspect('equal');plt.xlabel('quer g');plt.ylabel('längs g');plt.title('g-g-Diagramm');plt.grid(alpha=.3);plt.savefig(f'{D}/gg_diagramm.png',dpi=110);plt.close()
plt.figure(figsize=(10,9))
if best: g=np.array(best['geom']);plt.plot(g[:,0],g[:,1],c='0.8',lw=6,zorder=1,label=f"Schleife {best['id']}")
sc=plt.scatter(d.lon,d.lat,c=v,s=3,cmap='viridis',zorder=2);plt.colorbar(sc,label='km/h')
top=np.argsort(-aq)[:0]
for idx,lab,c in [(np.nanargmax(np.fmin(aq[:-1],aq[1:])),'max quer g','r'),(np.nanargmax(np.nan_to_num(-lg)),'max Bremsung','m'),(np.argmax(vm*0+np.nan_to_num(vm)),'vmax','orange'),(0,'Start','g'),(len(v)-1,'Ende','k')]:
    plt.plot(d.lon[idx],d.lat[idx],'*',ms=16,c=c,label=lab)
if np.argmax(dt)>0: i=np.argmax(dt);plt.plot(d.lon[i],d.lat[i],'x',ms=12,c='k',label=f'größte Lücke {dt[i]:.1f}s')
plt.gca().set_aspect(1/math.cos(math.radians(d.lat.mean())));plt.legend(fontsize=8);plt.title('Track (privat – nicht ins Repo)');plt.savefig(f'{D}/karte.png',dpi=110);plt.close()
