package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject

enum class AssistantIntent52(val label: String, val hint: String) {
    WHAT_TODAY("Was passt heute?", "Drei passende Ziele für die aktuelle Situation"),
    DAY_PLAN("Tagesplan", "Mehrere passende Stopps zu einem entspannten Ablauf verbinden"),
    COMPARE("Ziele vergleichen", "Zwei Ziele nach Entfernung, Dauer und Familiennutzen vergleichen"),
    DRIVE_REVIEW("Fahrt bewerten", "Route, Verkehr, Tank und Ankunft zusammenfassen"),
    PAUSE_PLAN("Pause planen", "Einen vernünftigen nächsten Pausenzeitpunkt ableiten"),
    FUEL_PLAN("Tankstopp prüfen", "Tankreserve und vorhandenen Tankvorschlag bewerten"),
    PARKING_HELP("Parken planen", "Für ein Ziel eine stressarme Ankunft und Parkplatzstrategie ableiten"),
    PACKING_CHECK("Packliste prüfen", "Sinnvolle Ergänzungen vorschlagen, nichts automatisch löschen"),
    CUSTOM("Eigene Frage", "Freie Frage mit den aktuellen Reisedaten")
}

enum class AssistantSource52 { OPENAI, OPENAI_DIRECT, LOCAL }

enum class AssistantMode53(val label: String, val detail: String) {
    LOCAL("Lokal", "Ohne Internet und ohne API-Kosten"),
    DIRECT("Direkt", "Persönlicher Testmodus mit lokal verschlüsseltem API-Key"),
    PROXY("Proxy", "Empfohlener Dauerbetrieb über eigenes Backend");

    companion object {
        fun fromStored(value: String?): AssistantMode53 = entries.firstOrNull { it.name == value } ?: LOCAL
    }
}

data class AssistantSuggestion52(
    val title: String,
    val detail: String,
    val reason: String = "",
    val destinationTitle: String = "",
    val packingItems: List<String> = emptyList()
)

data class AssistantAnswer52(
    val headline: String,
    val summary: String,
    val suggestions: List<AssistantSuggestion52>,
    val warnings: List<String> = emptyList(),
    val source: AssistantSource52 = AssistantSource52.LOCAL
)

fun AssistantAnswer52.toJson(): JSONObject = JSONObject().apply {
    put("headline", headline)
    put("summary", summary)
    put("suggestions", JSONArray().apply {
        suggestions.forEach { suggestion ->
            put(JSONObject().apply {
                put("title", suggestion.title)
                put("detail", suggestion.detail)
                put("reason", suggestion.reason)
                put("destinationTitle", suggestion.destinationTitle)
                put("packingItems", JSONArray(suggestion.packingItems))
            })
        }
    })
    put("warnings", JSONArray(warnings))
}

fun assistantAnswerFromJson52(root: JSONObject, source: AssistantSource52): AssistantAnswer52 {
    val suggestionsJson = root.optJSONArray("suggestions") ?: JSONArray()
    val suggestions = buildList {
        for (index in 0 until suggestionsJson.length()) {
            val value = suggestionsJson.optJSONObject(index) ?: continue
            val packingJson = value.optJSONArray("packingItems") ?: JSONArray()
            val packing = buildList {
                for (itemIndex in 0 until packingJson.length()) {
                    packingJson.optString(itemIndex).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            add(
                AssistantSuggestion52(
                    title = value.optString("title").ifBlank { "Vorschlag" },
                    detail = value.optString("detail"),
                    reason = value.optString("reason"),
                    destinationTitle = value.optString("destinationTitle"),
                    packingItems = packing.distinct().take(12)
                )
            )
        }
    }
    val warningsJson = root.optJSONArray("warnings") ?: JSONArray()
    val warnings = buildList {
        for (index in 0 until warningsJson.length()) {
            warningsJson.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
    return AssistantAnswer52(
        headline = root.optString("headline").ifBlank { "ReisePilot-Empfehlung" },
        summary = root.optString("summary"),
        suggestions = suggestions.take(6),
        warnings = warnings.take(5),
        source = source
    )
}