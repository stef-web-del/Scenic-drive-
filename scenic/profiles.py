"""Fahrprofile. Default: auto_sport (Leitbild 'Porsche-Tour'). Alle Werte über Params überschreibbar."""
from dataclasses import dataclass, field, asdict, replace
@dataclass
class Params:
    profile: str='auto_sport'
    min_min: float=45; max_min: float=60          # Dauer-Fenster
    # Score-Gewichte (positiv)
    w_curv: float=0.35; w_relief: float=0.20; w_view: float=0.10; w_edge: float=0.12; w_open: float=0.08; w_forest: float=0.05
    w_section: float=0.5        # Anteil Abschnitts-Score am Kantenscore (0=nur Kante)
    hairpin_factor: float=0.5   # Gewicht enger Kehren (r<40 m) im Kurvenmaß; 1=Kehren wie flüssige Kurven
    # Abzüge
    pen_town: float=0.45; pen_village: float=0.15; pen_bldg: float=0.25
    pen_bstrasse: float=0.35; pen_trunk: float=0.6; pen_residential: float=0.3; pen_track: float=0.5
    pen_minor: float=0.12; pen_narrow: float=0.06; pen_hairpin: float=0.08
    # Routing / Kriterien
    cost_base: float=0.3; cost_bad: float=1.7; b_cost_mult: float=2.0
    max_overlap: float=0.08       # max. Anteil doppelt gefahrener Länge (innerhalb einer Runde)
    max_jaccard: float=0.25       # max. Überschneidung zwischen ausgegebenen Runden (längengew. Jaccard), 0.25 = 25 %
    max_b_share: float=0.12       # max. Anteil Bundesstraße/primary/trunk
    max_town_share: float=0.25    # max. Anteil Ortsdurchfahrt (Gebäudedichte>BLDG_TOWN o. <600 m an Dorf/Stadtzentrum)
    min_score: float=0.15
    anchor_min_m: float=3000; anchor_max_m: float=16000; anchor_spacing_m: float=1200; n_anchors: int=160
    speed_factor: float=1.0       # globale Kalibrierung (Valhalla)
    allow_narrow: bool=True
    avoid_eids: tuple=()          # Neuheit: bereits gefahrene Kanten (Kostenaufschlag)
    avoid_mult: float=3.0
    town_minor_mult: float=1.0   # Kostenfaktor Neben-/Wohnstraßen im Ort (1 = aus); >1 hält die Runde im Ort auf der Hauptstraße
    # Hin-und-zurück-Sperre (Stichfahrten)
    no_backtrack: bool=True       # harte Regel: keine Straße doppelt/in Gegenrichtung, keine Wende, Wegpunkt wird durchfahren
    leg_limit_mult: float=5.0     # Suchradius je Bein (Vielfaches der Baumkosten Start->fernster Wegpunkt)
    start_zone_m: float=1500      # Ausnahme: gemeinsames Zu-/Abfahrtsstück um den Start (Luftlinie Kantenmitte–Start)
    max_start_shared_m: float=2000  # ... aber höchstens so viel doppelt gefahrene Länge im Startbereich
    # Interessante Abschnitte (Highlights)
    hl_min_m: float=1500          # Mindestlänge eines Highlight-Abschnitts
    hl_gap_m: float=300           # kürzere Unterbrechungen zwischen guten Stücken werden überbrückt (zusammengefasst)
    hl_min_score: float=0.30      # Mindest-Score (Mittel) eines Highlight-Abschnitts
PROFILES={
 'auto_sport': Params(),
 'motorrad': Params(profile='motorrad', w_curv=0.45, w_relief=0.15, hairpin_factor=0.8, pen_hairpin=0.0,
                    pen_narrow=0.03, pen_bstrasse=0.45, speed_factor=0.95),
}
def get(profile='auto_sport', **kw):
    return replace(PROFILES[profile], **kw)
