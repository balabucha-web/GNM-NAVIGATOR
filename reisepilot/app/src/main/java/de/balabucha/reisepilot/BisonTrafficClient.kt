package de.balabucha.reisepilot

import android.text.Html
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class BisonEvent(
    val severity: Int,
    val road: String,
    val title: String,
    val detail: String
)

internal data class BisonTrafficSummary(
    val updated: String = "",
    val events: List<BisonEvent> = emptyList(),
    val error: String? = null
)

/**
 * Reads the official Bison Futé open-data traffic summary and reduces it to the
 * French motorways used by the configured trip. Standard event phrases are
 * translated to German so the user never needs the French Bison Futé UI.
 */
internal object BisonTrafficClient {
    private const val ENDPOINT =
        "https://tipi.bison-fute.gouv.fr/bison-fute-ouvert/publicationsDIR/Evenementiel-DIR/cnir/RecapTraficFranceEntiere.html"

    fun load(stage: Stage): BisonTrafficSummary = runCatching {
        val html = http(ENDPOINT)
        val plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        val roads = when (stage) {
            Stage.SATURDAY -> setOf("A36")
            Stage.SUNDAY -> setOf("A36", "A6", "A7", "A9")
        }
        val updated = Regex("du\\s+([^\\n]+)", RegexOption.IGNORE_CASE)
            .find(plain)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val events = plain.lineSequence()
            .map(String::trim)
            .filter { it.length >= 20 }
            .mapNotNull { line -> parseEvent(line, roads) }
            .distinctBy { "${it.road}:${it.title}:${it.detail.take(90)}" }
            .sortedWith(compareByDescending<BisonEvent> { it.severity }.thenBy { it.road })
            .take(6)
            .toList()

        BisonTrafficSummary(updated = updated, events = events)
    }.getOrElse { error ->
        BisonTrafficSummary(error = error.message?.take(140) ?: "Bison-Futé-Daten nicht erreichbar")
    }

    private fun parseEvent(line: String, roads: Set<String>): BisonEvent? {
        val road = Regex("\\bA(?:36|6|7|9)\\b").find(line)?.value ?: return null
        if (road !in roads) return null
        val lower = line.lowercase()
        val title = when {
            "route fermée" in lower || "route coupée" in lower -> "Straße gesperrt"
            "accident" in lower -> "Unfall"
            "bouchon" in lower || "embouteillage" in lower -> "Stau"
            "réduction du nombre de voies" in lower || "voie neutralisée" in lower -> "Fahrstreifen reduziert"
            "véhicule en panne" in lower -> "Pannenfahrzeug"
            "obstacle" in lower -> "Hindernis auf der Fahrbahn"
            "travaux" in lower || "chantier" in lower -> "Baustelle"
            "sortie fermée" in lower -> "Ausfahrt gesperrt"
            "entrée fermée" in lower -> "Auffahrt gesperrt"
            "aire de service fermée" in lower -> "Raststätte geschlossen"
            "limitation de vitesse" in lower -> "Tempolimit"
            else -> return null
        }
        val severity = line.takeWhile { it == '*' }.length.coerceIn(1, 3)
        return BisonEvent(severity, road, title, germanCompact(line, road, title))
    }

    private fun germanCompact(raw: String, road: String, title: String): String {
        var text = raw
            .replace(Regex("^\\*+\\s*"), "")
            .replace(Regex("\\s*\\[Origine[^]]*]", RegexOption.IGNORE_CASE), "")
            .replace("Route fermée", "Straße gesperrt", ignoreCase = true)
            .replace("Route coupée", "Straße vollständig gesperrt", ignoreCase = true)
            .replace("Réduction du nombre de voies", "Fahrstreifen reduziert", ignoreCase = true)
            .replace("Véhicule en panne", "Pannenfahrzeug", ignoreCase = true)
            .replace("Événement signalé", "gemeldetes Ereignis", ignoreCase = true)
            .replace("Obstacle", "Hindernis", ignoreCase = true)
            .replace("Sortie fermée", "Ausfahrt gesperrt", ignoreCase = true)
            .replace("Entrée fermée", "Auffahrt gesperrt", ignoreCase = true)
            .replace("Aire de service fermée", "Raststätte geschlossen", ignoreCase = true)
            .replace("Limitation de vitesse", "Tempolimit", ignoreCase = true)
            .replace("applicable à tous les véhicules", "für alle Fahrzeuge", ignoreCase = true)
            .replace("Sur voie de droite", "auf der rechten Spur", ignoreCase = true)
            .replace("Sur voie de gauche", "auf der linken Spur", ignoreCase = true)
            .replace("Sur toutes les voies", "auf allen Spuren", ignoreCase = true)
            .replace("La mesure est obligatoire", "", ignoreCase = true)
            .replace("sens nord-sud", "Richtung Süden", ignoreCase = true)
            .replace("sens sud-nord", "Richtung Norden", ignoreCase = true)
            .replace("sens est-ouest", "Richtung Westen", ignoreCase = true)
            .replace("sens ouest-est", "Richtung Osten", ignoreCase = true)
            .replace(Regex("\\s+"), " ")
            .trim(' ', ';')

        if (text.startsWith(title, ignoreCase = true)) text = text.substring(title.length).trim(' ', ',', ';')
        if (!text.contains(road)) text = "$road · $text"
        return text.take(210)
    }

    private fun http(url: String): String {
        var lastFailure = "Bison Futé nicht erreichbar"
        repeat(3) { attempt ->
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 18_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Language", "fr,de;q=0.8")
                connection.setRequestProperty("User-Agent", "ReisePilot/4.9 Android family travel app")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299 && body.isNotBlank()) return body
                lastFailure = "Bison Futé HTTP $code"
                if (code !in setOf(429, 500, 502, 503, 504) || attempt == 2) error(lastFailure)
                Thread.sleep(700L * (attempt + 1))
            } catch (error: IOException) {
                lastFailure = "Bison Futé: ${error.message ?: error.javaClass.simpleName}"
                if (attempt == 2) throw error
                Thread.sleep(700L * (attempt + 1))
            } finally {
                connection.disconnect()
            }
        }
        error(lastFailure)
    }
}
