#!/usr/bin/env bash
# Vor jedem Deploy: komplette Testsuite. Bricht bei Fehler ab (kein Deploy).
set -euo pipefail
cd "$(dirname "$0")/.."
npm run test:all
npm run build
echo "✔ test:all grün – Deploy freigegeben"
