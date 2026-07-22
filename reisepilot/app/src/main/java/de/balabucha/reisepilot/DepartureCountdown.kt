package de.balabucha.reisepilot

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal val VACATION_TIME_ZONE: ZoneId = ZoneId.of("Europe/Berlin")

internal val VACATION_DEPARTURE: ZonedDateTime = ZonedDateTime.of(
    2026,
    7,
    25,
    9,
    0,
    0,
    0,
    VACATION_TIME_ZONE
)

internal data class DepartureCountdown(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val started: Boolean
)

internal fun tripModeAt(now: Instant): TripMode =
    if (now.isBefore(VACATION_DEPARTURE.toInstant())) TripMode.TEST else TripMode.REAL

internal fun departureCountdown(now: Instant): DepartureCountdown {
    val remainingMillis = Duration.between(now, VACATION_DEPARTURE.toInstant()).toMillis()
    if (remainingMillis <= 0L) {
        return DepartureCountdown(
            days = 0,
            hours = 0,
            minutes = 0,
            seconds = 0,
            started = true
        )
    }

    // Round up so the display does not jump to zero while part of the last second remains.
    val totalSeconds = (remainingMillis + 999L) / 1_000L
    val days = totalSeconds / 86_400L
    val hours = totalSeconds % 86_400L / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return DepartureCountdown(
        days = days.toInt(),
        hours = hours.toInt(),
        minutes = minutes.toInt(),
        seconds = seconds.toInt(),
        started = false
    )
}
