# Hintergrund-Audit Navigation & Aufzeichnung – 0.6.4 (versionCode 13)

Stand 30.10.2026. Geprüft: Manifest, `rec/RecordingService.kt`, `rec/RideRecorder.kt`, `rec/RecordUi.kt`, `nav/NavViewModel.kt`, `nav/NavigationScreen.kt`, `nav/NavSpeech.kt`, `MainActivity.kt`, `androidApp/build.gradle.kts`, abgeglichen mit der aktuellen Android-Doku.

## Gesamturteil
**Die Aufzeichnung ist im Hintergrund solide gebaut.** Sie läuft als Vordergrunddienst vom Typ `location` mit der passenden Berechtigung, jeder Fix wird sofort gespeichert, und nach einem Absturz wird die Fahrt fortgesetzt. **Die Navigation allein ist es nicht.** Ferrostar läuft im ViewModel der Activity, und der Ferrostar-Vordergrunddienst ist bewusst ausgeschaltet (`foregroundServiceManager = null`). Ein Vordergrunddienst läuft deshalb nur, wenn in den Einstellungen „Aufzeichnen“ an ist. Ist die Aufzeichnung aus, bekommt die App nach Bildschirm aus oder App-Wechsel nur noch Hintergrund-Standort, also wenige Fixes pro Stunde. Prozess und TTS können dann eingefroren oder beendet werden.

## Befunde

