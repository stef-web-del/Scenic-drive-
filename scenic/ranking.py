"""Fahrstile und Auswahl (Entscheidung Stefan 29.10.): Sportlich, Genießer, Entdecker.
Ein Fahrstil ist eine Gewichtung messbarer Rundenkennzahlen aus character.analyze. Jede Kennzahl wird innerhalb der
gültigen Runden eines Starts als Perzentil (0..1) normiert, damit die Gewichte vergleichbar sind.
Gesucht wird einmal (gleiche Regeln/Ausschlüsse für alle Stile: kein Hin-und-zurück, Bundesstraße <=12 %, Ort <=25 %);
der Stil bestimmt nur die Reihenfolge. Pro Start und Stil bleiben bis zu KEEP Runden mit Rang; ab Rang TOP+1 bekommt
jede Runde ehrlich Gründe, warum sie schwächer ist als die Top-Runden."""
import numpy as np
TOP=6; KEEP=20
# Kennzahl -> (Feld, Vorzeichen). Vorzeichen -1: weniger ist besser.
METRICS={
 'kurven':('curv_mean',1),          # mittlere Kurvigkeit (0..1)
 'relief':('relief_mean',1),        # Relief/Steigungen (0..1)
 'hoehenmeter':('ascent_m',1),
 'aussicht':('view_frac',1),        # Anteil der Strecke mit Fernsicht
 'highlights':('highlight_share',1),# Anteil Highlight-Abschnitte (>=1500 m)
 'rhythmus':('rhythm_mean',1),
 'landschaft':('open_forest',1),    # offene Landschaft + Wald (Anteil)
 'ortsfrei':('town_share',-1),
 'unterbrechungen':('interrupts_per10km',-1),
 'vielfalt':('n_orte',1),           # Zahl der durchfahrenen/gestreiften Orte (Entdecken)
 'einkehr':('n_pausen',1),          # Einkehr-/Aussichts-/Parkmöglichkeiten an der Strecke
 'gesamt':('tour_score',1),
}
PROFILES={
 'sportlich':{'label':'Sportlich','info':'Viele Kurven, Rhythmus und Steigen, wenig Unterbrechungen.',
              'w':{'kurven':.30,'rhythmus':.15,'relief':.15,'highlights':.15,'unterbrechungen':.10,'gesamt':.15}},
 'geniesser':{'label':'Genießer','info':'Aussicht, ruhige Landschaft, wenig Ortsdurchfahrt, Einkehr unterwegs.',
              'w':{'aussicht':.25,'landschaft':.15,'ortsfrei':.20,'einkehr':.10,'kurven':.10,'gesamt':.20}},
 'entdecker':{'label':'Entdecker','info':'Viele verschiedene Orte und Aussichten, Höhenmeter, Abwechslung.',
              'w':{'vielfalt':.25,'aussicht':.20,'hoehenmeter':.15,'einkehr':.10,'highlights':.10,'gesamt':.20}},
}
def metric_table(C):
    """C: Liste der character-Dicts -> Rohwerte je Kennzahl (np.array)."""
    def g(c,f):
        if f=='open_forest': return float((c.get('open') or 0)+(c.get('forest') or 0))
        if f=='n_orte': return float(len(c.get('orte') or []))
        if f=='n_pausen': return float(len(c.get('pauses') or []))
        v=c.get(f); return float(v) if v is not None else 0.0
    return {k:np.array([g(c,f) for c in C]) for k,(f,_) in METRICS.items()}
def pct(x,sign):
    if len(x)<2: return np.ones(len(x))
    r=np.argsort(np.argsort(sign*x,kind='stable'),kind='stable'); return r/(len(x)-1)
def profile_scores(C):
    T=metric_table(C); P={k:pct(T[k],METRICS[k][1]) for k in T}
    return {n:sum(w*P[k] for k,w in pr['w'].items()) for n,pr in PROFILES.items()},T
def reasons(i,top,T,over):
    """Gründe, warum Runde i schwächer ist als die Top-Runden (Vergleich mit deren Median). Nur aus Messwerten."""
    med=lambda k: float(np.median(T[k][top])); R=[]
    if T['ortsfrei'][i]>med('ortsfrei')+0.05: R.append('mehr Ortsdurchfahrt')
    if T['kurven'][i]<0.85*med('kurven'): R.append('weniger kurvig')
    if T['hoehenmeter'][i]<0.8*med('hoehenmeter'): R.append('weniger Höhenmeter')
    if T['aussicht'][i]<0.8*med('aussicht'): R.append('weniger Aussicht')
    if T['unterbrechungen'][i]>1.25*med('unterbrechungen')+0.2: R.append('mehr Unterbrechungen')
    if over: R.append(f"ähnlich wie Runde {over[0]} ({round(100*over[1])} % gleich)")
    return R or ['etwas niedrigere Gesamtwertung']
def select(ok,C,jac,ov=0.25,top=TOP,keep=KEEP,relax=(0.35,0.5)):
    """ok/C: gültige Runden + Charakterisierung; jac(i,j) -> Überschneidung (längengew. Jaccard).
    Je Stil: greedy nach Stil-Score, Überschneidung <= ov (Stefans 25 %-Regel); reicht das nicht für keep Runden,
    werden in Stufen (relax) ähnlichere Runden ergänzt und als 'ähnlich wie Runde N' gekennzeichnet.
    Rückgabe {stil:[{i,rank,score,why,maxov}]}"""
    S,T=profile_scores(C); out={}; cache={}
    def J(a,b):
        k=(a,b) if a<b else (b,a)
        if k not in cache: cache[k]=jac(*k)
        return cache[k]
    for n,sc in S.items():
        order=list(np.argsort(-sc,kind='stable')); sel=[]
        for lim in (ov,)+tuple(relax):
            for i in order:
                if len(sel)>=keep: break
                if i in sel: continue
                if all(J(i,j)<=lim for j in sel): sel.append(i)
            if len(sel)>=keep: break
        res=[]
        for r,i in enumerate(sel):
            o=max(((J(i,j),k+1) for k,j in enumerate(sel[:r])),default=(0,None))
            d=dict(i=int(i),rank=r+1,score=round(float(sc[i]),3),maxov=round(float(o[0]),3))
            if r>=top or o[0]>ov: d['why']=reasons(i,sel[:top],T,(o[1],o[0]) if o[0]>ov else None)
            res.append(d)
        out[n]=res
    return out
