#!/bin/bash
set -e
export PATH=/workspace/scenic/tools/bin:$PATH
cd /workspace/scenic/osm
P=/workspace/scenic/miltenberg_bbox.osm.pbf
f(){ osmium tags-filter -O $P "${@:3}" -o $1.pbf; osmium export -O $1.pbf -f geojsonseq -o $1.geojsonl $2 -a id; }
f roads --geometry-types=linestring w/highway &
f places "" nwr/place wr/landuse=residential &
f landcover --geometry-types=polygon wr/landuse wr/natural &
f viewpoints "" nw/tourism=viewpoint &
f buildings --geometry-types=polygon wr/building &
wait
