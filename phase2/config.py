# Zentrale Parameter (Profil 'auto'; für Motorrad z.B. SPEED/Gewichte anpassen)
PROFILE='sportwagen'  # Leitbild: Porsche-Tour, Kurvenfluss > Spitzkehren
START=(49.7009613,9.2248586)   # OBI Miltenberg, Nominatim node/480173326
RADIUS_M=30000; RADIUS_DEG_LAT=0.28; RADIUS_DEG_LON=0.42
STEP=20  # m Stützpunktabstand
SPEED={  # track nur falls asphaltiert, stark abgewertet
      'trunk':90,'primary':85,'secondary':80,'tertiary':70,'unclassified':55,'residential':30,
       'living_street':10,'primary_link':50,'secondary_link':45,'tertiary_link':40,'trunk_link':50,'track':25}
# motorway/motorway_link ausgeschlossen (nicht in SPEED); track nur mit Belag asphalt etc.
PAVED={'asphalt','paved','concrete','concrete:plates','chipseal'}
DEM_PRIORITY=['dem/dgm10_by.vrt','dem/dgm1_by_miltenberg.vrt','dem/glo30_bbox.tif']
VIEW_RADIUS=600; BLDG_TOWN=60; CURV_SPEED_DIV=900
# Gewichte Score (0..1-Komponenten)
W=dict(curv=0.35, relief=0.20, view=0.10, edge=0.12, open=0.08, forest=0.05,
       pen_village=0.15, pen_town=0.45, pen_bldg=0.25, pen_primary=0.25, pen_trunk=0.5, pen_residential=0.3,
       pen_track=0.5, pen_surface=0.15, pen_minor=0.12, pen_narrow=0.06, pen_hairpin=0.08)
LOOP_MIN=45*60; LOOP_MAX=60*60
