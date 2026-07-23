package de.balabucha.reisepilot

import android.content.Context

object AssistantOffline52 {
    fun answer(
        context: Context,
        snapshot: TripSnapshot,
        intent: AssistantIntent52,
        question: String
    ): AssistantAnswer52 = when (resolveIntent(intent, question)) {
        AssistantIntent52.WHAT_TODAY -> destinations(snapshot)
        AssistantIntent52.DRIVE_REVIEW -> driveReview(snapshot)
        AssistantIntent52.PAUSE_PLAN -> pausePlan(snapshot)
        AssistantIntent52.FUEL_PLAN -> fuelPlan(snapshot)
        AssistantIntent52.PACKING_CHECK -> packingCheck(context, question)
        AssistantIntent52.CUSTOM -> destinations(snapshot)
    }

    private fun resolveIntent(intent: AssistantIntent52, question: String): AssistantIntent52 {
        if (intent != AssistantIntent52.CUSTOM) return intent
        val clean = question.lowercase()
        return when {
            listOf("tank", "diesel", "sprit").any(clean::contains) -> AssistantIntent52.FUEL_PLAN
            listOf("pause", "rast", "toilette").any(clean::contains) -> AssistantIntent52.PAUSE_PLAN
            listOf("pack", "mitnehmen", "fehlt").any(clean::contains) -> AssistantIntent52.PACKING_CHECK
            listOf("fahrt", "route", "ankunft", "verkehr").any(clean::contains) -> AssistantIntent52.DRIVE_REVIEW
            else -> AssistantIntent52.WHAT_TODAY
        }
    }

    private fun destinations(snapshot: TripSnapshot): AssistantAnswer52 {
        val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
        val region = DestinationCatalog.nearestRegion(location) ?: TravelRegion.CANET
        val selected = DestinationCatalog.places
            .filter { it.region == region }
            .sortedWith(
                compareBy<TravelPlace> { DestinationCatalog.distanceKm(location, it) ?: Double.MAX_VALUE }
                    .thenByDescending { it.priority }
            )
            .fold(mutableListOf<TravelPlace>()) { result, place ->
                if (result.none { it.kind == place.kind } || result.size < 2) result += place
                result
            }
            .take(3)
        return AssistantAnswer52(
            headline = "Drei passende Ziele",
            summary = "Lokale ReisePilot-Auswertung für ${region.label}. Wetter und Öffnungszeiten sind noch nicht berücksichtigt.",
            suggestions = selected.map { place ->
                val distance = DestinationCatalog.distanceKm(location, place)
                AssistantSuggestion52(
                    title = place.title,
                    detail = buildString {
                        append(place.duration)
                        distance?.let { append(" · ").append("%.1f".format(it)).append(" km ab aktuellem Standort") }
                    },
                    reason = place.description,
                    destinationTitle = place.title
                )
            },
            warnings = listOf("Vor der Abfahrt Öffnungszeiten, Tickets und Wetter prüfen."),
            source = AssistantSource52.LOCAL
        )
    }

    private fun driveReview(snapshot: TripSnapshot): AssistantAnswer52 {
        val traffic = when {
            snapshot.trafficDelayMin == null -> "Verkehrslage noch nicht vollständig geladen."
            snapshot.trafficDelayMin <= 5 -> "Die Route läuft derzeit weitgehend planmäßig."
            else -> "Aktuell sind etwa ${snapshot.trafficDelayMin} Minuten Verzögerung eingerechnet."
        }
        val fuel = when (snapshot.fuelLight) {
            Light.GREEN -> "Die geschätzte Tankreserve ist unkritisch."
            Light.YELLOW -> "Den nächsten sinnvollen Tankstopp nicht unnötig verschieben."
            Light.RED -> "Zeitnah tanken und die Reichweite nicht ausreizen."
            Light.GREY -> "Der Tankstatus ist noch nicht belastbar."
        }
        return AssistantAnswer52(
            headline = if (snapshot.active) "Fahrt aktuell bewertet" else "Fahrt noch nicht gestartet",
            summary = if (snapshot.active) {
                listOfNotNull(
                    snapshot.remainingKm?.let { "$it km Reststrecke" },
                    snapshot.trafficDelayMin?.let { if (it > 0) "+$it Min. Verkehr" else "Verkehr normal" },
                    "${"%.1f".format(snapshot.fuelLitres)} l geschätzt"
                ).joinToString(" · ")
            } else "Für eine echte Live-Bewertung zuerst die Reise oder Testfahrt starten.",
            suggestions = listOf(
                AssistantSuggestion52("Verkehr", traffic),
                AssistantSuggestion52("Tankreserve", fuel),
                AssistantSuggestion52("Nächster Schritt", snapshot.nextTitle, snapshot.nextDetail)
            ),
            source = AssistantSource52.LOCAL
        )
    }

