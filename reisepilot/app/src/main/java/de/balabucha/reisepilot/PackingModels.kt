package de.balabucha.reisepilot

import org.json.JSONArray
import org.json.JSONObject

const val PACKING_SCHEMA_VERSION = 1

enum class PackPerson(val displayName: String) {
    ANDREY("Andrey"),
    JULIA("Julia"),
    EDWARD("Edward"),
    SOFIA("Sofia");

    companion object {
        fun fromStored(value: String?): PackPerson? = entries.firstOrNull { it.name == value }
    }
}

data class PackingCategory(
    val id: String,
    val name: String,
    val description: String = "",
    val position: Int,
    val collapsed: Boolean = true,
    val hidden: Boolean = false,
    val isStandard: Boolean = false
)

data class PackingItem(
    val id: String,
    val name: String,
    val quantity: String = "1",
    val categoryId: String,
    val person: PackPerson? = null,
    val note: String = "",
    val position: Int,
    val checked: Boolean = false,
    val isStandard: Boolean = false
)

data class PackingState(
    val schemaVersion: Int = PACKING_SCHEMA_VERSION,
    val categories: List<PackingCategory>,
    val items: List<PackingItem>
)

data class PackingProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val percent: Int get() = if (total == 0) 0 else ((done * 100f) / total).toInt()
}

enum class PackingFilter(val label: String) {
    ALL("Alle"),
    OPEN("Offen"),
    DONE("Erledigt"),
    ANDREY("Andrey"),
    JULIA("Julia"),
    EDWARD("Edward"),
    SOFIA("Sofia");

    val person: PackPerson?
        get() = when (this) {
            ANDREY -> PackPerson.ANDREY
            JULIA -> PackPerson.JULIA
            EDWARD -> PackPerson.EDWARD
            SOFIA -> PackPerson.SOFIA
            else -> null
        }
}

object PackingLogic {
    fun progress(items: List<PackingItem>): PackingProgress = PackingProgress(
        done = items.count { it.checked },
        total = items.size
    )

    fun categoryProgress(state: PackingState, categoryId: String): PackingProgress =
        progress(state.items.filter { it.categoryId == categoryId })

    fun personProgress(state: PackingState, person: PackPerson): PackingProgress =
        progress(state.items.filter { it.person == person })

    fun filteredItems(
        state: PackingState,
        query: String,
        filter: PackingFilter,
        categoryId: String?
    ): List<PackingItem> {
        val cleanQuery = query.trim()
        val categories = state.categories.associateBy { it.id }
        return state.items
            .asSequence()
            .filter { item -> categoryId == null || item.categoryId == categoryId }
            .filter { item ->
                when (filter) {
                    PackingFilter.ALL -> true
                    PackingFilter.OPEN -> !item.checked
                    PackingFilter.DONE -> item.checked
                    else -> item.person == filter.person
                }
            }
            .filter { item ->
                if (cleanQuery.isBlank()) return@filter true
                val category = categories[item.categoryId]?.name.orEmpty()
                listOf(
                    item.name,
                    item.quantity,
                    item.note,
                    item.person?.displayName.orEmpty(),
                    category
                ).any { it.contains(cleanQuery, ignoreCase = true) }
            }
            .sortedWith(compareBy<PackingItem> { item ->
                categories[item.categoryId]?.position ?: Int.MAX_VALUE
            }.thenBy { it.position }.thenBy { it.id })
            .toList()
    }

    fun normalized(state: PackingState): PackingState {
        val categories = state.categories
            .distinctBy { it.id }
            .sortedWith(compareBy<PackingCategory> { it.position }.thenBy { it.id })
            .mapIndexed { index, category -> category.copy(position = index) }
        val categoryIds = categories.mapTo(mutableSetOf()) { it.id }
        val items = state.items
            .distinctBy { it.id }
            .filter { it.categoryId in categoryIds }
            .groupBy { it.categoryId }
            .flatMap { (_, group) ->
                group.sortedWith(compareBy<PackingItem> { it.position }.thenBy { it.id })
                    .mapIndexed { index, item -> item.copy(position = index) }
            }
        return state.copy(
            schemaVersion = PACKING_SCHEMA_VERSION,
            categories = categories,
            items = items
        )
    }
}

