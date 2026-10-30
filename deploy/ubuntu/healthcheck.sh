#!/bin/bash
# Prüft beide Dienste lokal. Mit --restart wird ein nicht antwortender Dienst neu gestartet.
ok=0
chk(){ if curl -fsS -m 10 "$2" >/dev/null; then echo "OK   $1"; else echo "FEHL $1 ($2)"; ok=1; [ "$3" = --restart ] && systemctl restart "$1"; fi; }
chk scenic-server http://127.0.0.1:8765/health "$1"
chk scenic-nav    http://127.0.0.1:8002/health "$1"
exit $ok
