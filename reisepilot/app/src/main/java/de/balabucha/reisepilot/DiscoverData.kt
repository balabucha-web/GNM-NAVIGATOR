package de.balabucha.reisepilot

enum class TravelRegion(
    val label: String,
    val center: GeoPoint,
    val radiusKm: Int,
    val shortPlan: String,
    val logistics: String,
    val imageFallback: String
) {
    CANET(
        "Canet & Umgebung",
        GeoPoint(42.7069, 3.0182),
        75,
        "Stressarm: Strand oder Oniria → abends Canet. Für einen halben Tag Collioure oder Perpignan; für einen ganzen Familientag Sigean.",
        "Vom Malibu Village zuerst Ziele bis etwa 45 Minuten bevorzugen. Für Collioure und Barcelona früh starten; Strandparkplätze füllen sich vormittags.",
        "Canet-en-Roussillon Mediterranean coast"
    ),
    BARCELONA(
        "Barcelona",
        GeoPoint(41.3874, 2.1686),
        75,
        "Ohne Hetze: bewachtes Parkhaus Moll de la Fusta → Gotisches Viertel → Hafen/Maremagnum → eine große Attraktion.",
        "Nicht quer durch die Stadt fahren. Auto einmal in einem bewachten BSM-Parkhaus abstellen und den Rest zu Fuß oder per Metro erledigen.",
        "Barcelona skyline Sagrada Familia"
    ),
    ANDORRA(
        "Andorra",
        GeoPoint(42.5063, 1.5218),
        75,
        "Top-Kombination: Roc del Quer → Meritxell oder Engolasters → Andorra la Vella mit Einkauf. Für 5–7 km: Camí de les Pardines.",
        "Bergstraßen kosten Zeit. Pro Tag höchstens zwei Bergziele plus Einkauf wählen. Lift-, Brücken- und Parkplatzstatus morgens prüfen.",
        "Andorra Pyrenees mountains"
    ),
    PARIS(
        "Paris",
        GeoPoint(48.8566, 2.3522),
        80,
        "Stressarm: Metro zum Trocadéro/Eiffelturm → Seine-Fahrt → ein Viertel. Pro Tag höchstens zwei große Programmpunkte.",
        "Auto am Hotel lassen. In Paris Metro nutzen; Zeitfenster für Eiffelturm, Louvre oder Sainte-Chapelle vorher buchen.",
        "Paris Eiffel Tower Seine"
    )
}

enum class PlaceKind(val label: String) {
    HIGHLIGHT("Top-Ziele"),
    FAMILY("Mit Kindern"),
    NATURE("Natur & Wege"),
    QUICK("Kurz & spontan"),
    RAIN("Regen / Hitze"),
    SHOPPING("Einkaufen")
}

data class TravelPlace(
    val region: TravelRegion,
    val title: String,
    val description: String,
    val point: GeoPoint,
    val kind: PlaceKind,
    val duration: String,
    val imageQuery: String,
    val tip: String = "",
    val priority: Int = 50
)

