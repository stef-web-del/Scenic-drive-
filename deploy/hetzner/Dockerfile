# Scenic Routes – Server „Runde ab Punkt“. Nur vorbereitet, nicht gebaut oder ausgerollt.
# Bauen (lokal):  docker build -f deploy/hetzner/Dockerfile -t scenic-server .
# Daten (phase3/cache, places.gpkg, valhalla/) kommen als Volume, nicht ins Image.
FROM python:3.13-slim
RUN apt-get update && apt-get install -y --no-install-recommends libexpat1 && rm -rf /var/lib/apt/lists/*
WORKDIR /srv/scenic
COPY deploy/hetzner/requirements.txt /tmp/req.txt
RUN python -m venv venv && venv/bin/pip install --no-cache-dir -r /tmp/req.txt \
 && python -m venv vh && vh/bin/pip install --no-cache-dir pyvalhalla==3.9.1 numpy
COPY scenic/ scenic/
COPY server/ server/
RUN useradd -r -u 10001 scenic && chown -R scenic /srv/scenic
USER scenic
ENV SCENIC_HOST=0.0.0.0 SCENIC_PORT=8765 PYTHONUNBUFFERED=1
EXPOSE 8765
HEALTHCHECK --interval=60s --start-period=120s CMD python -c "import urllib.request,os;urllib.request.urlopen(f'http://127.0.0.1:{os.environ[\"SCENIC_PORT\"]}/health',timeout=5)"
CMD ["venv/bin/python", "-W", "ignore", "-m", "server.server"]
