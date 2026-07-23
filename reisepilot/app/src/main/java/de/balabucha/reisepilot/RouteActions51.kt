package de.balabucha.reisepilot

import android.content.Intent
import android.net.Uri

fun MainActivity.openJourneyRoute51(origin: String, destination: String) {
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1" +
            "&origin=${Uri.encode(origin)}" +
            "&destination=${Uri.encode(destination)}" +
            "&travelmode=driving"
    )
    startActivity(Intent(Intent.ACTION_VIEW, uri))
}