object DestinationCatalog {
    val places: List<TravelPlace> = listOf(
        TravelPlace(TravelRegion.CANET, "Strand & Promenade Canet", "Breiter Sandstrand für einen unkomplizierten Bade-, Eis- oder Abendspaziergang direkt am Urlaubsort.", GeoPoint(42.6969, 3.0355), PlaceKind.HIGHLIGHT, "1–4 Std.", "Canet-en-Roussillon beach", "Am entspanntesten morgens oder ab etwa 17 Uhr.", 100),
        TravelPlace(TravelRegion.CANET, "Oniria Aquarium", "Modernes Aquarium am Hafen und die stärkste wetterunabhängige Kinderoption in unmittelbarer Nähe.", GeoPoint(42.6993, 3.0374), PlaceKind.RAIN, "2–3 Std.", "Oniria aquarium Canet-en-Roussillon", "Gut mit Hafen und Strandpromenade kombinierbar.", 98),
        TravelPlace(TravelRegion.CANET, "Fischerdorf & Étang", "Traditionelle Fischerhütten und ruhige Lagunenlandschaft; passend für einen kurzen Naturausflug ohne Tagesprogramm.", GeoPoint(42.6704, 3.0063), PlaceKind.NATURE, "1,5–2,5 Std.", "Etang de Canet Saint Nazaire fishermen village", "Sonnenschutz und Wasser mitnehmen.", 94),
        TravelPlace(TravelRegion.CANET, "Collioure", "Kompakter Küstenort mit Burg, Hafen, Altstadt und sehr schöner Abendstimmung – eines der besten Ziele der Region.", GeoPoint(42.5250, 3.0833), PlaceKind.HIGHLIGHT, "3–5 Std.", "Collioure harbour France", "Früh hinfahren und außerhalb des engen Zentrums parken.", 99),
        TravelPlace(TravelRegion.CANET, "Perpignan Altstadt & Castillet", "Kurzer Stadtbummel mit Castillet, Plätzen und Cafés; deutlich kompakter als ein voller Barcelona-Tag.", GeoPoint(42.6998, 2.8956), PlaceKind.QUICK, "2–4 Std.", "Le Castillet Perpignan", "Mit dem Palast der Könige von Mallorca kombinieren.", 88),
        TravelPlace(TravelRegion.CANET, "Palast der Könige von Mallorca", "Historische Festungsanlage über Perpignan mit Innenhöfen und weitem Blick Richtung Pyrenäen.", GeoPoint(42.6937, 2.8954), PlaceKind.RAIN, "1,5–2,5 Std.", "Palace of the Kings of Majorca Perpignan", "Teilweise innen; mittags trotzdem heiß.", 84),
        TravelPlace(TravelRegion.CANET, "Réserve Africaine de Sigean", "Großer Safari- und Tierpark mit Auto- und Fußbereich; besonders passend für einen ganzen Tag mit Kindern.", GeoPoint(43.0624, 2.9498), PlaceKind.FAMILY, "5–7 Std.", "Reserve Africaine de Sigean animals", "Früh starten und Getränke sowie Snacks einpacken.", 96),
        TravelPlace(TravelRegion.CANET, "Aqualand Saint-Cyprien", "Großer Wasserpark für einen aktiven Familientag, wenn Strand allein zu langweilig wird.", GeoPoint(42.6109, 3.0346), PlaceKind.FAMILY, "4–7 Std.", "Aqualand Saint Cyprien", "Öffnung, Mindestgrößen und Tickets vorher prüfen.", 87),
        TravelPlace(TravelRegion.CANET, "Les Orgues d’Ille-sur-Têt", "Ungewöhnliche Felsformationen mit kurzem Rundweg; landschaftlich stark und ohne lange Bergwanderung machbar.", GeoPoint(42.6814, 2.6219), PlaceKind.NATURE, "2–3 Std.", "Orgues Ille-sur-Tet", "Kaum Schatten – nur morgens oder später am Tag.", 90),
        TravelPlace(TravelRegion.CANET, "Cap Leucate & Klippenweg", "Meerblick, Klippen und kurze Wege; gute spontane Naturalternative nördlich von Canet.", GeoPoint(42.9103, 3.0590), PlaceKind.NATURE, "2–4 Std.", "Cap Leucate cliffs", "Bei starkem Wind lieber auf ein anderes Ziel wechseln.", 82),
        TravelPlace(TravelRegion.CANET, "Port-Vendres & Cap Béar", "Hafen, Küstenstraße und Aussichtspunkte für einen entspannten Halbtagesausflug südlich von Collioure.", GeoPoint(42.5180, 3.1169), PlaceKind.QUICK, "2–4 Std.", "Port Vendres Cap Bear", "Ideal, wenn Collioure zu voll wirkt.", 80),
        TravelPlace(TravelRegion.CANET, "Festung Salses", "Massive Festung mit kühlen Innenräumen und kurzer Besichtigung; sinnvoll bei Hitze oder wechselhaftem Wetter.", GeoPoint(42.8397, 2.9180), PlaceKind.RAIN, "1,5–2,5 Std.", "Forteresse de Salses", "Mit Leucate kombinierbar.", 81),
        TravelPlace(TravelRegion.CANET, "Markt Canet-Plage", "Lokaler Markt für Obst, Käse, Snacks und Urlaubsstimmung ohne zusätzliche lange Autofahrt.", GeoPoint(42.6997, 3.0318), PlaceKind.QUICK, "30–90 Min.", "Canet-en-Roussillon market", "Markttage und Uhrzeiten vor Ort prüfen.", 85),
        TravelPlace(TravelRegion.CANET, "Intermarché Canet", "Praktischer Großeinkauf für Wasser, Frühstück und Reisevorräte, meist günstiger als kleine Strandläden.", GeoPoint(42.7032, 3.0108), PlaceKind.SHOPPING, "30–60 Min.", "Intermarche supermarket France", "Direkt nach Ankunft oder früh am Vormittag.", 100),
        TravelPlace(TravelRegion.CANET, "Lidl Canet", "Schneller günstiger Lebensmitteleinkauf mit kleinem Umweg vom Malibu Village.", GeoPoint(42.7070, 3.0094), PlaceKind.SHOPPING, "20–40 Min.", "Lidl France supermarket", "Gut für den kleinen Nachkauf.", 94),
        TravelPlace(TravelRegion.CANET, "Carrefour Claira / Salanca", "Großes Einkaufsgebiet für umfangreichen Einkauf, Kleidung und Dinge, die in Canet nicht verfügbar sind.", GeoPoint(42.7757, 2.9952), PlaceKind.SHOPPING, "1–3 Std.", "Centre commercial Salanca Claira", "Nur bei echtem Bedarf – sonst unnötiger Zeitfresser.", 78),
        TravelPlace(TravelRegion.CANET, "Anse de Paulilles", "Geschützte kleine Bucht zwischen Weinbergen und Felsküste mit Strand, kurzen Wegen und schöner Landschaft südlich von Collioure.", GeoPoint(42.5010, 3.1284), PlaceKind.HIGHLIGHT, "2–4 Std.", "Anse de Paulilles France", "Früh kommen; die begrenzten Parkplätze füllen sich an warmen Tagen schnell.", 93),
        TravelPlace(TravelRegion.CANET, "Villefranche-de-Conflent", "Vollständig ummauerter Bergort mit Festungscharakter, kleinen Gassen und deutlichem Kontrast zum Strandurlaub.", GeoPoint(42.5872, 2.3672), PlaceKind.HIGHLIGHT, "3–5 Std.", "Villefranche de Conflent France", "Mit Grottes des Canalettes verbinden, wenn ein ganzer Ausflugstag geplant ist.", 91),
        TravelPlace(TravelRegion.CANET, "Banyuls & Biodiversarium", "Küstenort mit Meerblick und naturkundlichem Angebot; eine ruhige Alternative zu den volleren Orten rund um Collioure.", GeoPoint(42.4824, 3.1289), PlaceKind.FAMILY, "3–5 Std.", "Banyuls sur Mer Biodiversarium", "Vorher prüfen, welche Bereiche des Biodiversariums am Besuchstag geöffnet sind.", 86),
        TravelPlace(TravelRegion.CANET, "Gorges de Galamus", "Spektakuläre enge Schlucht mit Aussichtspunkten und kurzen Wegen für einen landschaftlich starken Ausflug ins Hinterland.", GeoPoint(42.8350, 2.4815), PlaceKind.NATURE, "3–5 Std.", "Gorges de Galamus France", "Straßenbreite, Zufahrt und Wetter prüfen; nicht bei Gewitter oder Zeitdruck fahren.", 88),
        TravelPlace(TravelRegion.CANET, "Lac de Villeneuve-de-la-Raho", "Großer See mit Uferwegen, Picknickmöglichkeiten und Badebereichen als entspannte Süßwasser-Alternative zum Meer.", GeoPoint(42.6369, 2.9107), PlaceKind.QUICK, "2–4 Std.", "Lac Villeneuve de la Raho", "Ideal für einen ruhigen Nachmittag; ausgewiesene Badezonen und Regeln beachten.", 83),
        TravelPlace(TravelRegion.CANET, "Luna Park Argelès", "Abendlicher Freizeitpark mit Fahrgeschäften und typischer Urlaubsatmosphäre, besonders interessant für die Kinder.", GeoPoint(42.5685, 3.0427), PlaceKind.FAMILY, "2–4 Std.", "Luna Park Argeles sur Mer", "Saisonzeiten und Öffnung am selben Tag prüfen; eher als Abendprogramm einplanen.", 84),

        TravelPlace(TravelRegion.BARCELONA, "Sagrada Família", "Gaudís Wahrzeichen und der stärkste einzelne Programmpunkt; innen nur mit festem Zeitfenster sinnvoll.", GeoPoint(41.4036, 2.1744), PlaceKind.HIGHLIGHT, "1,5–2,5 Std.", "Sagrada Familia Barcelona", "Ticket vorher buchen; nicht zusätzlich drei weitere Großziele planen.", 100),
        TravelPlace(TravelRegion.BARCELONA, "Park Güell", "Mosaike, Gaudí-Architektur und Stadtblick; mit Kindern gut, aber an heißen Tagen körperlich spürbar.", GeoPoint(41.4145, 2.1527), PlaceKind.HIGHLIGHT, "2–3 Std.", "Park Guell Barcelona", "Zeitfenster reservieren und Wasser mitnehmen.", 96),
        TravelPlace(TravelRegion.BARCELONA, "Gotisches Viertel & Kathedrale", "Schmale Gassen, Plätze und Altstadtatmosphäre – ideal mit Hafen und Rambla kombinierbar.", GeoPoint(41.3839, 2.1763), PlaceKind.HIGHLIGHT, "2–4 Std.", "Gothic Quarter Barcelona cathedral", "Auto im BSM Moll de la Fusta lassen.", 95),
        TravelPlace(TravelRegion.BARCELONA, "Montjuïc, Seilbahn & Burg", "Aussicht, Gärten, Olympiagelände und Burg bieten viel Abwechslung ohne dauernden Innenstadttrubel.", GeoPoint(41.3636, 2.1651), PlaceKind.NATURE, "3–5 Std.", "Montjuic cable car Barcelona", "Seilbahnstatus prüfen; nicht mit Park Güell am selben heißen Nachmittag kombinieren.", 93),
        TravelPlace(TravelRegion.BARCELONA, "L’Aquàrium Barcelona", "Großes Aquarium am Hafen mit Haifischtunnel; sehr passende Familien- und Schlechtwetteroption.", GeoPoint(41.3762, 2.1840), PlaceKind.RAIN, "2–3 Std.", "Barcelona Aquarium Port Vell", "Direkt mit Maremagnum und Altstadt kombinierbar.", 96),
        TravelPlace(TravelRegion.BARCELONA, "CosmoCaixa", "Interaktives Wissenschaftsmuseum mit Regenwaldhalle; stark bei Hitze, Regen oder wenn die Kinder genug von Altstadt haben.", GeoPoint(41.4130, 2.1312), PlaceKind.RAIN, "3–4 Std.", "CosmoCaixa Barcelona", "Eigenes Ziel; nicht sinnvoll zwischen Innenstadtpunkten quetschen.", 94),
        TravelPlace(TravelRegion.BARCELONA, "Tibidabo", "Historischer Freizeitpark mit weitem Blick über Barcelona und Attraktionen für Kinder und Erwachsene.", GeoPoint(41.4225, 2.1188), PlaceKind.FAMILY, "4–7 Std.", "Tibidabo amusement park Barcelona", "Als ganzen Familientag behandeln.", 91),
        TravelPlace(TravelRegion.BARCELONA, "Parc de la Ciutadella & Arc de Triomf", "Grünanlage, See, Spielmöglichkeiten und Triumphbogen – leicht, kostenlos und spontan einbaubar.", GeoPoint(41.3882, 2.1875), PlaceKind.QUICK, "1–2,5 Std.", "Parc de la Ciutadella Barcelona", "Gut als Pause zwischen Altstadt und Strand.", 89),
        TravelPlace(TravelRegion.BARCELONA, "Poble Espanyol", "Überschaubares Freilichtmuseum mit Architektur, Handwerk und Gastronomie auf dem Montjuïc.", GeoPoint(41.3687, 2.1489), PlaceKind.RAIN, "2–3 Std.", "Poble Espanyol Barcelona", "Teilweise draußen, aber deutlich ruhiger als die Innenstadt.", 82),
        TravelPlace(TravelRegion.BARCELONA, "Laberint d’Horta", "Historischer Garten mit echtem Heckenlabyrinth; ruhige und günstigere Familienalternative.", GeoPoint(41.4397, 2.1472), PlaceKind.FAMILY, "1,5–2,5 Std.", "Parc del Laberint d Horta", "Nicht zentral – nur wählen, wenn bewusst etwas Ruhiges gesucht wird.", 83),
        TravelPlace(TravelRegion.BARCELONA, "Barceloneta & Strandpromenade", "Lockerer Abschluss mit Meer, Promenade und Essen, ohne noch eine weitere Eintrittsattraktion zu benötigen.", GeoPoint(41.3784, 2.1925), PlaceKind.QUICK, "1–3 Std.", "Barceloneta beach Barcelona", "Wertsachen eng am Körper behalten.", 86),
        TravelPlace(TravelRegion.BARCELONA, "Mercat de la Boqueria", "Bunter Markt für Snacks und einen kurzen Eindruck; eher Zwischenstopp als eigenes Tagesziel.", GeoPoint(41.3817, 2.1715), PlaceKind.QUICK, "30–60 Min.", "La Boqueria Barcelona", "Früh oder außerhalb der Hauptzeit angenehmer.", 80),
        TravelPlace(TravelRegion.BARCELONA, "Maremagnum", "Zentrales Einkaufszentrum am Hafen mit Restaurants; logisch mit Aquarium und Altstadt kombinierbar.", GeoPoint(41.3758, 2.1825), PlaceKind.SHOPPING, "1–2 Std.", "Maremagnum Barcelona", "Kein zusätzlicher Stadtweg nötig.", 94),
        TravelPlace(TravelRegion.BARCELONA, "Westfield Glòries", "Modernes Einkaufszentrum mit großer Auswahl und weniger touristischem Gedränge als die Altstadt.", GeoPoint(41.4037, 2.1914), PlaceKind.SHOPPING, "1–3 Std.", "Westfield Glories Barcelona", "Gut bei Hitze oder für gezielten Einkauf.", 84),
        TravelPlace(TravelRegion.BARCELONA, "Diagonal Mar", "Großes klimatisiertes Einkaufszentrum nahe Meer, praktisch bei schlechtem Wetter und mit Parkmöglichkeiten.", GeoPoint(41.4107, 2.2162), PlaceKind.SHOPPING, "1–3 Std.", "Diagonal Mar shopping centre Barcelona", "Nur wählen, wenn Shopping wirklich geplant ist.", 82),
        TravelPlace(TravelRegion.BARCELONA, "La Roca Village", "Outlet außerhalb Barcelonas; sinnvoll auf der Rückfahrt oder als bewusst geplanter Shoppingblock.", GeoPoint(41.6105, 2.3433), PlaceKind.SHOPPING, "2–4 Std.", "La Roca Village outlet", "Nicht mit einem vollen Innenstadtprogramm verbinden.", 76),
        TravelPlace(TravelRegion.BARCELONA, "Casa Batlló", "Sehr bildstarkes Gaudí-Haus am Passeig de Gràcia mit ungewöhnlichen Räumen und einer familienfreundlich inszenierten Besichtigung.", GeoPoint(41.3917, 2.1649), PlaceKind.HIGHLIGHT, "1,5–2,5 Std.", "Casa Batllo Barcelona facade", "Zeitfenster buchen und höchstens mit einem weiteren großen Gaudí-Ziel kombinieren.", 97),
        TravelPlace(TravelRegion.BARCELONA, "La Pedrera", "Gaudís geschwungener Wohnblock mit Innenhöfen und markanter Dachlandschaft; architektonisch stark und zentral gelegen.", GeoPoint(41.3954, 2.1619), PlaceKind.HIGHLIGHT, "1,5–2,5 Std.", "Casa Mila La Pedrera Barcelona", "Casa Batlló und La Pedrera nicht beide unter Zeitdruck in denselben Vormittag quetschen.", 92),
        TravelPlace(TravelRegion.BARCELONA, "Recinte Modernista Sant Pau", "Weitläufiges Jugendstil-Ensemble nahe der Sagrada Família mit deutlich weniger Gedränge und vielen Fotomotiven.", GeoPoint(41.4117, 2.1744), PlaceKind.QUICK, "1,5–2,5 Std.", "Hospital Sant Pau Barcelona modernist", "Sehr logisch mit der Sagrada Família über die Avinguda de Gaudí verbinden.", 90),
        TravelPlace(TravelRegion.BARCELONA, "Museu Marítim", "Schifffahrtsmuseum in historischen Werfthallen nahe der Rambla; gute Innenoption mit großen Exponaten für Kinder.", GeoPoint(41.3758, 2.1762), PlaceKind.RAIN, "2–3 Std.", "Maritime Museum Barcelona Drassanes", "Mit Hafen und Kolumbus-Säule kombinieren; bei Hitze besonders angenehm.", 87),
        TravelPlace(TravelRegion.BARCELONA, "Schokoladenmuseum", "Kompaktes Museum mit Schokoladenfiguren und Geschichte des Kakaos; ein unkomplizierter Programmpunkt für Kinder.", GeoPoint(41.3875, 2.1811), PlaceKind.FAMILY, "1–2 Std.", "Museu de la Xocolata Barcelona", "Gut mit Born, Ciutadella und Arc de Triomf kombinierbar.", 85),
        TravelPlace(TravelRegion.BARCELONA, "Sitges", "Eleganter Küstenort südlich von Barcelona mit Altstadt, Promenade und Strand als entspannte Alternative zur Großstadt.", GeoPoint(41.2370, 1.8058), PlaceKind.NATURE, "4–7 Std.", "Sitges beach old town", "Als eigenen Halbtages- oder Tagesausflug behandeln und nicht nach einem vollen Barcelonatag anhängen.", 84),

        TravelPlace(TravelRegion.ANDORRA, "Roc del Quer", "Spektakulärer Aussichtssteg über dem Tal mit sehr hohem Erlebniswert bei relativ kurzem Zeitaufwand.", GeoPoint(42.5670, 1.5905), PlaceKind.HIGHLIGHT, "1–1,5 Std.", "Roc del Quer Andorra viewpoint", "Morgens zuerst; Wetter und Zufahrt prüfen.", 100),
        TravelPlace(TravelRegion.ANDORRA, "Tibetische Brücke Canillo", "Lange Hängebrücke mit Bergpanorama und echtem Abenteuerfaktor für die Familie.", GeoPoint(42.5835, 1.6460), PlaceKind.FAMILY, "2–3 Std.", "Tibetan bridge Canillo Andorra", "Ticket, Shuttle und Zeitfenster vorher prüfen.", 98),
        TravelPlace(TravelRegion.ANDORRA, "Camí de les Pardines & Engolasters", "Leichter Panoramaweg zum See, passend zu eurem Wunsch nach etwa fünf bis sieben Kilometern ohne Extremwanderung.", GeoPoint(42.5298, 1.5716), PlaceKind.NATURE, "3–4 Std.", "Engolasters lake Pardines trail Andorra", "Hin- und Rückweg je nach Startpunkt ungefähr 5–7 km.", 99),
        TravelPlace(TravelRegion.ANDORRA, "Incles-Tal", "Eines der schönsten Gletschertäler Andorras mit familiengeeigneten Wegen und flexibler Streckenlänge.", GeoPoint(42.6043, 1.6875), PlaceKind.NATURE, "3–5 Std.", "Incles valley Andorra", "Im Sommer Zufahrt und Shuttle-Regelung prüfen.", 96),
        TravelPlace(TravelRegion.ANDORRA, "Tristaina-Seen & Solar-Aussichtspunkt", "Hochalpines Panorama mit Bergseen und besonderem Aussichtsbauwerk – landschaftlich eines der stärksten Ziele.", GeoPoint(42.6378, 1.4807), PlaceKind.HIGHLIGHT, "4–6 Std.", "Tristaina solar viewpoint lakes Andorra", "Liftbetrieb, Wetter und letzte Talfahrt vorher prüfen.", 94),
        TravelPlace(TravelRegion.ANDORRA, "Ruta del Ferro", "Leichter Kultur- und Naturweg entlang der Eisen-Geschichte; gut für eine flexible Familienwanderung.", GeoPoint(42.6070, 1.5328), PlaceKind.NATURE, "2–4 Std.", "Ruta del Ferro Andorra", "Strecke nach Energie der Kinder verkürzen oder verlängern.", 90),
        TravelPlace(TravelRegion.ANDORRA, "Naturpark Sorteny", "Ruhigeres Tal mit Bergpflanzen, Wasserläufen und Wanderwegen abseits der Einkaufsachsen.", GeoPoint(42.6208, 1.5606), PlaceKind.NATURE, "3–5 Std.", "Sorteny Valley Nature Park Andorra", "Gute Alternative, wenn Tristaina zu voll ist.", 88),
        TravelPlace(TravelRegion.ANDORRA, "Naturland", "Outdoor-Familienpark mit Tobotronc und weiteren Aktivitäten südlich von Andorra la Vella.", GeoPoint(42.4356, 1.5237), PlaceKind.FAMILY, "4–7 Std.", "Naturland Andorra Tobotronc", "Als eigenen Tagesblock planen; Öffnung und Größenlimits prüfen.", 92),
        TravelPlace(TravelRegion.ANDORRA, "Mon(t) Magic Canillo", "Sommeraktivitäten im Grandvalira-Gebiet mit Bergbahn, Natur und familiengeeigneten Angeboten.", GeoPoint(42.5662, 1.6007), PlaceKind.FAMILY, "4–6 Std.", "Mont Magic Canillo Andorra", "Betriebstage und enthaltene Aktivitäten vorher prüfen.", 86),
        TravelPlace(TravelRegion.ANDORRA, "Caldea", "Große Thermenlandschaft in Escaldes – ideal nach Wandern oder bei Regen und kühlerem Wetter.", GeoPoint(42.5115, 1.5379), PlaceKind.RAIN, "3–4 Std.", "Caldea Andorra", "Altersbereiche und Familienzeiten für beide Kinder prüfen.", 95),
        TravelPlace(TravelRegion.ANDORRA, "Palau de Gel Canillo", "Eislaufhalle und Freizeitangebot als klare Schlechtwetteralternative in Canillo.", GeoPoint(42.5665, 1.5999), PlaceKind.RAIN, "1,5–3 Std.", "Palau de Gel Andorra Canillo", "Öffnungszeiten und öffentliche Laufzeiten prüfen.", 82),
        TravelPlace(TravelRegion.ANDORRA, "Automobilmuseum Encamp", "Kompaktes Museum mit historischen Fahrzeugen – sinnvoll bei Regen oder als kurzer Zwischenstopp.", GeoPoint(42.5343, 1.5802), PlaceKind.RAIN, "1–2 Std.", "National Automobile Museum Andorra", "Gut mit Meritxell oder Canillo kombinierbar.", 80),
        TravelPlace(TravelRegion.ANDORRA, "Ordino Altstadt", "Ruhiger historischer Ortskern mit Steinhäusern und Bergkulisse, ideal für einen kurzen Spaziergang.", GeoPoint(42.5563, 1.5330), PlaceKind.QUICK, "1–2 Std.", "Ordino Andorra village", "Mit Ruta del Ferro oder Sorteny kombinieren.", 85),
        TravelPlace(TravelRegion.ANDORRA, "Santuari de Meritxell", "Architektonisch interessantes Heiligtum und kurzer, kostenloser Kulturstopp auf dem Weg nach Canillo.", GeoPoint(42.5540, 1.5901), PlaceKind.QUICK, "45–90 Min.", "Sanctuary of Meritxell Andorra", "Sehr guter Lückenfüller zwischen zwei größeren Zielen.", 84),
        TravelPlace(TravelRegion.ANDORRA, "Altstadt Andorra la Vella", "Kompakter Rundgang durch Barri Antic, Casa de la Vall und kleine Plätze.", GeoPoint(42.5063, 1.5218), PlaceKind.QUICK, "1,5–3 Std.", "Andorra la Vella old town", "Danach zu Fuß zur Avinguda Meritxell weitergehen.", 88),
        TravelPlace(TravelRegion.ANDORRA, "Avinguda Meritxell", "Zentrale Einkaufsachse für Parfüm, Elektronik, Kleidung und Reisebedarf.", GeoPoint(42.5082, 1.5302), PlaceKind.SHOPPING, "1–3 Std.", "Avinguda Meritxell Andorra", "Preise vergleichen; nicht automatisch ist alles günstiger.", 98),
        TravelPlace(TravelRegion.ANDORRA, "Illa Carlemany", "Modernes Einkaufszentrum mit Supermarkt, Gastronomie und Parkhaus in Escaldes.", GeoPoint(42.5095, 1.5395), PlaceKind.SHOPPING, "1–2 Std.", "Illa Carlemany Andorra", "Praktisch mit Caldea kombinierbar.", 94),
        TravelPlace(TravelRegion.ANDORRA, "Pyrénées Andorra", "Großes Kaufhaus nahe der Altstadt für Lebensmittel, Parfüm und klassische Andorra-Einkäufe.", GeoPoint(42.5070, 1.5228), PlaceKind.SHOPPING, "1–2 Std.", "Pyrenees Andorra department store", "Gut erreichbar, aber Preise trotzdem vergleichen.", 90),
        TravelPlace(TravelRegion.ANDORRA, "Epizen", "Großes Einkaufszentrum südlich der Hauptstadt mit Hypermarkt und Parkhaus, praktisch auf der Durchfahrt.", GeoPoint(42.4623, 1.4910), PlaceKind.SHOPPING, "1–3 Std.", "Epizen Andorra shopping centre", "Sinnvoll vor der Ausreise Richtung Spanien.", 86),
        TravelPlace(TravelRegion.ANDORRA, "Sant Joan de Caselles", "Sehr gut erhaltene romanische Kirche direkt an der Talstraße bei Canillo und ein kurzer kultureller Stopp ohne großen Umweg.", GeoPoint(42.5708, 1.6068), PlaceKind.QUICK, "30–60 Min.", "Sant Joan de Caselles Andorra", "Mit Meritxell, Roc del Quer oder Canillo kombinieren; Öffnung vorab prüfen.", 89),
        TravelPlace(TravelRegion.ANDORRA, "Casa d’Areny-Plandolit", "Historisches Wohnhaus in Ordino mit original eingerichteten Räumen und gut verständlichem Einblick in das frühere Andorra.", GeoPoint(42.5568, 1.5324), PlaceKind.RAIN, "1–2 Std.", "Casa Areny Plandolit Andorra", "Ideal zusammen mit Ordino Altstadt; Führungs- und Öffnungszeiten vorher ansehen.", 84),
        TravelPlace(TravelRegion.ANDORRA, "Mirador de la Comella", "Schnell erreichbarer Aussichtspunkt oberhalb von Andorra la Vella mit weitem Blick über Hauptstadt und Bergketten.", GeoPoint(42.4963, 1.5265), PlaceKind.QUICK, "30–60 Min.", "Mirador de la Comella Andorra", "Sehr guter spontaner Fotostopp bei klarer Sicht und wenig Zeit.", 87),
        TravelPlace(TravelRegion.ANDORRA, "Estanys de Juclà", "Hochalpine Seenlandschaft im Incles-Tal für einen deutlich längeren Naturtag mit großem Bergpanorama.", GeoPoint(42.6104, 1.7165), PlaceKind.NATURE, "5–7 Std.", "Estanys de Jucla Andorra", "Nur bei stabilem Wetter, geeigneten Schuhen und genügend Zeit wählen.", 86),
        TravelPlace(TravelRegion.ANDORRA, "Vall del Madriu", "UNESCO-geschütztes Tal mit alten Steinwegen, Wald und Berglandschaft; die Strecke lässt sich flexibel anpassen.", GeoPoint(42.5025, 1.5658), PlaceKind.NATURE, "3–6 Std.", "Madriu Perafita Claror valley Andorra", "Für die Familie nur den passenden unteren Abschnitt wählen und Rückwegzeit großzügig planen.", 92),
        TravelPlace(TravelRegion.ANDORRA, "Bici Lab Andorra", "Modernes Fahrradmuseum in Andorra la Vella mit Technik, Geschichte und interaktiven Elementen als kompakte Innenoption.", GeoPoint(42.5079, 1.5222), PlaceKind.RAIN, "1–2 Std.", "Bici Lab Andorra museum", "Gut mit Altstadt, Casa de la Vall und Einkauf auf der Meritxell-Achse kombinieren.", 82),

        TravelPlace(TravelRegion.PARIS, "Eiffelturm & Trocadéro", "Pflichtpunkt mit starkem Blick vom Trocadéro; Auffahrt nur mit gebuchtem Zeitfenster wirklich entspannt.", GeoPoint(48.8584, 2.2945), PlaceKind.HIGHLIGHT, "1,5–3 Std.", "Eiffel Tower Trocadero Paris", "Morgens oder abends; danach direkt zur Seine-Fahrt.", 100),
        TravelPlace(TravelRegion.PARIS, "Seine-Fahrt", "Sehr entspannte Familienrunde mit vielen Hauptsehenswürdigkeiten ohne zusätzliche lange Fußwege.", GeoPoint(48.8590, 2.2920), PlaceKind.FAMILY, "1–1,5 Std.", "Seine river cruise Paris", "Perfekt nach dem Eiffelturm.", 99),
        TravelPlace(TravelRegion.PARIS, "Montmartre & Sacré-Cœur", "Aussicht, kleine Gassen und Künstlerplatz; eines der stärksten Viertel, aber mit Steigungen.", GeoPoint(48.8867, 2.3431), PlaceKind.HIGHLIGHT, "2–3 Std.", "Montmartre Sacre Coeur Paris", "Früh oder am Abend besuchen; Funiculaire nutzen.", 94),
        TravelPlace(TravelRegion.PARIS, "Arc de Triomphe", "Starker Blick über die Stadt und Champs-Élysées; kompakt und gut per Metro erreichbar.", GeoPoint(48.8738, 2.2950), PlaceKind.HIGHLIGHT, "1–2 Std.", "Arc de Triomphe Paris", "Unterführung benutzen, niemals den Kreisverkehr überqueren.", 92),
        TravelPlace(TravelRegion.PARIS, "Louvre & Tuilerien", "Auch ohne Museumsbesuch eine starke Kombination aus Architektur, Park und entspanntem Spaziergang.", GeoPoint(48.8606, 2.3376), PlaceKind.HIGHLIGHT, "2–4 Std.", "Louvre Tuileries Paris", "Museum nur mit festem Zeitfenster einplanen.", 95),
        TravelPlace(TravelRegion.PARIS, "Notre-Dame & Île de la Cité", "Kompakter Spaziergang rund um die wiedereröffnete Kathedrale, Seineufer und historische Insel.", GeoPoint(48.8530, 2.3499), PlaceKind.QUICK, "1,5–3 Std.", "Notre Dame Paris Ile de la Cite", "Mit Sainte-Chapelle kombinieren.", 94),
        TravelPlace(TravelRegion.PARIS, "Sainte-Chapelle", "Spektakuläre Glasfenster auf kleiner Fläche; sehr hoher Erlebniswert bei überschaubarer Besuchsdauer.", GeoPoint(48.8554, 2.3450), PlaceKind.RAIN, "1–1,5 Std.", "Sainte Chapelle Paris interior", "Zeitfenster buchen und Sicherheitskontrolle einrechnen.", 91),
        TravelPlace(TravelRegion.PARIS, "Jardin du Luxembourg", "Erholsame Pause mit viel Platz, Schatten und familienfreundlicher Atmosphäre im Zentrum.", GeoPoint(48.8462, 2.3372), PlaceKind.FAMILY, "1–2 Std.", "Jardin du Luxembourg Paris", "Gut nach Notre-Dame oder dem Quartier Latin.", 88),
        TravelPlace(TravelRegion.PARIS, "Cité des Sciences", "Großes Wissenschaftsmuseum und eine der besten Regen- oder Hitzoptionen für Kinder.", GeoPoint(48.8956, 2.3880), PlaceKind.RAIN, "3–5 Std.", "Cite des Sciences Paris", "Cité des Enfants passend zum Alter vorher reservieren.", 94),
        TravelPlace(TravelRegion.PARIS, "Grande Galerie de l’Évolution", "Beeindruckende Tier- und Naturhalle, kompakter als der Louvre und sehr passend für Familien.", GeoPoint(48.8422, 2.3561), PlaceKind.RAIN, "2–3 Std.", "Grande Galerie de Evolution Paris", "Mit Jardin des Plantes verbinden.", 92),
        TravelPlace(TravelRegion.PARIS, "Aquarium de Paris", "Aquarium nahe Trocadéro und damit eine gute wetterunabhängige Ergänzung rund um den Eiffelturm.", GeoPoint(48.8620, 2.2888), PlaceKind.RAIN, "1,5–2,5 Std.", "Aquarium de Paris Trocadero", "Nur wählen, wenn das Wetter schlecht ist oder die Kinder Aquarium wollen.", 84),
        TravelPlace(TravelRegion.PARIS, "Jardin d’Acclimatation", "Familienpark mit Fahrgeschäften, Spielbereichen und Grünflächen westlich des Zentrums.", GeoPoint(48.8773, 2.2634), PlaceKind.FAMILY, "4–6 Std.", "Jardin d Acclimatation Paris", "Als halben bis ganzen Familientag behandeln.", 89),
        TravelPlace(TravelRegion.PARIS, "Musée de l’Air et de l’Espace", "Flugzeuge, Raumfahrt und große Hallen – besonders passend für Kinder und vom Pariser Norden gut erreichbar.", GeoPoint(48.9474, 2.4353), PlaceKind.FAMILY, "3–5 Std.", "Musee de l Air et de l Espace Le Bourget", "Sehr gute Alternative, falls euer Hotel nördlich von Paris liegt.", 87),
        TravelPlace(TravelRegion.PARIS, "Parc des Buttes-Chaumont", "Hügeliger Stadtpark mit Aussicht und weniger touristischem Betrieb als die zentralen Gärten.", GeoPoint(48.8809, 2.3828), PlaceKind.NATURE, "1,5–3 Std.", "Parc des Buttes Chaumont Paris", "Nur bei gutem Wetter und passenden Schuhen.", 79),
        TravelPlace(TravelRegion.PARIS, "Canal Saint-Martin", "Lockerer Spaziergang am Wasser mit Brücken und Cafés, wenn ihr spontan etwas ohne Eintritt sucht.", GeoPoint(48.8721, 2.3657), PlaceKind.QUICK, "1–2 Std.", "Canal Saint Martin Paris", "Gut am späten Nachmittag.", 80),
        TravelPlace(TravelRegion.PARIS, "Galeries Lafayette Dachterrasse", "Kaufhaus mit kostenloser Dachterrasse und starkem Blick über Paris.", GeoPoint(48.8738, 2.3320), PlaceKind.SHOPPING, "1–2 Std.", "Galeries Lafayette rooftop Paris", "Dachterrasse mit Opéra Garnier verbinden.", 94),
        TravelPlace(TravelRegion.PARIS, "Westfield Les 4 Temps", "Sehr großes Einkaufszentrum in La Défense mit Parkhaus und wetterunabhängigem Angebot.", GeoPoint(48.8918, 2.2382), PlaceKind.SHOPPING, "1–3 Std.", "Westfield Les 4 Temps Paris La Defense", "Praktisch bei Regen, aber kein Pflichtziel.", 86),
        TravelPlace(TravelRegion.PARIS, "Bercy Village", "Überschaubare Fußgängerzone mit Läden und Restaurants in ehemaligen Lagerhäusern.", GeoPoint(48.8331, 2.3868), PlaceKind.SHOPPING, "1–2 Std.", "Bercy Village Paris", "Mit Parc de Bercy kombinierbar.", 79),
        TravelPlace(TravelRegion.PARIS, "Atelier des Lumières", "Großformatige immersive Projektionen in einer ehemaligen Gießerei und eine eindrucksvolle Innenoption für die ganze Familie.", GeoPoint(48.8610, 2.3800), PlaceKind.RAIN, "1,5–2,5 Std.", "Atelier des Lumieres Paris", "Programm und Zeitfenster vorher prüfen; gut mit Bastille oder Père-Lachaise kombinierbar.", 93),
        TravelPlace(TravelRegion.PARIS, "Musée Grévin", "Historisches Wachsfigurenmuseum mit vielen bekannten Persönlichkeiten und unterhaltsamer Inszenierung für Kinder.", GeoPoint(48.8719, 2.3423), PlaceKind.FAMILY, "2–3 Std.", "Musee Grevin Paris", "Bei Regen sinnvoll; außerhalb der Hauptzeiten deutlich angenehmer.", 90),
        TravelPlace(TravelRegion.PARIS, "Opéra Garnier", "Prunkvolles Opernhaus mit großer Treppe und reich ausgestatteten Sälen; auch ohne Vorstellung sehr sehenswert.", GeoPoint(48.8719, 2.3316), PlaceKind.HIGHLIGHT, "1–2 Std.", "Opera Garnier Paris interior", "Direkt mit Galeries Lafayette und deren Dachterrasse verbinden.", 91),
        TravelPlace(TravelRegion.PARIS, "Schloss Versailles", "Monumentales Schloss mit Spiegelsaal und weitläufigen Gärten als klassischer Tagesausflug westlich von Paris.", GeoPoint(48.8049, 2.1204), PlaceKind.HIGHLIGHT, "5–8 Std.", "Palace of Versailles gardens", "Als eigenen Tag planen, Tickets buchen und die langen Wege in den Gärten berücksichtigen.", 95),
        TravelPlace(TravelRegion.PARIS, "Disneyland Paris", "Zwei große Themenparks östlich von Paris und die stärkste ganztägige Erlebnisoption für die Kinder.", GeoPoint(48.8674, 2.7836), PlaceKind.FAMILY, "8–12 Std.", "Disneyland Paris castle", "Nur als bewusst geplanten ganzen Tag wählen; Tickets und Anfahrt vorher festlegen.", 94),
        TravelPlace(TravelRegion.PARIS, "Ménagerie im Jardin des Plantes", "Kompakter historischer Zoo in zentraler Lage, der sich gut mit Naturkundemuseum und Garten verbinden lässt.", GeoPoint(48.8437, 2.3598), PlaceKind.FAMILY, "2–4 Std.", "Menagerie Jardin des Plantes Paris", "Für einen Familientag mit Grande Galerie de l’Évolution kombinieren.", 88)
    )

    fun forRegion(region: TravelRegion): List<TravelPlace> = places
        .filter { it.region == region }
        .sortedByDescending { it.priority }

    fun nearestRegion(location: GeoPoint?): TravelRegion? {
        location ?: return null
        return TravelRegion.entries
            .map { it to Geo.distanceM(location, it.center) / 1000.0 }
            .filter { (region, distanceKm) -> distanceKm <= region.radiusKm }
            .minByOrNull { it.second }
            ?.first
    }

    fun distanceKm(location: GeoPoint?, place: TravelPlace): Double? =
        location?.let { Geo.distanceM(it, place.point) / 1000.0 }
}
