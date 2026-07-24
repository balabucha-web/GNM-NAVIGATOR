package de.balabucha.reisepilot

import org.json.JSONObject

internal object RouteAheadTools54 {
    fun parseRoute(raw: String): List<GeoPoint> = runCatching {
        if (raw.isBlank()) return emptyList()
        val coordinates = JSONObject(raw).getJSONArray("coordinates")
        buildList {
            for (index in 0 until coordinates.length()) {
                val coordinate = coordinates.getJSONArray(index)
                add(GeoPoint(coordinate.getDouble(1), coordinate.getDouble(0)))
            }
        }
    }.getOrDefault(emptyList())

    fun pointAhead(current: GeoPoint, route: List<GeoPoint>, distanceM: Double): GeoPoint? {
        if (route.size < 2) return null
        val projection = Geo.projectOnPolyline(current, route) ?: return null
        val wanted = projection.distanceAlongRouteM + distanceM.coerceAtLeast(0.0)
        var accumulated = 0.0
        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            val segment = Geo.distanceM(a, b)
            if (segment <= 0.01) continue
            val next = accumulated + segment
            if (wanted <= next) {
                val fraction = ((wanted - accumulated) / segment).coerceIn(0.0, 1.0)
                return GeoPoint(
                    lat = a.lat + (b.lat - a.lat) * fraction,
                    lon = a.lon + (b.lon - a.lon) * fraction
                )
            }
            accumulated = next
        }
        return route.last()
    }

    fun sampleAhead(
        current: GeoPoint,
        route: List<GeoPoint>,
        maxDistanceM: Double = 120_000.0,
        stepM: Double = 15_000.0
    ): List<GeoPoint> {
        if (route.size < 2) return listOf(current)
        val projection = Geo.projectOnPolyline(current, route) ?: return listOf(current)
        val available = projection.remainingRouteM.coerceAtMost(maxDistanceM)
        val samples = mutableListOf(current)
        var distance = stepM
        while (distance <= available && samples.size < 10) {
            pointAhead(current, route, distance)?.let(samples::add)
            distance += stepM
        }
        pointAhead(current, route, available)?.let { last ->
            if (samples.none { Geo.distanceM(it, last) < 2_000.0 }) samples += last
        }
        return samples.distinctBy { "%.4f,%.4f".format(java.util.Locale.US, it.lat, it.lon) }
    }

    fun forwardDistanceKm(current: GeoPoint, target: GeoPoint, route: List<GeoPoint>, maxOffsetM: Double): Double? =
        Geo.distanceAheadOnRouteM(
            current = current,
            target = target,
            line = route,
            maxCurrentOffsetM = 8_000.0,
            maxTargetOffsetM = maxOffsetM,
            includeTargetOffset = true
        )?.div(1_000.0)
}
