"""Hilfsfunktionen: OSRM-Antworten (Valhalla format=osrm) zu EINER durchgehenden Route zusammenfügen."""
def dec(s,p=6):
    c=[];i=lat=lon=0;f=10**p
    while i<len(s):
        for k in (0,1):
            sh=r=0
            while True:
                b=ord(s[i])-63;i+=1;r|=(b&0x1f)<<sh;sh+=5
                if b<0x20:break
            d=~(r>>1) if r&1 else r>>1
            if k==0:lat+=d
            else:lon+=d
        c.append((lat/f,lon/f))
    return c
def enc(c,p=6):
    out=[];pl=pn=0;f=10**p
    for la,lo in c:
        a,b=round(la*f),round(lo*f)
        for d in (a-pl,b-pn):
            d=~(d<<1) if d<0 else d<<1
            while d>=0x20: out.append(chr((0x20|(d&0x1f))+63));d>>=5
            out.append(chr(d+63))
        pl,pn=a,b
    return ''.join(out)
def merge(routes):
    """Fügt OSRM-Routen hintereinander zu einer Route mit einem Leg zusammen."""
    R=routes[0]; steps=[];admins=[];geo=[];dist=dur=w=0;summ=[]
    for k,r in enumerate(routes):
        L=r['legs'][0]; st=[dict(s) for s in L['steps']]; off=len(admins); admins+=L.get('admins',[])
        for s in st:
            for it in s.get('intersections',[]):
                if 'admin_index' in it: it['admin_index']+=off
        if k>0 and st:
            m=dict(st[0]['maneuver']); m['type']='continue'; st[0]['maneuver']=m
        if k<len(routes)-1 and st and st[-1]['maneuver']['type']=='arrive': st=st[:-1]
        steps+=st; g=dec(r['geometry']); geo+=g if not geo else g[1:]
        dist+=r['distance'];dur+=r['duration'];w+=r.get('weight',0); summ.append(L.get('summary',''))
    leg={'via_waypoints':[],'admins':admins,'weight':w,'duration':dur,'steps':steps,'distance':dist,'summary':', '.join(x for x in summ if x)}
    return {'voiceLocale':R.get('voiceLocale','de-DE'),'weight_name':R.get('weight_name','auto'),'weight':w,'duration':dur,'distance':dist,'legs':[leg],'geometry':enc(geo)}