    private fun pausePlan(snapshot: TripSnapshot): AssistantAnswer52 {
        val untilPause = (150 - snapshot.driveMinutes).coerceAtLeast(0)
        val detail = when {
            !snapshot.active -> "Reise starten; danach wird die bisherige Fahrzeit berücksichtigt."
            snapshot.paused -> "Die Fahrt ist bereits pausiert."
            untilPause == 0 -> "Eine Pause ist jetzt sinnvoll."
            else -> "In ungefähr $untilPause Minuten erneut prüfen."
        }
        return AssistantAnswer52(
            headline = "Pausenplanung",
            summary = "Die lokale Regel plant konservativ mit etwa 2½ Stunden Fahrzeit zwischen längeren Pausen.",
            suggestions = listOf(
                AssistantSuggestion52("Empfehlung", detail),
                AssistantSuggestion52("Kombinieren", "Pause möglichst mit Toilette, Getränken und einem sinnvollen Tankstopp verbinden.")
            ),
            source = AssistantSource52.LOCAL
        )
    }

    private fun fuelPlan(snapshot: TripSnapshot): AssistantAnswer52 {
        val fuel = snapshot.fuelSuggestion
        return if (fuel != null) {
            AssistantAnswer52(
                headline = "Tankstopp geprüft",
                summary = "${fuel.name} liegt ${"%.1f".format(fuel.distanceAheadKm)} km voraus und verursacht ungefähr ${"%.1f".format(fuel.detourKm)} km Umweg.",
                suggestions = listOf(
                    AssistantSuggestion52(
                        title = fuel.name,
                        detail = fuel.pricePerLitre?.let { "${"%.3f".format(it)} €/l" } ?: "Preis wird noch geladen",
                        reason = "Der Stopp wurde bereits durch das Tankmodell entlang der Route ausgewählt."
                    )
                ),
                source = AssistantSource52.LOCAL
            )
        } else {
            AssistantAnswer52(
                headline = "Noch kein Tankstopp ausgewählt",
                summary = "Geschätzter Tankinhalt: ${"%.1f".format(snapshot.fuelLitres)} Liter.",
                suggestions = listOf(
                    AssistantSuggestion52("Weiter beobachten", "Nach Fahrtstart prüft ReisePilot Stationen entlang der Route und berücksichtigt Umwege."),
                    AssistantSuggestion52("Nicht auf Kante fahren", "Bei gelbem Tankstatus den nächsten vernünftigen Stopp übernehmen.")
                ),
                source = AssistantSource52.LOCAL
            )
        }
    }

    private fun packingCheck(context: Context, question: String): AssistantAnswer52 {
        val repository = PackingRepository(context)
        val existing = repository.state.items.map { it.name.lowercase() }
        val base = mutableListOf("Sonnencreme", "Trinkwasser", "Powerbank", "leichte Regenjacken")
        val clean = question.lowercase()
        if (clean.contains("andorra") || clean.contains("wander")) base += listOf("feste Schuhe", "kleiner Wanderrucksack")
        if (clean.contains("strand") || clean.contains("canet")) base += listOf("Badeschuhe", "Sonnenschirm")
        if (clean.contains("paris") || clean.contains("regen")) base += "kleiner Regenschirm"
        val missing = base.distinct().filter { candidate -> existing.none { it.contains(candidate.lowercase()) } }.take(8)
        return AssistantAnswer52(
            headline = "Packliste geprüft",
            summary = if (missing.isEmpty()) "Für die geprüfte Situation ist kein offensichtlicher Standardpunkt offen." else "Diese Punkte sind in der bestehenden Liste nicht eindeutig erkennbar.",
            suggestions = if (missing.isEmpty()) {
                listOf(AssistantSuggestion52("Keine automatische Änderung", "Bestehende Einträge bleiben unverändert."))
            } else {
                listOf(
                    AssistantSuggestion52(
                        title = "Vorschläge zur Packliste",
                        detail = missing.joinToString(" · "),
                        reason = "Vor dem Hinzufügen kannst du jeden Vorschlag prüfen.",
                        packingItems = missing
                    )
                )
            },
            source = AssistantSource52.LOCAL
        )
    }
}
