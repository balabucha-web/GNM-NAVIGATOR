package de.balabucha.reisepilot

import kotlin.math.*

object Geo {
    private const val R = 6_371_000.0

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = Math.toRadians(b.lat - a.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * R * asin(sqrt(h))
    }

    fun closestToPolylineM(point: GeoPoint, line: List<GeoPoint>): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        return line.minOf { distanceM(point, it) }
    }
}