object PackingStateJson {
    fun encode(state: PackingState): String = JSONObject().apply {
        put("schemaVersion", state.schemaVersion)
        put("categories", JSONArray().apply {
            state.categories.forEach { category ->
                put(JSONObject().apply {
                    put("id", category.id)
                    put("name", category.name)
                    put("description", category.description)
                    put("position", category.position)
                    put("collapsed", category.collapsed)
                    put("hidden", category.hidden)
                    put("isStandard", category.isStandard)
                })
            }
        })
        put("items", JSONArray().apply {
            state.items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("categoryId", item.categoryId)
                    put("person", item.person?.name ?: JSONObject.NULL)
                    put("note", item.note)
                    put("position", item.position)
                    put("checked", item.checked)
                    put("isStandard", item.isStandard)
                })
            }
        })
    }.toString()

    fun decode(raw: String?): PackingState? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val categoriesJson = root.getJSONArray("categories")
            val itemsJson = root.getJSONArray("items")
            val categories = buildList {
                for (index in 0 until categoriesJson.length()) {
                    val value = categoriesJson.getJSONObject(index)
                    add(
                        PackingCategory(
                            id = value.getString("id"),
                            name = value.getString("name"),
                            description = value.optString("description"),
                            position = value.optInt("position", index),
                            collapsed = value.optBoolean("collapsed", true),
                            hidden = value.optBoolean("hidden", false),
                            isStandard = value.optBoolean("isStandard", false)
                        )
                    )
                }
            }
            val items = buildList {
                for (index in 0 until itemsJson.length()) {
                    val value = itemsJson.getJSONObject(index)
                    add(
                        PackingItem(
                            id = value.getString("id"),
                            name = value.getString("name"),
                            quantity = value.optString("quantity", "1"),
                            categoryId = value.getString("categoryId"),
                            person = PackPerson.fromStored(
                                if (value.isNull("person")) null else value.optString("person")
                            ),
                            note = value.optString("note"),
                            position = value.optInt("position", index),
                            checked = value.optBoolean("checked", false),
                            isStandard = value.optBoolean("isStandard", false)
                        )
                    )
                }
            }
            PackingLogic.normalized(
                PackingState(
                    schemaVersion = root.optInt("schemaVersion", PACKING_SCHEMA_VERSION),
                    categories = categories,
                    items = items
                )
            )
        }.getOrNull()
    }
}

private data class DefaultPackEntry(
    val name: String,
    val quantity: String = "1",
    val note: String = "",
    val person: PackPerson? = null
)

object DefaultPackingList {
    private fun e(
        name: String,
        quantity: String = "1",
        note: String = "",
        person: PackPerson? = null
    ) = DefaultPackEntry(name, quantity, note, person)

