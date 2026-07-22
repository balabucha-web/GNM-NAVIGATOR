package de.balabucha.reisepilot

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureCountdownTest {
    @Test
    fun splitsRemainingTimeIntoStableCountdownUnits() {
        val now = VACATION_DEPARTURE.toInstant().minus(
            Duration.ofDays(2)
                .plusHours(3)
                .plusMinutes(4)
                .plusSeconds(5)
        )

        val countdown = departureCountdown(now)

        assertFalse(countdown.started)
        assertEquals(2, countdown.days)
        assertEquals(3, countdown.hours)
        assertEquals(4, countdown.minutes)
        assertEquals(5, countdown.seconds)
        assertEquals(2, countdown.sleeps)
    }

    @Test
    fun roundsUpTheFinalPartialSecond() {
        val countdown = departureCountdown(VACATION_DEPARTURE.toInstant().minusMillis(250))

        assertFalse(countdown.started)
        assertEquals(1, countdown.seconds)
    }

    @Test
    fun switchesToCelebrationExactlyAtDeparture() {
        val countdown = departureCountdown(VACATION_DEPARTURE.toInstant())

        assertTrue(countdown.started)
        assertEquals(0, countdown.days)
        assertEquals(0, countdown.hours)
        assertEquals(0, countdown.minutes)
        assertEquals(0, countdown.seconds)
        assertEquals(0, countdown.sleeps)
    }
}
