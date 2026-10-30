package de.scenicroutes.shared.nav

import kotlin.math.*

/**
 * Sonnenstand für den Fahrmodus: hell/dunkel nach Sonnenauf- und -untergang am Standort.
 * Formel nach NOAA (vereinfacht, Genauigkeit ein bis zwei Minuten). Als „dunkel“ gilt die Zeit,
 * in der die Sonne mehr als 0,833° unter dem Horizont steht, also nach Sonnenuntergang.
 */
object Sun {
    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI

    /** Sonnenhöhe in Grad über dem Horizont. */
    fun elevation(lat: Double, lon: Double, epochMs: Long): Double {
        val jd = epochMs / 86_400_000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(125.04 - 1934.136 * t))
        val eps = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0 + 0.00256 * cos(rad(125.04 - 1934.136 * t))
        val decl = asin(sin(rad(eps)) * sin(rad(lambda)))
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val y = tan(rad(eps / 2)).pow(2)
        val eqTime = 4 * deg(y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) - 0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)))
        val minutesUtc = (epochMs.mod(86_400_000L)) / 60_000.0
        val tst = (minutesUtc + eqTime + 4 * lon).mod(1440.0)
        val ha = rad(tst / 4 - 180)
        val cosZ = sin(rad(lat)) * sin(decl) + cos(rad(lat)) * cos(decl) * cos(ha)
        return 90 - deg(acos(cosZ.coerceIn(-1.0, 1.0)))
    }

    fun isDark(lat: Double, lon: Double, epochMs: Long) = elevation(lat, lon, epochMs) < -0.833
}
