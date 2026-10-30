#!/bin/bash
# Code-Update: neuesten Stand aus dem Repo holen, Abhängigkeiten nachziehen, Dienste neu starten.
# Optional mit neuem Datenarchiv:  sudo bash update.sh [/tmp/scenic-data.tar.gz]
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "Bitte als root (sudo)" >&2; exit 1; }
BASE=/srv/scenic; APP=$BASE/app; HERE=$APP/deploy/ubuntu
REV=$(git -C $APP rev-parse --short HEAD)
sudo -u scenic git -C $APP pull --ff-only || git -C $APP pull --ff-only
echo "Code: $REV -> $(git -C $APP rev-parse --short HEAD)  (zurück: git -C $APP checkout $REV)"
uv pip install -q -p $APP/venv/bin/python -r $HERE/requirements-server.txt
uv pip install -q -p $APP/vh/bin/python -r $HERE/requirements-vh.txt
if [ -n "${1:-}" ]; then
  set -a; . /etc/scenic/scenic.env; set +a
  bash "$HERE/install.sh" "$1"; exit
fi
install -m644 $HERE/scenic-server.service $HERE/scenic-nav.service $HERE/scenic-health.service $HERE/scenic-health.timer /etc/systemd/system/
systemctl daemon-reload; systemctl restart scenic-server scenic-nav
for i in $(seq 1 90); do $HERE/healthcheck.sh >/dev/null 2>&1 && break; sleep 2; done
$HERE/healthcheck.sh
