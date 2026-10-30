package de.scenicroutes.app.ui

// GENERIERT aus design/tokens.json (app/tools/gen_tokens.py) – nicht von Hand ändern.
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Tokens {
    object Light {
        val primary = Color(0xFF8A5A14)
        val onPrimary = Color(0xFFF5F6FA)
        val primaryContainer = Color(0xFFFBEBD3)
        val onPrimaryContainer = Color(0xFF5E3D0C)
        val accent = Color(0xFFEFC07B)
        val onAccent = Color(0xFF1A1A2E)
        val accentContainer = Color(0xFFFBEBD3)
        val background = Color(0xFFF5F6FA)
        val surface = Color(0xFFF5F6FA)
        val surfaceContainer = Color(0xFFECEFF5)
        val surfaceContainerHigh = Color(0xFFDDE3EF)
        val onSurface = Color(0xFF1A1A2E)
        val onSurfaceVariant = Color(0xFF4A5370)
        val outline = Color(0xFF757D94)
        val outlineVariant = Color(0xFFA5ADC1)
        val error = Color(0xFFB3261E)
        val errorContainer = Color(0xFFF9DEDC)
        val onErrorContainer = Color(0xFF5C1612)
        val warning = Color(0xFF9A6400)
        val success = Color(0xFF2E6B4F)
        val record = Color(0xFFB3261E)
        val onRecord = Color(0xFFF5F6FA)
        val border = Color(0xFFA5ADC1)
    }
    object Dark {
        val primary = Color(0xFFEFC07B)
        val onPrimary = Color(0xFF1A1A2E)
        val primaryContainer = Color(0xFF0F3460)
        val onPrimaryContainer = Color(0xFFF5D3A0)
        val accent = Color(0xFFEFC07B)
        val onAccent = Color(0xFF1A1A2E)
        val accentContainer = Color(0xFF0F3460)
        val background = Color(0xFF1A1A2E)
        val surface = Color(0xFF16213E)
        val surfaceContainer = Color(0xFF16213E)
        val surfaceContainerHigh = Color(0xFF0F3460)
        val onSurface = Color(0xFFECEEF4)
        val onSurfaceVariant = Color(0xFFB8C1D6)
        val outline = Color(0xFF7F8BA8)
        val outlineVariant = Color(0xFF3A5F96)
        val error = Color(0xFFF2B8B5)
        val errorContainer = Color(0xFF5C1612)
        val onErrorContainer = Color(0xFFF2B8B5)
        val warning = Color(0xFFF0C470)
        val success = Color(0xFF8FD1B0)
        val record = Color(0xFFF2B8B5)
        val onRecord = Color(0xFF1A1A2E)
        val border = Color(0xFF3A5F96)
    }
    object MapLight {
        val land = Color(0xFFF5F6FA)
        val water = Color(0xFFC9D6EA)
        val forest = Color(0xFFD3DED6)
        val road = Color(0xFFFFFFFF)
        val roadMajor = Color(0xFFE0E3EC)
        val label = Color(0xFF4A5370)
        val routeActive = Color(0xFF9C6618)
        val routeCasing = Color(0xFF1A1A2E)
        val routeAlt = Color(0xFF757D94)
        val approach = Color(0xFF0F3460)
    }
    object MapDark {
        val land = Color(0xFF1A1A2E)
        val water = Color(0xFF0F3460)
        val forest = Color(0xFF1D2F3A)
        val road = Color(0xFF2E3756)
        val roadMajor = Color(0xFF414C70)
        val label = Color(0xFFB8C1D6)
        val routeActive = Color(0xFFEFC07B)
        val routeCasing = Color(0xFF0B0B16)
        val routeAlt = Color(0xFF7F8BA8)
        val approach = Color(0xFFECEEF4)
    }
    /** Score- und Heatmap-Skala Blau → Gold (farbenblindsicher). */
    val scoreStops = listOf(0.15 to Color(0xFF3E5F9A), 0.18 to Color(0xFF7A8DB0), 0.21 to Color(0xFFC9A46E), 0.24 to Color(0xFFEFC07B))
    val speedStops = listOf(Color(0xFF3E5F9A), Color(0xFF7A8DB0), Color(0xFFC9A46E), Color(0xFFEFC07B), Color(0xFFB3261E))
    object Type {
        val display = Triple(56.sp, 64.sp, 600)
        val headline = Triple(24.sp, 32.sp, 600)
        val title = Triple(18.sp, 24.sp, 600)
        val body = Triple(15.sp, 22.sp, 400)
        val label = Triple(13.sp, 18.sp, 500)
        val caption = Triple(12.sp, 16.sp, 400)
    }
    val touchMin = 48.dp; val touchDrive = 64.dp
    val driveTurn = 72.sp; val driveDist = 56.sp; val driveEta = 24.sp
    val driveKpi = 40.sp; val driveKpiSecondary = 22.sp; val driveProgress = 4.dp
    val routeWidth = 6.dp; val routeCasing = 2.dp; val altWidth = 4.dp
    val radiusSm = 8.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 28.dp
    val radiusMap = 20.dp
    val spaceNone = 0.dp
    val spaceXxs = 2.dp
    val spaceXs = 4.dp
    val spaceXsm = 6.dp
    val spaceSm = 8.dp
    val spaceSmd = 10.dp
    val spaceMd = 12.dp
    val spaceMdl = 14.dp
    val spaceLg = 16.dp
    val spaceLgx = 18.dp
    val spaceXl = 20.dp
    val spaceXxl = 24.dp
    val spaceXxxl = 32.dp
    val strokeHair = 1.dp
    val strokeFine = 1.5.dp
    val strokeThin = 2.dp
    val strokeMedium = 2.5.dp
    val strokeBold = 3.dp
    val sizeDotXs = 2.dp
    val sizeDotInner = 3.5.dp
    val sizeDotSm = 4.dp
    val sizeDotMd = 5.dp
    val sizeIndicator = 10.dp
    val sizeIndicatorMd = 12.dp
    val sizeIndicatorLg = 14.dp
    val sizeMapLine = 5.dp
    val sizeMapLineSel = 7.dp
    val sizeMapLineCasing = 8.dp
    val sizeMapLineSelCasing = 12.dp
    val sizeMapPointSel = 7.dp
    val sizeMapPoint = 8.dp
    val sizeMapPointPick = 9.dp
    val sizeIconXs = 16.dp
    val sizeIconSm = 18.dp
    val sizeIconMd = 20.dp
    val sizeIconLg = 24.dp
    val sizeIconXl = 48.dp
    val sizeIconHero = 56.dp
    val sizeValueCol = 56.dp
    val sizeThumbSm = 72.dp
    val sizeThumb = 84.dp
    val sizeRecStart = 72.dp
    val sizeRecButton = 80.dp
    val sizeChartSm = 90.dp
    val sizeListEnd = 96.dp
    val sizeChartMd = 120.dp
    val sizeHudClearance = 120.dp
    val sizeLabelCol = 132.dp
    val sizeMiniMap = 150.dp
    val sizeMoreCardW = 150.dp
    val sizeCardMinH = 170.dp
    val sizeMapSm = 200.dp
    val sizeCardW = 230.dp
    val sizeMapPicker = 240.dp
    val sizeChartLg = 260.dp
    val sizeMapMd = 280.dp
    val sizeMapLg = 300.dp
    val sizeListEndMap = 168.dp
    object Terms {
        const val go = "Losfahren"
        const val offline = "Offline speichern"
        const val why = "Warum diese Runde?"
        const val best = "Beste Abschnitte"
        const val segColor = "Karte nach Abschnitt färben"
        const val profile = "Höhenprofil"
        const val pauses = "Pausenorte"
        const val places = "Orte"
        const val length = "Länge"
        const val time = "Fahrzeit"
        const val climb = "Anstieg"
        const val score = "Score"
        const val tour = "Tour-Score"
        const val region = "Region"
        const val barsNote = "Balken = besser als … % aller {n} Runden"
        const val closed = "Gesperrt"
        const val update = "Update verfügbar"
        const val entry = "Einstieg"
        const val entryHere = "Ab meinem Standort einsteigen"
        const val entryServer = "Anfahrt berechnen"
        const val entryGoogle = "Anfahrt mit Google Maps"
        const val entryGuide = "Zum Einstieg ohne Navigation"
        const val entryOfficial = "Am offiziellen Start beginnen"
        const val entryStart = "Runde ab Einstieg starten"
    }
    val detailOrder = listOf("header", "stats", "strengths", "tags", "text", "actions", "why", "profile", "pauses", "places", "family")
}
