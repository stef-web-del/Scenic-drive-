import os
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D=ROOT; CACHE=f'{ROOT}/phase3/cache'; OUTDIR=f'{ROOT}/phase3/out'
START=(49.7009613,9.2248586)   # OBI Miltenberg (Nominatim node/480173326)
RADIUS_M=30000; STEP=20
PAVED={'asphalt','paved','concrete','concrete:plates','chipseal'}
DEM_PRIORITY=['dem/dgm10_by.vrt','dem/dgm1_by_miltenberg.vrt','dem/glo30_bbox.tif']
VIEW_RADIUS=600; BLDG_TOWN=60; CURV_SPEED_DIV=900
SPEED={'trunk':90,'primary':85,'secondary':80,'tertiary':70,'unclassified':55,'residential':30,'living_street':10,
       'primary_link':50,'secondary_link':45,'tertiary_link':40,'trunk_link':50,'track':25}
