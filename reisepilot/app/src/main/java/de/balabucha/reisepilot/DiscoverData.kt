package de.balabucha.reisepilot

enum class TravelRegion(
    val label: String,
    val center: GeoPoint,
    val radiusKm: Int
) {
    CANET("Canet", GeoPoint(42.7069, 3.0182), 65),
    BARCELONA("Barcelona", GeoPoint(41.3874, 2.1686), 70),
    ANDORRA("Andorra", GeoPoint(42.5063, 1.5218), 70),
    PARIS("Paris", GeoPoint(48.8566, 2.3522), 75)
}

enum class PlaceKind(val label: String) {
    HIGHLIGHT("Highlights"),
    FAMILY("Mit Kindern"),
    NATURE("Natur"),
    SHOPPING("Einkaufen")
}

data class TravelPlace(
    val region: TravelRegion,
    val title: String,
    val description: String,
    val point: GeoPoint,
    val kind: PlaceKind,
    val duration: String,
    val wikiTitle: String,
    val priority: Int = 50
)

object DestinationCatalog {
    val places: List<TravelPlace> = listOf(
        TravelPlace(TravelRegion.CANET, "Strand von Canet", "Breiter Sandstrand für einen unkomplizierten Bade- und Spaziergangstag.", GeoPoint(42.6969, 3.0355), PlaceKind.HIGHLIGHT, "1–4 Std.", "Canet-en-Roussillon", 100),
        TravelPlace(TravelRegion.CANET, "Oniria Aquarium", "Modernes Aquarium direkt am Hafen – gute Schlechtwetter- und Kinderoption.", GeoPoint(42.6993, 3.0374), PlaceKind.FAMILY, "2–3 Std.", "Canet-en-Roussillon", 95),
        TravelPlace(TravelRegion.CANET, "Fischerdorf & Lagune", "Kurzer Naturausflug am geschützten Étang mit traditionellen Fischerhütten.", GeoPoint(42.6704, 3.0063), PlaceKind.NATURE, "1,5–2,5 Std.", "Étang de Canet-Saint-Nazaire", 92),
        TravelPlace(TravelRegion.CANET, "Collioure", "Kompakter Küstenort mit Burg, Altstadt, Hafen und sehr schöner Abendstimmung.", GeoPoint(42.5250, 3.0833), PlaceKind.HIGHLIGHT, "Halber Tag", "Collioure", 98),
        TravelPlace(TravelRegion.CANET, "Palast der Könige von Mallorca", "Historische Festung über Perpignan mit Stadt- und Bergblick.", GeoPoint(42.6937, 2.8954), PlaceKind.HIGHLIGHT, "2–3 Std.", "Palace of the Kings of Majorca", 82),
        TravelPlace(TravelRegion.CANET, "Cap Leucate", "Klippen, Meerblick und kurze Wege – gut für einen spontanen Naturausflug.", GeoPoint(42.9103, 3.0590), PlaceKind.NATURE, "2–4 Std.", "Leucate", 78),
        TravelPlace(TravelRegion.CANET, "Intermarché Canet", "Großer Einkauf für Wasser, Frühstück und Reisevorräte; normalerweise günstiger als kleine Strandläden.", GeoPoint(42.7032, 3.0108), PlaceKind.SHOPPING, "30–60 Min.", "Canet-en-Roussillon", 100),
        TravelPlace(TravelRegion.CANET, "Lidl Canet", "Schneller günstiger Lebensmitteleinkauf mit kleinem Umweg vom Malibu Village.", GeoPoint(42.7070, 3.0094), PlaceKind.SHOPPING, "20–40 Min.", "Canet-en-Roussillon", 92),

        TravelPlace(TravelRegion.BARCELONA, "Sagrada Família", "Gaudís Wahrzeichen – von außen bereits stark, innen nur mit Zeitfenster sinnvoll.", GeoPoint(41.4036, 2.1744), PlaceKind.HIGHLIGHT, "1,5–2,5 Std.", "Sagrada Família", 100),
        TravelPlace(TravelRegion.BARCELONA, "Park Güell", "Mosaike, Gaudí-Architektur und Stadtblick; Tickets vorher reservieren.", GeoPoint(41.4145, 2.1527), PlaceKind.FAMILY, "2–3 Std.", "Park Güell", 96),
        TravelPlace(TravelRegion.BARCELONA, "Gotisches Viertel", "Schmale Gassen, Plätze und Kathedrale – ideal mit Rambla und Hafen kombinierbar.", GeoPoint(41.3839, 2.1763), PlaceKind.HIGHLIGHT, "2–4 Std.", "Gothic Quarter, Barcelona", 94),
        TravelPlace(TravelRegion.BARCELONA, "Montjuïc", "Aussicht, Gärten, Olympiagelände und Burg – viel Abwechslung ohne Dauerstress.", GeoPoint(41.3636, 2.1651), PlaceKind.NATURE, "Halber Tag", "Montjuïc", 90),
        TravelPlace(TravelRegion.BARCELONA, "Barceloneta", "Strand und Promenade; gut als lockerer Abschluss nach der Innenstadt.", GeoPoint(41.3784, 2.1925), PlaceKind.FAMILY, "1–3 Std.", "La Barceloneta, Barcelona", 82),
        TravelPlace(TravelRegion.BARCELONA, "Tibidabo", "Historischer Freizeitpark mit weitem Blick über Barcelona – besonders gut mit Kindern.", GeoPoint(41.4225, 2.1188), PlaceKind.FAMILY, "Halber Tag", "Tibidabo", 88),
        TravelPlace(TravelRegion.BARCELONA, "CosmoCaixa", "Interaktives Wissenschaftsmuseum; starke Alternative bei Hitze oder Regen.", GeoPoint(41.4130, 2.1312), PlaceKind.FAMILY, "3–4 Std.", "CosmoCaixa Barcelona", 86),
        TravelPlace(TravelRegion.BARCELONA, "Maremagnum", "Zentraler Einkauf am Hafen mit Restaurants; leicht mit Altstadt und Barceloneta kombinierbar.", GeoPoint(41.3758, 2.1825), PlaceKind.SHOPPING, "1–2 Std.", "Port Vell", 92),
        TravelPlace(TravelRegion.BARCELONA, "La Roca Village", "Outlet außerhalb Barcelonas; nur sinnvoll, wenn gezieltes Shopping geplant ist.", GeoPoint(41.6105, 2.3433), PlaceKind.SHOPPING, "2–4 Std.", "La Roca del Vallès", 78),

        TravelPlace(TravelRegion.ANDORRA, "Roc del Quer", "Spektakulärer Aussichtssteg über dem Tal; kurzer Weg, sehr hoher Erlebniswert.", GeoPoint(42.5670, 1.5905), PlaceKind.HIGHLIGHT, "1–1,5 Std.", "Canillo", 100),
        TravelPlace(TravelRegion.ANDORRA, "Tibetische Brücke Canillo", "Lange Hängebrücke mit Bergpanorama; Eintritt und Zeitfenster vorher prüfen.", GeoPoint(42.5835, 1.6460), PlaceKind.FAMILY, "2–3 Std.", "Canillo", 98),
        TravelPlace(TravelRegion.ANDORRA, "Caldea", "Große Thermenlandschaft in Escaldes – ideal nach Wandern oder bei schlechtem Wetter.", GeoPoint(42.5115, 1.5379), PlaceKind.FAMILY, "3–4 Std.", "Caldea", 94),
        TravelPlace(TravelRegion.ANDORRA, "Altstadt Andorra la Vella", "Kompakter Rundgang durch Barri Antic, Casa de la Vall und kleine Plätze.", GeoPoint(42.5063, 1.5218), PlaceKind.HIGHLIGHT, "1,5–3 Std.", "Andorra la Vella", 88),
        TravelPlace(TravelRegion.ANDORRA, "Incles-Tal", "Sehr schönes Gletschertal mit familiengeeigneten Wegen und Bergkulisse.", GeoPoint(42.6043, 1.6875), PlaceKind.NATURE, "Halber Tag", "Incles", 95),
        TravelPlace(TravelRegion.ANDORRA, "Tristaina Solar Viewpoint", "Aussichtspunkt hoch über den Bergseen; Anfahrt und Liftbetrieb vorher prüfen.", GeoPoint(42.6378, 1.4807), PlaceKind.NATURE, "Halber Tag", "Tristaina lakes", 90),
        TravelPlace(TravelRegion.ANDORRA, "Naturpark Sorteny", "Ruhigeres Tal mit Wanderwegen, Pflanzenwelt und weniger Stadtbetrieb.", GeoPoint(42.6208, 1.5606), PlaceKind.NATURE, "Halber Tag", "Sorteny Valley Nature Park", 84),
        TravelPlace(TravelRegion.ANDORRA, "Avinguda Meritxell", "Zentrale Einkaufsstraße für Parfüm, Elektronik, Kleidung und Reisebedarf.", GeoPoint(42.5082, 1.5302), PlaceKind.SHOPPING, "1–3 Std.", "Andorra la Vella", 98),
        TravelPlace(TravelRegion.ANDORRA, "Illa Carlemany", "Modernes Einkaufszentrum mit Supermarkt, Gastronomie und Parkhaus.", GeoPoint(42.5095, 1.5395), PlaceKind.SHOPPING, "1–2 Std.", "Escaldes-Engordany", 92),
        TravelPlace(TravelRegion.ANDORRA, "Pyrénées Andorra", "Großes Kaufhaus nahe Altstadt; praktisch für Lebensmittel und zollbegünstigte Waren.", GeoPoint(42.5070, 1.5228), PlaceKind.SHOPPING, "1–2 Std.", "Andorra la Vella", 88),

        TravelPlace(TravelRegion.PARIS, "Eiffelturm", "Pflichtpunkt mit Trocadéro-Blick; Auffahrt nur mit gebuchtem Zeitfenster entspannt.", GeoPoint(48.8584, 2.2945), PlaceKind.HIGHLIGHT, "1,5–3 Std.", "Eiffel Tower", 100),
        TravelPlace(TravelRegion.PARIS, "Seine-Fahrt", "Sehr entspannte Familienrunde mit vielen Hauptsehenswürdigkeiten ohne lange Fußwege.", GeoPoint(48.8590, 2.2920), PlaceKind.FAMILY, "1–1,5 Std.", "Seine", 98),
        TravelPlace(TravelRegion.PARIS, "Montmartre & Sacré-Cœur", "Aussicht, kleine Gassen und Künstlerplatz; am besten früh oder abends.", GeoPoint(48.8867, 2.3431), PlaceKind.HIGHLIGHT, "2–3 Std.", "Montmartre", 92),
        TravelPlace(TravelRegion.PARIS, "Arc de Triomphe", "Starker Blick über die Stadt und Champs-Élysées; gut per Metro erreichbar.", GeoPoint(48.8738, 2.2950), PlaceKind.HIGHLIGHT, "1–2 Std.", "Arc de Triomphe", 90),
        TravelPlace(TravelRegion.PARIS, "Louvre & Tuilerien", "Auch ohne Museumsbesuch eine sehr gute Kombination aus Architektur, Park und Spaziergang.", GeoPoint(48.8606, 2.3376), PlaceKind.FAMILY, "2–4 Std.", "Louvre Palace", 94),
        TravelPlace(TravelRegion.PARIS, "Jardin du Luxembourg", "Erholsame Pause mit viel Platz; passend für Kinder und einen stressarmen Nachmittag.", GeoPoint(48.8462, 2.3372), PlaceKind.FAMILY, "1–2 Std.", "Jardin du Luxembourg", 86),
        TravelPlace(TravelRegion.PARIS, "Cité des Sciences", "Großes Wissenschaftsmuseum und starke Regen- oder Hitzoption für Familien.", GeoPoint(48.8956, 2.3880), PlaceKind.FAMILY, "3–5 Std.", "Cité des Sciences et de l'Industrie", 84),
        TravelPlace(TravelRegion.PARIS, "Galeries Lafayette", "Kaufhaus mit kostenloser Dachterrasse und gutem Blick über Paris.", GeoPoint(48.8738, 2.3320), PlaceKind.SHOPPING, "1–2 Std.", "Galeries Lafayette Haussmann", 92),
        TravelPlace(TravelRegion.PARIS, "Westfield Les 4 Temps", "Großes Einkaufszentrum in La Défense – praktisch bei schlechtem Wetter und mit Parkhaus.", GeoPoint(48.8918, 2.2382), PlaceKind.SHOPPING, "1–3 Std.", "Les Quatre Temps", 86)
    )

    fun forRegion(region: TravelRegion): List<TravelPlace> = places
        .filter { it.region == region }
        .sortedByDescending { it.priority }

    fun nearestRegion(location: GeoPoint?): TravelRegion {
        if (location == null) return TravelRegion.CANET
        return TravelRegion.entries.minBy { Geo.distanceM(location, it.center) }
    }

    fun distanceKm(location: GeoPoint?, place: TravelPlace): Double? =
        location?.let { Geo.distanceM(it, place.point) / 1000.0 }
}
