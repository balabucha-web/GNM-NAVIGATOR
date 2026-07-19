package de.balabucha.reisepilot

/**
 * The active tracking service reloads the cleaned token on its next route refresh.
 * Kept as a UI compatibility hook; it intentionally does not reset the driving timer.
 */
fun MainActivity.reloadTripConfig() = Unit
