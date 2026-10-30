# CI – nur vorbereitet, nicht eingerichtet

Der Ort für CI ist noch offen (Roadmap-Entscheidung). `github-actions.example.yml` ist eine Vorlage. Sie wird erst aktiv,
wenn sie als `.github/workflows/ci.yml` in einem Repository liegt. Ein Konto oder Repository wurde dafür nicht angelegt.

Der Qualitätstest braucht die vorbereiteten Daten (`phase3/cache/features.gpkg`, ca. 1 min Rechenzeit). Diese Daten liegen nicht im Code.
Deshalb läuft er sinnvoll nur auf einem Runner mit Datenzugriff, also auf Stefans Server als self-hosted Runner.

Lokal:  venv/bin/python -m pytest -q tests      (alle Tests, ~1 min)
        venv/bin/python -m pytest -q tests -m "not slow"