| # | Schwere | Befund | Quelle |
|---|---|---|---|
| H1 | **hoch** | Navigation ohne Aufzeichnung hatte keinen Vordergrunddienst. Im Hintergrund kamen kaum Standort-Updates, die Ansagen fielen aus und der Prozess konnte beendet werden. **Umgesetzt** (E1, s. unten). | [Background location limits](https://developer.android.com/about/versions/oreo/background-location-limits), [FGS-Typen: location](https://developer.android.com/develop/background-work/services/fgs/service-types#location) |
| M1 | mittel | Nach Prozesstod ist die Navigation weg: Route und Fortschritt stehen nur im ViewModel. Die Aufzeichnung wird dagegen fortgesetzt. **Offen**: Das Wiederherstellen (Route, Einstieg, Ferrostar-Zustand) ist nicht risikoarm. Durch den Vordergrunddienst ist ein Prozesstod während der Fahrt jetzt aber deutlich seltener. | [Saved state / process death](https://developer.android.com/topic/libraries/architecture/saving-states) |
| M2 | mittel | `onStartCommand` lief nach einem fehlgeschlagenen `startForeground` weiter (Standort-Abo ohne Vordergrund, START_STICKY). **Behoben** (F1). | [Restrictions bg start / while-in-use](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) |
| M3 | mittel | Schreibreihenfolge der Fixes: Jeder Fix startete eine eigene Coroutine auf dem IO-Pool, die Reihenfolge war also nicht garantiert. **Behoben** (F2). | [kotlinx limitedParallelism](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html) |
| M4 | mittel | Korrektur: Der Bildschirm blieb schon an, allerdings auf dem ganzen Navi-Bildschirm (Ferrostar `KeepScreenOnDisposableEffect`), also auch vor dem Start und ohne Einstellung. **Umgesetzt** (E2): Er bleibt jetzt nur im Fahrmodus an, abschaltbar unter Einstellungen → Karte & Ansicht → „Bildschirm anlassen“, standardmäßig an. | [Keep the device awake](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on) |
| N1 | niedrig | Beschleunigung/Gyro mit 50 Hz ohne WakeLock: Bei Bildschirm aus schläft die CPU zwischen den GPS-Fixes, und G-Werte können lückenhaft sein. Die GPS-Spur ist davon nicht betroffen. → E3 | [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview), [WakeLocks](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock) |
| N2 | niedrig | Ein GPS-Verlust (Tunnel) wird nicht angezeigt und die Spur wird nicht in Segmente geteilt. Sprünge über 500 m werden bei der Distanz ignoriert, das ist ausreichend. | [Request location updates](https://developer.android.com/develop/sensors-and-location/location/request-updates) |
| N3 | niedrig | Kleines Benachrichtigungs-Icon ist ein System-Drawable (`ic_menu_mylocation`). Empfohlen ist ein eigenes, einfarbiges Icon. | [Notifications](https://developer.android.com/develop/ui/views/notifications) |
| N4 | niedrig | R8 ist aus (`isMinifyEnabled = false`). Das ist funktional sicher, das APK ist aber größer. Ein Umstieg bräuchte Keep-Regeln für JNA/uniffi (Ferrostar) und MapLibre. | [Shrink your app](https://developer.android.com/build/shrink-code) |
| N5 | niedrig | Pause/Fortsetzen aus der Benachrichtigung startet den Dienst per PendingIntent. Das ist laut Doku eine Ausnahme und deshalb erlaubt. | s. M2 |

## In Ordnung (geprüft)
- **Manifest:** `FOREGROUND_SERVICE` und `FOREGROUND_SERVICE_LOCATION`, Dienst `foregroundServiceType="location"`, `exported=false`. Das entspricht den Android-14-Pflichten. [FGS-Typen](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- **Kein `mediaPlayback` nötig:** TTS läuft im selben Prozess, solange der location-Dienst aktiv ist. Ein zweiter Typ wäre nicht begründbar (Play-Richtlinie).
- **Start des Dienstes:** nur aus sichtbarer UI. Den Neustart nach einem Kill übernimmt das System, laut Doku eine Ausnahme ("A system component starts the service").
- **location-Typ ohne Zeitlimit:** Die Android-15-Timeouts gelten nur für `dataSync` und `mediaProcessing`. [Android 15 FGS](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-changes)
- **Benachrichtigung:** Kanal mit IMPORTANCE_LOW, ongoing, `FOREGROUND_SERVICE_IMMEDIATE`, Pause- und Stopp-Aktion. POST_NOTIFICATIONS wird ab API 33 angefragt. Ohne diese Berechtigung läuft der Dienst trotzdem.
- **Berechtigungen:** fine und coarse gemeinsam, kein Hintergrund-Standort. Für eine vom Nutzer gestartete Fahrt mit Vordergrunddienst ist das korrekt. [Request location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions)
- **FusedLocation:** HIGH_ACCURACY, 1 s, kein Batching. Für Navigation und Spur ist das richtig. Batching würde die Live-Anzeige verzögern.
- **Doze:** Ein Vordergrunddienst mit Standort ist von Doze nicht betroffen. Die Akku-Ausnahme wird nur über die Einstellungsliste angeboten, kein Direktdialog (Play-konform). [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)
- **Persistenz:** jeder Fix sofort in der DB, Zustand `rec`/Pause gespeichert, `resumeFrom` nach Kill sowie Dialog für verwaiste Fahrten.
- **Rotation:** Das ViewModel überlebt Konfigurationswechsel. Der Dienst ist davon unabhängig.
- **TTS:** `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, Fokus `GAIN_TRANSIENT_MAY_DUCK` und Freigabe danach. Das entspricht der Doku. [Audio focus](https://developer.android.com/media/optimize/audio-focus)
- **Exakte Alarme:** keine. **WakeLock:** keiner, was beim location-Dienst richtig ist.
- **targetSdk 36 / compileSdk 37:** Damit ist die Play-Pflicht ab 31.08.2026 erfüllt. [Play target API](https://support.google.com/googleplay/android-developer/answer/11926878)

## Herstellerhinweis (nur Info)
Samsung, Xiaomi, Huawei und OnePlus beenden Vordergrunddienste teils trotzdem. Siehe https://dontkillmyapp.com. Für Nutzer: In den Akku-Einstellungen „Nicht optimiert“ bzw. „Keine Einschränkung“ wählen.

## Behoben
- **F1:** `startForegroundNow()` liefert jetzt Erfolg oder Misserfolg. Schlägt der Start fehl, endet `onStartCommand` mit `START_NOT_STICKY` und es gibt kein Standort-Abo ohne Vordergrunddienst. Die unterbrochene Fahrt bleibt erhalten, und beim nächsten Öffnen der App erscheint der Fortsetzen-Dialog.
- **F2:** Fixes werden über `Dispatchers.IO.limitedParallelism(1)` geschrieben, also in Ankunftsreihenfolge.
- **Test:** `:androidApp:testDebugUnitTest` mit 141 Tests, 0 Fehler. `assembleDebug` und `checkNoMaterialIcons` grün.
- **Emulator-Doze-Test:** nicht möglich, weil die Box keinen KVM-Zugriff hat.

## Umsetzung 30.10.2026 (freigegeben von Stefan, 12:33)
- **E1 – Modus „nur Navigation“ im `RecordingService`:**
  - `ServiceMode.of(recording, navigating)`: REC hat Vorrang und deckt die Navigation mit ab, so läuft nie ein Doppeldienst. NAV hält nur den Vordergrunddienst (Typ location). Ferrostars Fused-Provider und die TTS laufen im selben Prozess weiter, gespeichert wird nichts.
  - Eigener Kanal „Navigation“, Benachrichtigung „Navigation aktiv“ mit der Aktion „Beenden“. Sie stoppt die Zielführung in der App, danach beendet sich der Dienst.
  - Start und Ende hängen am Fahrmodus (Zielführung oder In-App-Anfahrt), an `vm.stop()` und an `onCleared`. Wird eine Aufzeichnung gespeichert, während die Navigation weiterläuft, wechselt der Dienst zu NAV (GPS/Sensoren der Aufzeichnung aus).
  - Im NAV-Modus gilt `START_NOT_STICKY`, nur REC ist sticky. Beobachtet wird erst nach dem ersten `startForeground`.
- **E2 – Bildschirm anlassen:** nur im Fahrmodus. Einstellung `keepScreenOn` in NavPrefs, Standard an, Schalter unter „Karte & Ansicht“.
- **Tests:** `NavServiceModeTest` (Robolectric, 5 Tests) prüft die Moduslogik ohne Doppeldienst, dass NAV die Benachrichtigung „Navigation aktiv/Beenden“ zeigt und nichts speichert, das Selbst-Stoppen bei Navigationsende, die Aktion „Beenden“, den Sofortstopp ohne Aufgabe sowie Standard und Speicherung der Einstellung samt Schalter.
- **Ergebnisse:** androidApp 146 Tests, shared (jvm + androidHost) 148 Tests, alle 0 Fehler. `checkNoMaterialIcons`, `assembleDebug` und `assembleRelease` grün. Kein Release.

## Entscheidungsfragen (Stand: E1/E2 ja und umgesetzt, E3/E4/E5 nein)
- **E1 (Empfehlung: ja):** Während der Navigation immer einen Vordergrunddienst starten, auch wenn „Aufzeichnen“ aus ist. Am einfachsten über RecordingService mit einem Modus „nur Navigation“, der nicht speichert. Alternativ den Ferrostar-`foregroundServiceManager` aktivieren.
- **E2 (Empfehlung: ja, als Einstellung, Standard an):** Bildschirm während aktiver Navigation anlassen.
- **E3 (Empfehlung: nein):** Partial-WakeLock nur für G-Werte. Das kostet Akku und lohnt sich nur, wenn die G-Statistik wichtig ist.
- **E4 (Empfehlung: nein):** `ACCESS_BACKGROUND_LOCATION` ist mit Vordergrunddienst unnötig und hätte eine Play-Prüfung zur Folge.
- **E5 (Empfehlung: Hinweis im Startcheck reicht):** Direktdialog `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Ist nach der Play-Richtlinie riskant.

## Nur am echten Gerät prüfbar
Mehrstündige Fahrt mit Bildschirm aus (Samsung/Xiaomi), Tunnel-Verhalten, TTS bei laufender Musik oder Bluetooth, Akkuverbrauch pro Stunde, Kill durch den Hersteller und das Fortsetzen danach, Doze mit `adb shell dumpsys deviceidle force-idle` während der Aufzeichnung.
