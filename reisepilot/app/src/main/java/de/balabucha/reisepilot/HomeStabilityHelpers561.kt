package de.balabucha.reisepilot

import kotlin.math.ceil

internal fun travelTime55(distanceKm: Double, speedKmh: Int?): String {
    val effectiveSpeed = (speedKmh ?: 100).coerceIn(30, 130)
    val minutes = ceil(distanceKm / effectiveSpeed.toDouble() * 60.0).toInt().coerceAtLeast(1)
    return if (minutes < 60) "ca. $minutes Min." else "ca. ${minutes / 60} Std. ${minutes % 60} Min."
}

internal fun featureText55(stop: RoadsideStop54): String {
    val features = buildList {
        if (stop.hasToilets) add("WC")
        if (stop.hasFood) add("Essen")
        if (stop.hasFuel) add("Tanken")
    }
    return if (features.isEmpty()) "" else " · ${features.joinToString(" · ")}"
}
