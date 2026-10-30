#!/bin/bash
# Scenic Routes – Server-Installation auf frischem Ubuntu 22.04/24.04 (x86_64, ab 4 GB RAM).
# Aufruf als root:
#   sudo SCENIC_DOMAIN=routen.example.de bash install.sh /tmp/scenic-data.tar.gz
# SCENIC_DOMAIN leer = nur http://<IP> auf Port 80. Mehrfach ausführbar (idempotent).
set -euo pipefail
DATA=${1:-}
[ "$(id -u)" = 0 ] || { echo "Bitte als root (sudo) ausführen" >&2; exit 1; }
[ -n "$DATA" ] && [ -f "$DATA" ] || { echo "Aufruf: install.sh <scenic-data.tar.gz>" >&2; exit 1; }
HERE=$(cd "$(dirname "$0")" && pwd)
CLI_DOMAIN=${SCENIC_DOMAIN-__unset__}; CLI_EMAIL=${SCENIC_ACME_EMAIL-__unset__}
BASE=/srv/scenic; APP=$BASE/app
SWAP_GB=${SCENIC_SWAP_GB:-4}
log(){ echo -e "\n== $*"; }

log "1/9 Pakete"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q git curl ca-certificates zstd ufw debian-keyring debian-archive-keyring apt-transport-https gpg logrotate
if ! command -v caddy >/dev/null; then   # offizielles Caddy-Repo (cloudsmith)
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -q && apt-get install -y -q caddy
fi
command -v uv >/dev/null || curl -LsSf https://astral.sh/uv/install.sh | env UV_INSTALL_DIR=/usr/local/bin UV_NO_MODIFY_PATH=1 sh

log "2/9 Swapfile (${SWAP_GB} GB)"
if ! swapon --show=NAME --noheadings | grep -q /swapfile; then
  fallocate -l ${SWAP_GB}G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
sysctl -qw vm.swappiness=10; echo 'vm.swappiness=10' > /etc/sysctl.d/90-scenic.conf

log "3/9 Benutzer und Verzeichnisse"
id scenic >/dev/null 2>&1 || useradd --system --home $BASE --shell /usr/sbin/nologin scenic
mkdir -p $BASE /var/lib/scenic/nav /etc/scenic
[ -f /etc/scenic/scenic.env ] || cp "$HERE/scenic.env.example" /etc/scenic/scenic.env
set -a; . /etc/scenic/scenic.env; set +a
# Werte aus dem Aufruf haben Vorrang und werden in scenic.env gemerkt
[ "$CLI_DOMAIN" != __unset__ ] && SCENIC_DOMAIN=$CLI_DOMAIN && sed -i "s|^SCENIC_DOMAIN=.*|SCENIC_DOMAIN=$SCENIC_DOMAIN|" /etc/scenic/scenic.env
[ "$CLI_EMAIL" != __unset__ ] && SCENIC_ACME_EMAIL=$CLI_EMAIL && sed -i "s|^SCENIC_ACME_EMAIL=.*|SCENIC_ACME_EMAIL=$SCENIC_ACME_EMAIL|" /etc/scenic/scenic.env

log "4/9 Code"
if [ -d $APP/.git ]; then git -C $APP pull --ff-only; else git clone --depth 1 -b "${SCENIC_GIT_BRANCH:-main}" "${SCENIC_GIT_URL}" $APP; fi

log "5/9 Daten entpacken"
tar -xf "$DATA" -C $APP
python3 - "$APP" "${SCENIC_NAV_CACHE:-200000000}" <<'PY'
import json,sys
app,cache=sys.argv[1],int(sys.argv[2]); p=f'{app}/valhalla/valhalla.json'; c=json.load(open(p)); m=c['mjolnir']
m['tile_dir']=f'{app}/valhalla/tiles'; m['tile_extract']=f'{app}/valhalla/none.tar'; m['traffic_extract']=f'{app}/valhalla/none-traffic.tar'
m['max_cache_size']=cache   # schlank: weniger RAM, gleiche Routen
c.setdefault('additional_data',{})['elevation']=f'{app}/valhalla/elevation/'
json.dump(c,open(p,'w'),indent=1)
PY

log "6/9 Python-Umgebungen (uv, Python 3.13)"
cd $APP
[ -x venv/bin/python ] || uv venv -q -p 3.13 venv
uv pip install -q -p venv/bin/python -r "$HERE/requirements-server.txt"
[ -x vh/bin/python ] || uv venv -q -p 3.13 vh
uv pip install -q -p vh/bin/python -r "$HERE/requirements-vh.txt"
chown -R scenic:scenic $BASE /var/lib/scenic

log "7/9 Dienste (systemd)"
install -m644 "$HERE"/scenic-server.service "$HERE"/scenic-nav.service "$HERE"/scenic-health.service "$HERE"/scenic-health.timer /etc/systemd/system/
mkdir -p /etc/systemd/journald.conf.d && install -m644 "$HERE/journald-scenic.conf" /etc/systemd/journald.conf.d/scenic.conf
systemctl restart systemd-journald
systemctl daemon-reload
systemctl enable --now scenic-server scenic-nav scenic-health.timer
systemctl restart scenic-server scenic-nav

log "8/9 Caddy (${SCENIC_DOMAIN:-nur http/Port 80})"
SITE=${SCENIC_DOMAIN:-:80}; EM=""; [ -n "${SCENIC_ACME_EMAIL:-}" ] && EM="email $SCENIC_ACME_EMAIL"
sed -e "s|__SITE__|$SITE|" -e "s|__EMAIL__|$EM|" "$HERE/Caddyfile.tmpl" > /etc/caddy/Caddyfile
mkdir -p /var/log/caddy && chown caddy:caddy /var/log/caddy
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
systemctl reload caddy || systemctl restart caddy

log "9/9 Firewall (ufw)"
ufw allow OpenSSH >/dev/null; ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null; ufw status

echo; echo "Warte auf Dienste (Suchserver lädt ~1 min)…"
for i in $(seq 1 90); do "$HERE/healthcheck.sh" >/dev/null 2>&1 && break; sleep 2; done
"$HERE/healthcheck.sh" && echo "Fertig. Prüfen: siehe docs/server-deployment.md, Abschnitt Prüfen."
