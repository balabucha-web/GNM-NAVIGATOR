package de.balabucha.reisepilot

import kotlin.math.*

data class RouteProjection(
    val distanceFromRouteM: Double,
    val distanceAlongRouteM: Double,
    val remainingRouteM: Double,
    val segmentIndex: Int
)

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

    fun routeLengthM(line: List<GeoPoint>): Double =
        line.zipWithNext().sumOf { (a, b) -> distanceM(a, b) }

    fun projectOnPolyline(point: GeoPoint, line: List<GeoPoint>): RouteProjection? {
        if (line.isEmpty()) return null
        if (line.size == 1) {
            return RouteProjection(distanceM(point, line.first()), 0.0, 0.0, 0)
        }

        val total = routeLengthM(line)
        var accumulated = 0.0
        var best: RouteProjection? = null

        for (index in 0 until line.lastIndex) {
            val a = line[index]
            val b = line[index + 1]
            val segmentLength = distanceM(a, b)
            if (segmentLength < 0.01) continue

            val meanLat = Math.toRadians((a.lat + b.lat + point.lat) / 3.0)
            fun x(p: GeoPoint) = Math.toRadians(p.lon - a.lon) * cos(meanLat) * R
            fun y(p: GeoPoint) = Math.toRadians(p.lat - a.lat) * R

            val bx = x(b)
            val by = y(b)
            val px = x(point)
            val py = y(point)
            val denominator = bx * bx + by * by
            val t = if (denominator <= 0.0) 0.0 else ((px * bx + py * by) / denominator).coerceIn(0.0, 1.0)
            val dx = px - bx * t
            val dy = py - by * t
            val distance = hypot(dx, dy)
            val along = accumulated + segmentLength * t
            val candidate = RouteProjection(
                distanceFromRouteM = distance,
                distanceAlongRouteM = along,
                remainingRouteM = (total - along).coerceAtLeast(0.0),
                segmentIndex = index
            )
            if (best == null || candidate.distanceFromRouteM < best.distanceFromRouteM) best = candidate
            accumulated += segmentLength
        }
        return best
    }

    fun closestToPolylineM(point: GeoPoint, line: List<GeoPoint>): Double =
        projectOnPolyline(point, line)?.distanceFromRouteM ?: Double.MAX_VALUE

    /**
     * Remaining route distance from current position to a target projected on the same route.
     * Returns null when the target is behind the current route progress or too far away from the route.
     */
    fun distanceAheadOnRouteM(
        current: GeoPoint,
        target: GeoPoint,
        line: List<GeoPoint>,
        maxCurrentOffsetM: Double = 5_000.0,
        maxTargetOffsetM: Double = 5_000.0,
        includeTargetOffset: Boolean = true
    ): Double? {
        if (line.size < 2) return null
        val currentProjection = projectOnPolyline(current, line) ?: return null
        val targetProjection = projectOnPolyline(target, line) ?: return null
        if (currentProjection.distanceFromRouteM > maxCurrentOffsetM) return null
        if (targetProjection.distanceFromRouteM > maxTargetOffsetM) return null
        val forward = targetProjection.distanceAlongRouteM - currentProjection.distanceAlongRouteM
        if (forward < -250.0) return null
        return forward.coerceAtLeast(0.0) + if (includeTargetOffset) targetProjection.distanceFromRouteM else 0.0
    }
}
