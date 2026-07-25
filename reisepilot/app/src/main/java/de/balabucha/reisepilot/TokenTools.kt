package de.balabucha.reisepilot

/** Removes clipboard formatting, line breaks and invisible characters from a Mapbox public token. */
fun normalizeMapboxToken(raw: String): String {
    val source = raw.trim().removePrefix("Bearer ").trim()
    val start = source.indexOf("pk.")
    val candidate = if (start >= 0) source.substring(start) else source
    return candidate.filter { ch ->
        ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_'
    }
}

fun mapboxTokenLooksValid(raw: String): Boolean {
    val token = normalizeMapboxToken(raw)
    return token.startsWith("pk.") && token.length >= 80 && token.count { it == '.' } >= 2
}

fun mapboxTokenSummary(raw: String): String {
    val token = normalizeMapboxToken(raw)
    if (token.isBlank()) return "Kein Token gespeichert"
    val ending = token.takeLast(6)
    return "${token.length} Zeichen · endet auf …$ending"
}
