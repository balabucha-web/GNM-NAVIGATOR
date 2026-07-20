package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors

internal data class GermanTrafficEvent(
    val severity: Int,
    val road: String,
    val title: String,
    val detail: String
)

internal data class GermanTrafficSummary(
    val updated: String = "",
    val roads: List<String> = emptyList(),
    val events: List<GermanTrafficEvent> = emptyList(),
    val error: String? = null
)

/** Reads current warnings, closures and roadworks from the Autobahn App feed. */
internal object GermanTrafficClient {
    private const val BASE = "https://verkehr.autobahn.de/o/autobahn"
    private val saturdayRoads = listOf("A24", "A10", "A9", "A6", "A5")

    fun load(stage: Stage): GermanTrafficSummary {
        if (stage != Stage.SATURDAY) return GermanTrafficSummary(roads = emptyList())
        return runCatching {
            val executor = Executors.newFixedThreadPool(5)
            try {
                val tasks = saturdayRoads.flatMap { road ->
                    listOf("warning", "closure", "roadworks").map { kind ->
                        Callable { fetch(road, kind) }
                    }
                }
                val events = executor.invokeAll(tasks)
                    .flatMap { runCatching { it.get() }.getOrDefault(emptyList()) }
                    .distinctBy { "${it.road}:${it.title}:${it.detail.take(100)}" }
                    .sortedWith(compareByDescending<GermanTrafficEvent> { it.severity }.thenBy { it.road })
                    .take(8)
                GermanTrafficSummary(
                    updated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")),
                    roads = saturdayRoads,
                    events = events
                )
            } finally {
                executor.shutdownNow()
            }
        }.getOrElse { error ->
            GermanTrafficSummary(
                roads = saturdayRoads,
                error = error.message?.take(150) ?: "Deutsche Verkehrsdaten nicht erreichbar"
            )
        }
    }

    private fun fetch(road: String, kind: String): List<GermanTrafficEvent> {
        val root = JSONObject(http("$BASE/$road/services/$kind"))
        val array = root.optJSONArray(kind) ?: root.optJSONArray(kind.removeSuffix("s")) ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val rawTitle = row.optString("title").trim()
                val description = text(row.opt("description"))
                val subtitle = text(row.opt("subtitle"))
                val combined = listOf(rawTitle, subtitle, description).filter(String::isNotBlank).joinToString(" · ")
                if (combined.isBlank()) continue
                val blocked = row.optString("isBlocked").equals("true", true) || row.optBoolean("isBlocked")
                val title = when {
                    kind == "closure" || blocked -> "Sperrung"
                    kind == "roadworks" -> "Baustelle"
                    combined.contains("Unfall", true) -> "Unfall"
                    combined.contains("Stau", true) -> "Stau"
                    combined.contains("Gefahr", true) -> "Gefahrenstelle"
                    combined.contains("Panne", true) -> "Pannenfahrzeug"
                    else -> "Verkehrsmeldung"
                }
                val severity = when {
                    kind == "closure" || blocked -> 3
                    title in setOf("Unfall", "Stau", "Gefahrenstelle") -> 2
                    else -> 1
                }
                add(GermanTrafficEvent(severity, road, title, compact(combined, road)))
            }
        }
    }

    private fun text(value: Any?): String = when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) value.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" · ")
        is String -> value.trim()
        else -> ""
    }

    private fun compact(raw: String, road: String): String {
        var text = raw.replace(Regex("\\s+"), " ").trim(' ', '·')
        if (!text.contains(road)) text = "$road · $text"
        return text.take(230)
    }

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "de")
            connection.setRequestProperty("User-Agent", "ReisePilot/4.4 Android family travel app")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Autobahn-App HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }
}
