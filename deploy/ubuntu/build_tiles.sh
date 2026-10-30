#!/bin/bash
# Baut Valhalla-Kacheln für das Testgebiet neu (optional – das Datenarchiv enthält fertige Kacheln).
# Braucht ~3 GB Platz und ~1,5 GB RAM; auf dem 4-GB-Server mit Swap möglich, lokal schneller.
#   bash build_tiles.sh [ZIEL=/srv/scenic/app/valhalla]   (BBOX überschreibbar: BBOX=minLon,minLat,maxLon,maxLat)
# Quelle: Geofabrik-Auszüge (ODbL), wie in docs/daten-und-lizenzen.md.
set -euo pipefail
OUT=${1:-/srv/scenic/app/valhalla}
VH=${VH:-/srv/scenic/app/vh/bin}
BBOX=${BBOX:-8.50,49.21,10.03,50.20}   # Testgebiet wie der Navigationsserver
WORK=${WORK:-/var/tmp/scenic-osm}
REGIONS=(europe/germany/bayern/unterfranken europe/germany/hessen europe/germany/baden-wuerttemberg/karlsruhe-regbez europe/germany/baden-wuerttemberg/stuttgart-regbez)
command -v osmium >/dev/null || { echo "osmium fehlt: sudo apt-get install -y osmium-tool" >&2; exit 1; }
mkdir -p "$WORK" && cd "$WORK"
for r in "${REGIONS[@]}"; do f=$(basename $r)-latest.osm.pbf; [ -f "$f" ] || curl -fL -o "$f" "https://download.geofabrik.de/$r-latest.osm.pbf"; done
osmium merge -O -o merged.osm.pbf *-latest.osm.pbf
osmium extract -O -b "$BBOX" -o area.osm.pbf merged.osm.pbf
NEW="$OUT/tiles.new"; rm -rf "$NEW"; mkdir -p "$NEW"
python3 - "$OUT/valhalla.json" "$NEW" "$WORK/build.json" <<'PY'
import json,sys; c=json.load(open(sys.argv[1])); c['mjolnir']['tile_dir']=sys.argv[2]; c['mjolnir']['concurrency']=2
json.dump(c,open(sys.argv[3],'w'))
PY
"$VH/valhalla_build_tiles" -c "$WORK/build.json" area.osm.pbf
# Austausch erst nach erfolgreichem Bau; alter Stand bleibt als tiles.old
rm -rf "$OUT/tiles.old"; mv "$OUT/tiles" "$OUT/tiles.old"; mv "$NEW" "$OUT/tiles"
echo "Fertig. Dienste neu starten: sudo systemctl restart scenic-server scenic-nav"
echo "Achtung: neue OSM-Daten können andere Routen ergeben; die freie Suche nutzt zusätzlich phase3/cache (aus derselben OSM-Version gebaut)."
