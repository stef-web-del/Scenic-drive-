package de.scenicroutes.shared

import de.scenicroutes.shared.nav.Sun
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SunTest {
    private val lat = 49.703; private val lon = 9.255 // Miltenberg
    private fun ms(s: String) = Instant.parse(s).toEpochMilli()

    @Test fun sommerMittagHell() = assertFalse(Sun.isDark(lat, lon, ms("2026-06-21T11:00:00Z")))
    @Test fun sommerNachtDunkel() = assertTrue(Sun.isDark(lat, lon, ms("2026-06-21T22:00:00Z")))
    // Sonnenuntergang Miltenberg 21.12. etwa 16:25 MEZ (15:25 UTC), Aufgang etwa 8:15 MEZ.
    @Test fun winterAbendDunkel() = assertTrue(Sun.isDark(lat, lon, ms("2026-12-21T15:45:00Z")))
    @Test fun winterNachmittagHell() = assertFalse(Sun.isDark(lat, lon, ms("2026-12-21T14:45:00Z")))
    @Test fun winterMorgenDunkel() = assertTrue(Sun.isDark(lat, lon, ms("2026-12-21T06:45:00Z")))
    @Test fun winterMorgenHell() = assertFalse(Sun.isDark(lat, lon, ms("2026-12-21T07:45:00Z")))
}
