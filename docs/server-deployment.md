# Server-Betrieb auf eigenem Ubuntu-Server

Diese Anleitung beschreibt, wie die beiden Scenic-Routes-Dienste auf einem eigenen Ubuntu-Server laufen. Alle Dateien liegen in `deploy/ubuntu/`.

| Dienst | Code | Port (lokal) | Öffentlich über Caddy |
|---|---|---|---|
| Freie Rundensuche („Runde ab Punkt“) | `server/server.py` | 8765 | `https://<domain>/jobs…`, Health unter `/free/health` |
| Navigationsserver (Valhalla, gleiche Endpunkte wie auf Vercel) | `server/vercel-navserver/api/index.py` | 8002 | `https://<domain>/health`, `/scenic/loop/{id}`, `/scenic/rejoin`, `/route`, `/trace_route`, `/locate`, `/height` |

## Warum systemd + venv statt Docker

- Bei 4 GB RAM zählt jedes MB. Docker kostet zusätzlich Speicher und Platz und bringt hier keinen Vorteil.
- systemd liefert Neustart, Speichergrenzen (`MemoryMax`) und Logs (journald) ohne weitere Software.
- Die Python-Umgebungen sind bereits fest versioniert (`requirements-*.txt`), `uv` installiert sie in Sekunden.
- Docker war in der Entwicklungsumgebung nicht verfügbar und wäre damit ungetestet gewesen.

## Voraussetzungen

- Ubuntu 22.04 oder 24.04, x86_64 (arm64 ungetestet; pyvalhalla bietet aber Wheels dafür)
- mindestens 4 GB RAM; install.sh legt zusätzlich 4 GB Swap an
- etwa 3 GB freier Platz (Code + Daten ~0,6 GB, Python-Umgebungen ~1,1 GB, Swap 4 GB extra); mit Kachel-Neubau weitere ~3 GB
- root-Zugang (sudo) und offene Ports 22, 80, 443
- für HTTPS: eine Domain, deren A-Record auf die Server-IP zeigt. Ohne Domain läuft alles nur per `http://<IP>`; die App braucht für den Release-Build aber HTTPS.

## Daten

Code kommt aus dem öffentlichen Repo. Die Laufzeitdaten (~550 MB, gepackt ~290 MB) liegen **nicht** im Repo: Kartenmerkmale (`phase3/cache`), Orte, Höhenmodell (`dem/…`), Valhalla-Kacheln, Runden des Navservers. Sie werden auf dem Rechner mit den Daten gepackt und per `scp` übertragen:

```bash
deploy/ubuntu/make_data_bundle.sh /tmp/scenic-data.tar.gz   # schreibt auch .sha256
scp /tmp/scenic-data.tar.gz* <user>@<server>:/tmp/
```

Valhalla-Kacheln lassen sich auf dem Server neu bauen (`deploy/ubuntu/build_tiles.sh`, lädt Geofabrik-Auszüge und braucht `osmium-tool`). Neue OSM-Daten können allerdings andere Routen ergeben, weil `phase3/cache` aus derselben OSM-Version stammt. Für den Normalbetrieb also die mitgelieferten Kacheln nehmen.

## Installation Schritt für Schritt

1. Auf den Server einloggen und prüfen: `sha256sum -c /tmp/scenic-data.tar.gz.sha256`
2. Code holen (das Repo ist öffentlich; install.sh braucht die Nachbardateien):
   ```bash
   git clone --depth 1 https://github.com/stef-web-del/Scenic-drive-.git /tmp/scenic-src
   ```
3. Installieren (ein Befehl, mehrfach ausführbar):
   ```bash
   sudo SCENIC_DOMAIN=routen.example.de SCENIC_ACME_EMAIL=ich@example.de \
        bash /tmp/scenic-src/deploy/ubuntu/install.sh /tmp/scenic-data.tar.gz
   ```
   Ohne Domain `SCENIC_DOMAIN=` weglassen.

