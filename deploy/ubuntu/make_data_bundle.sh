#!/bin/bash
# Packt die Laufzeitdaten (nicht im Git-Repo, ~550 MB) in ein Archiv für den Server.
# Aufruf auf dem Rechner MIT den Daten:  deploy/ubuntu/make_data_bundle.sh [/pfad/scenic-data.tar.gz]
# Danach z. B.:  scp scenic-data.tar.gz <user>@<server>:/tmp/
set -euo pipefail
ROOT=${SCENIC_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
OUT=${1:-$PWD/scenic-data.tar.gz}
cd "$ROOT"
FILES=(places.gpkg
  phase3/cache/features.gpkg phase3/cache/pois.gpkg phase3/cache/smoothness.json
  phase3/cache/parts.pkl phase3/cache/char_all.pkl phase3/cache/routes_all.pkl phase3/cache/routes_all_alt.pkl
  dem/dgm10 dem/dgm10_by.vrt dem/dgm1_by dem/dgm1_by_miltenberg.vrt dem/glo30_bbox.tif
  valhalla/tiles valhalla/valhalla.json
  server/vercel-navserver/data/loops.json)
for f in "${FILES[@]}"; do [ -e "$f" ] || { echo "fehlt: $f" >&2; exit 1; }; done
tar -caf "$OUT" "${FILES[@]}"
sha256sum "$OUT" | tee "$OUT.sha256"
du -h "$OUT"
