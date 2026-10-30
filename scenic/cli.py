"""CLI:  python -m scenic.cli --lat 49.7010 --lon 9.2249 --profil auto_sport --min 45 --max 60 -n 5 --out phase3/out/cli
Weitere Parameter: --set key=value (beliebiges Params-Feld, z. B. max_b_share=0.05 hairpin_factor=0.8)."""
import re,argparse,time,json,os
from . import api,export
HOME={'Miltenberg','Großheubach','Kleinheubach','Bürgstadt'}
def kurztitel(orte):
    o=[x for x in orte if x not in HOME and not x.startswith(('Eisenwerk','Gut ','Hof ')) and not re.search(r'(mühle|hof|haus|hütte|säge|siedlung)$',x,re.I)] or orte
    if not o: return 'Runde'
    a,b=o[len(o)//3],o[(2*len(o))//3]
    return f'{a} – {b}' if a!=b else a

def main():
    a=argparse.ArgumentParser(description='Scenic Routes – kurvige Rundtouren')
    a.add_argument('--lat',type=float,default=49.7009613); a.add_argument('--lon',type=float,default=9.2248586)
    a.add_argument('--profil',default='auto_sport',choices=['auto_sport','motorrad'])
    a.add_argument('--min',type=float,default=45); a.add_argument('--max',type=float,default=60)
    a.add_argument('--max-results','-n',dest='n',type=int,default=10,help='Anzahl Familienvertreter (Default 10)'); a.add_argument('--ueberschneidung','--vielfalt',dest='ueberschneidung',type=float,default=0.25,
                   help='max. Überschneidung zwischen ausgegebenen Runden (Jaccard, 0–1 oder Prozent), Default 0.25')
    a.add_argument('--kombis',type=int,default=4000,help='Wegpunkt-Kombinationen je Anzahl (Schnellmodus)')
    a.add_argument('--set',nargs='*',default=[]); a.add_argument('--out',default=None)
    o=a.parse_args(); kw={k:float(v) for k,v in (s.split('=') for s in o.set)}
    ov=o.ueberschneidung/100 if o.ueberschneidung>1 else o.ueberschneidung
    ov=kw.pop('max_jaccard',ov)
    t=time.time(); loops,info=api.find_loops((o.lat,o.lon),o.profil,max_results=o.n,max_jaccard=ov,max_combos=o.kombis,min_min=o.min,max_min=o.max,**kw)
    print('Laufzeit:',{k:round(v,2) for k,v in info['timing'].items()})
    print(f"{info['candidates']} Kandidaten, {info['valid']} gültig, {info['clusters']} Familien (zeige max. {o.n} Vertreter) – {time.time()-t:.1f} s")
    for i,r in enumerate(loops):
        c=r['char']; print(f"\n#{i+1}  {kurztitel(c.get('orte',[]))}\n   {r['len_km']:.0f} km · {r['time_min']:.0f} min · Score {r['score']:.3f} · Tour {c['tour_score']:.3f}\n   [{', '.join(c['tags'])}]\n   {c['text']}")
    if o.out:
        os.makedirs(o.out,exist_ok=True); E=info['E']
        for i,r in enumerate(loops): export.route_files(r,i,o.out)
        export.overview(E,loops,(o.lat,o.lon),o.out); print('Dateien in',o.out)
if __name__=='__main__': main()