    fun create(): PackingState {
        val categories = mutableListOf<PackingCategory>()
        val items = mutableListOf<PackingItem>()

        fun category(
            id: String,
            name: String,
            description: String = "",
            person: PackPerson? = null,
            entries: List<DefaultPackEntry>
        ) {
            val categoryPosition = categories.size
            categories += PackingCategory(
                id = id,
                name = name,
                description = description,
                position = categoryPosition,
                collapsed = true,
                isStandard = true
            )
            entries.forEachIndexed { index, entry ->
                items += PackingItem(
                    id = "$id-item-${(index + 1).toString().padStart(3, '0')}",
                    name = entry.name,
                    quantity = entry.quantity,
                    categoryId = id,
                    person = entry.person ?: person,
                    note = entry.note,
                    position = index,
                    isStandard = true
                )
            }
        }

        category(
            id = "packing-v1-cat-01-documents",
            name = "Dokumente und Geld",
            entries = listOf(
                e("Personalausweise oder Reisepässe", "4", "für alle vier"),
                e("Führerschein"),
                e("Fahrzeugschein"),
                e("Versicherungsnachweis"),
                e("Schutzbrief"),
                e("Europäische Krankenversicherungskarten", "4"),
                e("Buchungsbestätigung Montbéliard"),
                e("Buchungsbestätigung Malibu Village"),
                e("Paris-Buchung später ergänzen", note = "Unterkunft noch nicht gebucht"),
                e("Kreditkarte"),
                e("PIN der Kreditkarte"),
                e("EC- oder Debitkarte"),
                e("Bargeld"),
                e("Ersatzschlüssel"),
                e("Kopien oder Fotos wichtiger Dokumente"),
                e("Crit’Air-Plakette oder Bestellnachweis"),
                e("Übernachtungssteuer Montbéliard einplanen"),
                e("Übernachtungssteuer Malibu Village einplanen"),
                e("100 Euro Kaution Malibu Village einplanen")
            )
        )

        category(
            id = "packing-v1-cat-02-andrey",
            name = "Andrey",
            person = PackPerson.ANDREY,
            entries = listOf(
                e("T-Shirts oder Oberteile", "7"),
                e("Kurze Hosen", "3"),
                e("Leichte lange Hosen", "2"),
                e("Normales bis schickeres Outfit für Paris"),
                e("Pullover"),
                e("Leichte Jacke"),
                e("Unterhosen", "8"),
                e("Paar Socken", "7"),
                e("Badehosen", "2"),
                e("Schlafanzug"),
                e("Cap oder Sonnenhut"),
                e("Paar bequeme Schuhe"),
                e("Paar offene Schuhe oder Latschen"),
                e("Paar Badeschuhe")
            )
        )

        category(
            id = "packing-v1-cat-03-julia",
            name = "Julia",
            person = PackPerson.JULIA,
            entries = listOf(
                e("T-Shirts oder Oberteile", "7"),
                e("Kurze Hosen oder Röcke", "3"),
                e("Leichte lange Hosen", "2"),
                e("Normales bis schickeres Outfit für Paris"),
                e("Pullover"),
                e("Leichte Jacke"),
                e("Unterwäsche-Sets", "8"),
                e("Paar Socken", "7"),
                e("Badeoutfits", "2"),
                e("Schlafanzug"),
                e("Sonnenhut oder Cap"),
                e("Paar bequeme Schuhe"),
                e("Paar offene Schuhe oder Latschen"),
                e("Paar Badeschuhe")
            )
        )

        category(
            id = "packing-v1-cat-04-edward",
            name = "Edward",
            person = PackPerson.EDWARD,
            entries = listOf(
                e("T-Shirts", "8–9"),
                e("Kurze Hosen", "4"),
                e("Lange Hosen", "2"),
                e("Reserveoutfits", "2"),
                e("Normales bis schickeres Outfit für Paris"),
                e("Pullover"),
                e("Leichte Jacke"),
                e("Unterhosen", "10"),
                e("Paar Socken", "8"),
                e("Badehosen", "3"),
                e("Schlafanzüge", "2"),
                e("Cap"),
                e("Paar bequeme Schuhe"),
                e("Paar offene Schuhe oder Latschen"),
                e("Paar Badeschuhe")
            )
        )

        category(
            id = "packing-v1-cat-05-sofia",
            name = "Sofia",
            person = PackPerson.SOFIA,
            entries = listOf(
                e("T-Shirts", "8–9"),
                e("Kurze Hosen oder Röcke", "4"),
                e("Lange Hosen", "2"),
                e("Reserveoutfits", "2"),
                e("Normales bis schickeres Outfit für Paris"),
                e("Pullover"),
                e("Leichte Jacke"),
                e("Unterwäsche-Sets", "10"),
                e("Paar Socken", "8"),
                e("Badeoutfits", "3"),
                e("Schlafanzüge", "2"),
                e("Cap oder Sonnenhut"),
                e("Paar bequeme Schuhe"),
                e("Paar offene Schuhe oder Latschen"),
                e("Paar Badeschuhe")
            )
        )

        category(
            id = "packing-v1-cat-06-household",
            name = "Unterkunft und Haushalt",
            description = "Wäschepakete für vier Personen sind bestellt. Keine eigene Bettwäsche, zusätzlichen normalen Duschtücher, Föhn, große 55-Liter-Kühlbox, Grillzubehör oder komplette Küchenausstattung einplanen.",
            entries = listOf(
                e("Geschirrtücher", "2"),
                e("Spülmittel"),
                e("Spülschwamm"),
                e("Küchenrolle"),
                e("Müllbeutel"),
                e("Toilettenpapier für die Ankunft"),
                e("Waschmittel", "2 Waschgänge"),
                e("Fleckenmittel"),
                e("Wäscheklammern"),
                e("Brotdosen"),
                e("Zip-Beutel"),
                e("Trinkflaschen", "4"),
                e("Kleine Kühltasche"),
                e("Kühlakkus"),
                e("Salz"),
                e("Pfeffer"),
                e("Kleine Gewürzauswahl"),
                e("Küchenmesser"),
                e("Flaschenöffner oder Korkenzieher"),
                e("Mehrfachsteckdose"),
                e("Verlängerungskabel")
            )
        )

        category(
            id = "packing-v1-cat-07-beach",
            name = "Strand und Pool",
            entries = listOf(
                e("Große Strandhandtücher", "2"),
                e("Große Stranddecke"),
                e("Strandtasche"),
                e("Mittelgroßer Sonnenschirm"),
                e("Strandstühle", "2"),
                e("Sonnencreme für Erwachsene"),
                e("Sonnencreme LSF 50 für die Kinder"),
                e("After-Sun"),
                e("Sonnenbrillen", "4"),
                e("Caps oder Sonnenhüte", "4"),
                e("Paar Badeschuhe", "4"),
                e("Schwimmbrillen"),
                e("Schnorchelset"),
                e("Etwas Wasserspielzeug"),
                e("Beutel für nasse Badesachen"),
                e("Kleine Kühltasche"),
                e("Trinkflaschen", "4"),
                e("Wasserfeste Handyhülle"),
                e("Kleine Bürste gegen Sand")
            )
        )

        category(
            id = "packing-v1-cat-08-sup",
            name = "SUP",
            description = "Zusammengefaltet im Kofferraum. Keine zusätzlichen Schwimmwesten einplanen.",
            entries = listOf(
                e("SUP-Board"),
                e("SUP-Transporttasche"),
                e("Paddel"),
                e("Finne"),
                e("Finnenstift oder Schraube"),
                e("Manuelle SUP-Pumpe"),
                e("Elektrische Pkw-Reifenpumpe"),
                e("Adapter Pkw-Ventil auf SUP"),
                e("Pumpenschlauch"),
                e("Manometer"),
                e("Ventilschlüssel"),
                e("Reparaturset"),
                e("Ersatzflicken"),
                e("Kleber"),
                e("Leash"),
                e("Wasserdichter Packsack"),
                e("Wasserdichte Handyhülle"),
                e("Kleines Mikrofasertuch"),
                e("Trinkflasche"),
                e("Spanngurte"),
                e("Ersatzspanngurt"),
                e("Kabelschloss oder Sicherung")
            )
        )

        category(
            id = "packing-v1-cat-09-tech",
            name = "Technik",
            description = "Keine Drohne, Bluetooth-Lautsprecher, Spielekonsole, Laptop oder Föhn einplanen.",
            entries = listOf(
                e("Handys", "3"),
                e("Tablet"),
                e("Ladekabel für die Handys", "3"),
                e("Tablet-Ladekabel"),
                e("Powerbanks", "2"),
                e("12-Volt-USB-Adapter"),
                e("Kopfhörer für die Kinder", "2"),
                e("Tablet-Halterung für das Auto"),
                e("Mi Box"),
                e("Mi-Box-Fernbedienung"),
                e("Mi-Box-Netzteil"),
                e("HDMI-Kabel"),
                e("Batterien für die Mi-Box-Fernbedienung"),
                e("Smartwatch-Ladekabel", note = "falls benötigt"),
                e("Offline-Karten"),
                e("Offline-Filme"),
                e("Offline-Spiele"),
                e("Buchungsbestätigungen offline speichern")
            )
        )

        category(
            id = "packing-v1-cat-10-food",
            name = "Essen und Getränke",
            description = "Für lange Autofahrten, ersten Abend, ersten Morgen und etwa ein bis zwei Tage. Die große 55-Liter-Kompressor-Kühlbox bleibt zu Hause.",
            entries = listOf(
                e("Brot"),
                e("Belegte Brote"),
                e("Snacks"),
                e("Obst"),
                e("Müsliriegel"),
                e("Getränke"),
                e("Wasser"),
                e("Kaffee"),
                e("Milch oder Alternative"),
                e("Lebensmittel für den ersten Abend"),
                e("Frühstück für den ersten Morgen im Malibu Village"),
                e("Servietten"),
                e("Küchenrolle"),
                e("Müllbeutel"),
                e("Besteck für unterwegs"),
                e("Kleine Kühltasche"),
                e("Kühlakkus")
            )
        )

        category(
            id = "packing-v1-cat-11-car",
            name = "Auto und Fahrt",
            entries = listOf(
                e("Passendes Motoröl", "mindestens 2 Liter"),
                e("Ölstand vor Abfahrt kontrollieren"),
                e("Ölstand unterwegs erneut kontrollieren"),
                e("Kühlmittelstand prüfen"),
                e("Scheibenwaschwasser auffüllen"),
                e("Insektenreiniger"),
                e("AdBlue prüfen"),
                e("Reifenluftdruck für volle Beladung einstellen"),
                e("Reifenprofil prüfen"),
                e("Reserverad oder Pannenset prüfen"),
                e("Kompressor"),
                e("Wagenheber"),
                e("Radschlüssel"),
                e("Abschleppöse"),
                e("Starthilfekabel"),
                e("Ersatzsicherungen"),
                e("Taschenlampe"),
                e("Arbeitshandschuhe"),
                e("Kleines Werkzeugset"),
                e("OBD11 oder Xtool"),
                e("Handyhalterung"),
                e("Ladekabel"),
                e("Warnwesten", "4"),
                e("Warndreieck"),
                e("Verbandkasten"),
                e("Klebeband"),
                e("Kabelbinder"),
                e("Trinkwasser als Notreserve"),
                e("Müllbeutel"),
                e("Feuchttücher"),
                e("Taschentücher")
            )
        )

        category(
            id = "packing-v1-cat-12-personal",
            name = "Körperpflege und persönliche Mittel",
            description = "Rein organisatorische Liste ohne automatische medizinische Warnungen oder allgemeine Gesundheitshinweise.",
            entries = listOf(
                e("Zahnbürsten", "4"),
                e("Zahnpasta"),
                e("Shampoo"),
                e("Duschgel"),
                e("Deo"),
                e("Bürste oder Kamm"),
                e("Rasierer"),
                e("Persönliche Hygieneartikel"),
                e("Feuchttücher"),
                e("Taschentücher"),
                e("Lippenpflege mit Sonnenschutz"),
                e("Insektenschutz"),
                e("Mittel gegen Insektenstiche"),
                e("Pflaster"),
                e("Blasenpflaster"),
                e("Wunddesinfektion"),
                e("Verbandmaterial"),
                e("Pinzette"),
                e("Zeckenzange"),
                e("Fieberthermometer"),
                e("Elektrolyte"),
                e("Kühlgel"),
                e("Persönliche beziehungsweise bewährte Mittel")
            )
        )

        category(
            id = "packing-v1-cat-13-montbeliard",
            name = "Montbéliard",
            description = "Separate kleine Übernachtungstasche. Frühstück ist inklusive und muss nicht zusätzlich eingeplant werden.",
            entries = listOf(
                e("Kleidung für den nächsten Tag", "4"),
                e("Unterwäsche", "4"),
                e("Schlafanzüge", "4"),
                e("Waschzeug"),
                e("Ladekabel"),
                e("Dokumente"),
                e("Persönliche Mittel")
            )
        )

        category(
            id = "packing-v1-cat-14-malibu",
            name = "Malibu Village",
            entries = listOf(
                e("Wäschepakete für vier Personen bestellt"),
                e("Bettwäsche enthalten"),
                e("Normale Handtücher enthalten"),
                e("Kreditkarte für Kaution"),
                e("Ausweis zur Schlüsselübergabe"),
                e("Lebensmittel für ersten Abend"),
                e("Frühstück für ersten Morgen"),
                e("Haushaltsgrundausstattung"),
                e("Waschmittel"),
                e("Strand- und SUP-Sachen")
            )
        )

        category(
            id = "packing-v1-cat-15-paris",
            name = "Paris",
            description = "Unterkunft noch nicht gebucht; später frei ergänzbar. Gepäck kompakt zusammenlegen und verdeckt unten im Kofferraum verstauen.",
            entries = listOf(
                e("Kleidung für drei Nächte", "4 Personen"),
                e("Unterwäsche", "4 Personen"),
                e("Schlafsachen", "4 Personen"),
                e("Waschzeug"),
                e("Bequeme Schuhe", "4 Paar"),
                e("Etwas schickeres Outfit"),
                e("Leichte Jacken", "4"),
                e("Tagesrucksäcke"),
                e("Trinkflaschen", "4"),
                e("Powerbank"),
                e("Ladekabel"),
                e("Dokumente"),
                e("Parkplatzbestätigung", note = "nach Hotelbuchung ergänzen"),
                e("Hotelbuchung", note = "noch offen"),
                e("Frühstück prüfen", note = "nach Hotelbuchung")
            )
        )

        category(
            id = "packing-v1-cat-16-luggage",
            name = "Gepäckaufteilung",
            description = "Weiche Taschen priorisieren. Gepäck darf zwischen den hinteren Sitzen, in den hinteren Fußräumen und unten im Kofferraum liegen – nicht im vorderen Fußraum. Schwere Gegenstände unten und sicher verstauen. Dachbox nur als Notlösung.",
            entries = listOf(
                e("Große Thule-Tasche", note = "Kleidung Andrey und Julia"),
                e("Kleine Sporttasche 1", note = "Kleidung Edward"),
                e("Kleine Sporttasche 2", note = "Kleidung Sofia"),
                e("60-Liter-Rucksack", note = "gemeinsame Paris-Sachen"),
                e("Faltbare Eurobox", note = "Küche, Lebensmittel, Getränke und Haushaltsartikel"),
                e("Tagesrucksäcke", note = "Ausflüge, Strand, Technik und persönliche Dinge"),
                e("SUP-Tasche", note = "SUP, Pumpe, Paddel, Finne, Leash und Reparaturset"),
                e("Rollkoffer nicht bevorzugen", "2", "Weiche Taschen lassen sich im Kofferraum besser verteilen")
            )
        )

        return PackingState(categories = categories, items = items)
    }
}