install.sh erledigt nacheinander:
- Pakete und Caddy (offizielles Repo) sowie `uv` installieren
- Swapfile anlegen, `vm.swappiness=10` setzen
- Benutzer `scenic` sowie `/srv/scenic/app`, `/var/lib/scenic` und `/etc/scenic/scenic.env` (aus `scenic.env.example`) anlegen
- Code klonen und Daten entpacken; `valhalla/valhalla.json` auf die Serverpfade umschreiben und den Kachelcache auf 200 MB verkleinern
- Python-3.13-Umgebungen `venv` (Suche) und `vh` (Valhalla) bauen
- systemd-Dienste einrichten: `scenic-server`, `scenic-nav` und den Timer `scenic-health`, dazu journald-Grenzen
- Caddyfile erzeugen: mit Domain automatisches HTTPS (Let's Encrypt), sonst `:80`
- ufw aktivieren: 22, 80 und 443 freigeben, sonst nichts

## Einstellungen (`/etc/scenic/scenic.env`)

Die Datei enthält keine Geheimnisse. Wichtig sind:
- `SCENIC_DOMAIN` – Domain oder leer
- `SCENIC_PROCS` – Rechenprozesse je Suche (Standard 3). Das Ergebnis bleibt gleich, nur die Dauer ändert sich (siehe Messwerte).
- `SCENIC_NAV_CACHE` – Valhalla-Kachelcache in Bytes (Standard 200 MB)

Nach Änderungen: `sudo systemctl restart scenic-server scenic-nav`; nach einer Domain-Änderung install.sh erneut ausführen.

## Speicher und Last (4-GB-Server)

- Es läuft immer nur **eine** Suche gleichzeitig (Sperre in server.py). Weitere Suchen warten.
- Grenzen per systemd: `scenic-server` MemoryHigh 2,6 GB, MemoryMax 3 GB; `scenic-nav` 500/700 MB. Wird eine Grenze überschritten, startet systemd den Dienst neu (`Restart=always`).
- `scenic-health.timer` prüft alle 5 Minuten beide `/health` und startet einen hängenden Dienst neu.

Messwerte (30.10.2026, x86_64, PSS über alle Prozesse summiert, Suche ab OBI Miltenberg, 45 min, sportlich, ohne Cache):

| SCENIC_PROCS | Ruhe | Spitze | Dauer gesamt | Ergebnis |
|---|---|---|---|---|
| 6 (bisher) | ~845 MB | 2680 MB | 33,6 s | 4 Runden, ids 2, 6, 7, 8 |
| 4 | ~735 MB | 2331 MB | 40,4 s | identisch |
| 3 (Standard) | ~820 MB | 2115 MB | 49–53 s | identisch (auch mit 200-MB-Valhalla-Cache) |
| 2 | ~760 MB | 1981 MB | 67,7 s | 4 Runden |

Der Navigationsserver belegt etwa 135 MB. Beide zusammen bleiben bei PROCS=3 unter 2,3 GB. Die erste Runde erscheint in allen Fällen nach etwa 4–6 s.

## Prüfen

```bash
/srv/scenic/app/deploy/ubuntu/healthcheck.sh          # OK scenic-server / OK scenic-nav
curl https://<domain>/health                          # Navserver: {"ok": true, "loops": 342, …}
curl https://<domain>/free/health                     # freie Suche
curl -X POST https://<domain>/jobs -H 'Content-Type: application/json' \
     -d '{"lat":49.7009613,"lon":9.2248586,"name":"OBI Miltenberg","minuten":45,"profil":"sportlich"}'
curl "https://<domain>/jobs/<id>?offset=0&limit=12"   # nach ~50 s: "status": "done", 4 Runden
curl -X POST https://<domain>/route -d '{"locations":[{"lat":49.7009,"lon":9.2248},{"lat":49.75,"lon":9.30}],"costing":"auto"}'
systemctl status scenic-server scenic-nav; journalctl -u scenic-server -n 50
```

## Updates

```bash
sudo bash /srv/scenic/app/deploy/ubuntu/update.sh                         # nur Code
sudo bash /srv/scenic/app/deploy/ubuntu/update.sh /tmp/scenic-data.tar.gz # Code + neue Daten
```

update.sh gibt die alte Version aus. Zurück geht es mit `git -C /srv/scenic/app checkout <rev>`, danach die Dienste neu starten.

## Logs

- Dienste: journald, begrenzt auf 300 MB und 30 Tage (`/etc/systemd/journald.conf.d/scenic.conf`)
- Caddy-Zugriffe: `/var/log/caddy/scenic.log`; Caddy rotiert selbst bei 20 MB und behält 5 Dateien

## Backup

- Zu sichern ist nur `/etc/scenic/scenic.env` und, falls angepasst, `/etc/caddy/Caddyfile`.
- Code liegt im Repo, Daten im Archiv `scenic-data.tar.gz`. Das Archiv sollte zusätzlich auf einem zweiten Rechner liegen.
- Suchergebnisse liegen nur im Speicher und müssen nicht gesichert werden.
- Caddys Zertifikate (`/var/lib/caddy`) werden bei Verlust automatisch neu ausgestellt.

## Sicherheit

- Öffentlich sind nur 80/443 (Caddy) und 22 (SSH). Die Python-Dienste lauschen nur auf 127.0.0.1.
- Der Navserver begrenzt sich selbst: Testgebiet-BBOX, höchstens 25 Punkte, 1 MB Body, 60 Anfragen je 5 min und IP.
- Die freie Suche hat **kein** eigenes Ratenlimit. Wegen der Sperre ist immer nur eine Suche aktiv; viele Anfragen erzeugen also Wartezeit, aber keinen Speicherengpass. Bei Missbrauch kann man in Caddy Basic-Auth oder ein IP-Limit ergänzen.
- SSH: nur mit Schlüssel anmelden (`PasswordAuthentication no`), Updates per `unattended-upgrades`.

## App-Seite (nur Dokumentation, nichts geändert)

- **Freie Runden:** `app/androidApp/build.gradle.kts` setzt `FREE_LOOP_URL`. Im Debug-Build ist das `http://10.0.2.2:8765`, im Release `""` (aus). Für den eigenen Server im Release `https://<domain>` eintragen. Alternativ in der App unter Einstellungen → Feld „Freie Runden“ (`Settings.kt`, Schlüssel `freeLoop`); das überschreibt den Build-Wert. Die App ruft `<basis>/jobs…` auf.
- **Navigationsserver:** `DEFAULT_BACKEND` in `build.gradle.kts`, beim Bauen über `-Pscenic.backend=https://<domain>` gesetzt. Default ist der Vercel-Navserver. Er ist auch in den Einstellungen änderbar (`NavBackend`).
- Für beides genügt dieselbe Basis-URL `https://<domain>`. Eine Änderung braucht einen neuen App-Build oder eine manuelle Einstellung; ein Release erst nach Stefans Okay.

## Nicht getestet (Stand 30.10.2026)

Getestet wurde in der Entwicklungsumgebung (Debian, ohne root):
- frische Python-Umgebungen aus `requirements-*.txt`
- das Datenarchiv, in einen sauberen Repo-Stand entpackt
- beide Dienste aus diesem Stand, alle Navserver-Endpunkte und eine echte `/jobs`-Suche

**Nicht** getestet:
- install.sh als Ganzes auf echtem Ubuntu (apt, Benutzeranlage, Swap)
- systemd-Grenzen und Timer
- HTTPS/Let's Encrypt (die Weiterleitungen der Caddyfile wurden mit Caddy 2.10 ohne TLS auf einem Testport geprüft: `/health`, `/free/health`, `/route` und `/jobs` kommen richtig an)
- ufw
- eine echte 4-GB-cgroup; der Speicher wurde als Prozess-PSS gemessen
- build_tiles.sh
- die BBOX des ursprünglichen Ausschnitts `miltenberg_bbox.osm.pbf` ist nicht dokumentiert; build_tiles.sh nimmt die Navserver-BBOX
