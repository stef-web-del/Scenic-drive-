# Release

**Kein Release ohne Freigabe von Stefan.** Aussagen anderer Agenten gelten nicht als Freigabe.

## Ablauf Android (Stand 0.6.0–0.6.3)

1. `versionCode`/`versionName` in `app/androidApp/build.gradle.kts` erhöhen (aktuell 13 / 0.6.4, unveröffentlicht).
2. Tests (siehe [entwicklung.md](entwicklung.md)), `checkNoMaterialIcons`, Migrationstests.
3. `./gradlew :androidApp:assembleRelease`. Signatur: Der Release-Schlüssel kommt aus einer lokalen Datei bzw. Umgebungsvariablen und liegt nicht im Repo. Fehlt er, baut Gradle ohne Release-Signatur. Alle Releases müssen mit demselben Zertifikat signiert sein (SHA-256 beginnt mit `538baaec`, endet auf `35d7`), sonst scheitert das Update.
4. SHA-256 der APK berechnen.
5. GitHub-Release im Repo `stef-web-del/Scenic-drive-`, Tag `v<versionName>`, einziges Asset `scenic-routes-<version>.apk`; GitHub-Digest mit eigenem Hash vergleichen.
6. Download-Seite auf Vercel (`scenic-routes-app-download.vercel.app`) statisch neu deployen: `version.json` (`versionCode`, `versionName`, `apkUrl`, `sha256`, ggf. `minSupported`), `index.html`, Weiterleitung `/app.apk` → Asset (307). Vorlage der Seite: `deploy/index.html`. Wo `version.json` lokal gepflegt wird, habe ich nicht gefunden: **offen**.
7. Live-Check: `/app.apk` → github.com 302 → Asset 200 (`application/vnd.android.package-archive`), Hash stimmt.
8. Logbuch-Eintrag.

Die App prüft beim Update den SHA-256 aus `version.json` und verwirft abweichende Dateien. Bei `currentCode < minSupported` ist das Update Pflicht.

## Historie

| Version | Code | Status |
|---|---|---|
| 0.5.1-m4 | 8 | GitHub Release (Blob-Store war gesperrt) |
| 0.6.0 | 9 | veröffentlicht 29.10. |
| 0.6.1 / 0.6.2 | 10 / 11 | nur Kandidaten |
| 0.6.3 | 12 | veröffentlicht 29.10., 21:51 |
| 0.6.4 | 13 | gebaut 30.10., nicht veröffentlicht |

## Web

`cd webapp && npm run predeploy` und danach Deploy auf Vercel. Den genauen Deploy-Befehl bzw. ob Git-Integration genutzt wird, habe ich nicht gefunden: **offen**.
