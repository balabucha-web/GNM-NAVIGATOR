package de.balabucha.reisepilot

/**
 * Exact real-world identities used before any broad image search. The terms are
 * deliberately independent from the German UI title: several cards describe two
 * nearby sights, use a translated name or share a name with an unrelated object.
 */
object DestinationMediaCatalog {
    private val identityQueries: Map<String, List<String>> = mapOf(
        "Strand & Promenade Canet" to listOf("Canet-en-Roussillon beach", "Canet-Plage"),
        "Oniria Aquarium" to listOf("Oniria aquarium"),
        "Fischerdorf & Étang" to listOf("Étang de Canet-Saint-Nazaire", "Village de pêcheurs de Canet"),
        "Collioure" to listOf("Collioure"),
        "Perpignan Altstadt & Castillet" to listOf("Le Castillet", "Historic centre of Perpignan"),
        "Palast der Könige von Mallorca" to listOf("Palace of the Kings of Majorca"),
        "Réserve Africaine de Sigean" to listOf("Réserve africaine de Sigean"),
        "Aqualand Saint-Cyprien" to listOf("Aqualand Saint-Cyprien"),
        "Les Orgues d’Ille-sur-Têt" to listOf("Orgues d'Ille-sur-Têt"),
        "Cap Leucate & Klippenweg" to listOf("Cap Leucate"),
        "Port-Vendres & Cap Béar" to listOf("Port-Vendres", "Cap Béar"),
        "Festung Salses" to listOf("Forteresse de Salses"),
        "Markt Canet-Plage" to listOf("Marché de Canet-en-Roussillon"),
        "Intermarché Canet" to listOf("Intermarché Canet-en-Roussillon"),
        "Lidl Canet" to listOf("Lidl Canet-en-Roussillon"),
        "Carrefour Claira / Salanca" to listOf("Centre Commercial Salanca Claira"),
        "Anse de Paulilles" to listOf("Anse de Paulilles"),
        "Villefranche-de-Conflent" to listOf("Villefranche-de-Conflent"),
        "Banyuls & Biodiversarium" to listOf("Biodiversarium", "Banyuls-sur-Mer"),
        "Gorges de Galamus" to listOf("Gorges de Galamus"),
        "Lac de Villeneuve-de-la-Raho" to listOf("Lac de Villeneuve-de-la-Raho"),
        "Luna Park Argelès" to listOf("Luna Park Argelès-sur-Mer"),

        "Sagrada Família" to listOf("Temple Expiatori de la Sagrada Família"),
        "Park Güell" to listOf("Park Güell"),
        "Gotisches Viertel & Kathedrale" to listOf("Barcelona Cathedral", "Gothic Quarter Barcelona"),
        "Montjuïc, Seilbahn & Burg" to listOf("Montjuïc Cable Car", "Montjuïc Castle"),
        "L’Aquàrium Barcelona" to listOf("Aquarium Barcelona"),
        "CosmoCaixa" to listOf("CosmoCaixa Barcelona"),
        "Tibidabo" to listOf("Tibidabo Amusement Park"),
        "Parc de la Ciutadella & Arc de Triomf" to listOf("Parc de la Ciutadella", "Arc de Triomf Barcelona"),
        "Poble Espanyol" to listOf("Poble Espanyol"),
        "Laberint d’Horta" to listOf("Parc del Laberint d'Horta"),
        "Barceloneta & Strandpromenade" to listOf("Barceloneta beach"),
        "Mercat de la Boqueria" to listOf("La Boqueria"),
        "Maremagnum" to listOf("Maremagnum Barcelona"),
        "Westfield Glòries" to listOf("Centre Comercial Glòries"),
        "Diagonal Mar" to listOf("Centre Comercial Diagonal Mar"),
        "La Roca Village" to listOf("La Roca Village"),
        "Casa Batlló" to listOf("Casa Batlló"),
        "La Pedrera" to listOf("Casa Milà"),
        "Recinte Modernista Sant Pau" to listOf("Hospital de Sant Pau"),
        "Museu Marítim" to listOf("Maritime Museum of Barcelona"),
        "Schokoladenmuseum" to listOf("Museu de la Xocolata"),
        "Sitges" to listOf("Sitges"),

        "Roc del Quer" to listOf("Mirador del Roc del Quer"),
        "Tibetische Brücke Canillo" to listOf("Pont Tibetà de Canillo"),
        "Camí de les Pardines & Engolasters" to listOf("Camí de les Pardines", "Lake Engolasters"),
        "Incles-Tal" to listOf("Vall d'Incles"),
        "Tristaina-Seen & Solar-Aussichtspunkt" to listOf("Mirador Solar de Tristaina", "Estanys de Tristaina"),
        "Ruta del Ferro" to listOf("Ruta del Ferro Andorra"),
        "Naturpark Sorteny" to listOf("Parc Natural de la Vall de Sorteny"),
        "Naturland" to listOf("Naturland Andorra"),
        "Mon(t) Magic Canillo" to listOf("Mon(t) Magic Family Park Canillo"),
        "Caldea" to listOf("Caldea"),
        "Palau de Gel Canillo" to listOf("Palau de Gel d'Andorra"),
        "Automobilmuseum Encamp" to listOf("Museu Nacional de l'Automòbil d'Andorra"),
        "Ordino Altstadt" to listOf("Ordino"),
        "Santuari de Meritxell" to listOf("Santuari de Meritxell"),
        "Altstadt Andorra la Vella" to listOf("Casa de la Vall", "Andorra la Vella old town"),
        "Avinguda Meritxell" to listOf("Avinguda Meritxell"),
        "Illa Carlemany" to listOf("Illa Carlemany"),
        "Pyrénées Andorra" to listOf("Grans Magatzems Pyrénées"),
        "Epizen" to listOf("Epizen Andorra"),
        "Sant Joan de Caselles" to listOf("Sant Joan de Caselles"),
        "Casa d’Areny-Plandolit" to listOf("Casa d'Areny-Plandolit"),
        "Mirador de la Comella" to listOf("Mirador de la Comella"),
        "Estanys de Juclà" to listOf("Estanys de Juclà"),
        "Vall del Madriu" to listOf("Madriu-Perafita-Claror Valley"),
        "Bici Lab Andorra" to listOf("Bici Lab Andorra"),

        "Eiffelturm & Trocadéro" to listOf("Eiffel Tower", "Place du Trocadéro"),
        "Seine-Fahrt" to listOf("Bateaux Mouches"),
        "Montmartre & Sacré-Cœur" to listOf("Sacré-Cœur Paris", "Montmartre"),
        "Arc de Triomphe" to listOf("Arc de Triomphe de l'Étoile"),
        "Louvre & Tuilerien" to listOf("Louvre Palace", "Tuileries Garden"),
        "Notre-Dame & Île de la Cité" to listOf("Notre-Dame de Paris", "Île de la Cité"),
        "Sainte-Chapelle" to listOf("Sainte-Chapelle"),
        "Jardin du Luxembourg" to listOf("Luxembourg Garden"),
        "Cité des Sciences" to listOf("Cité des sciences et de l'industrie"),
        "Grande Galerie de l’Évolution" to listOf("Grande galerie de l'Évolution"),
        "Aquarium de Paris" to listOf("Aquarium de Paris"),
        "Jardin d’Acclimatation" to listOf("Jardin d'Acclimatation"),
        "Musée de l’Air et de l’Espace" to listOf("Musée de l'Air et de l'Espace"),
        "Parc des Buttes-Chaumont" to listOf("Parc des Buttes-Chaumont"),
        "Canal Saint-Martin" to listOf("Canal Saint-Martin"),
        "Galeries Lafayette Dachterrasse" to listOf("Galeries Lafayette Haussmann"),
        "Westfield Les 4 Temps" to listOf("Les Quatre Temps"),
        "Bercy Village" to listOf("Bercy Village"),
        "Atelier des Lumières" to listOf("Atelier des Lumières"),
        "Musée Grévin" to listOf("Musée Grévin"),
        "Opéra Garnier" to listOf("Palais Garnier"),
        "Schloss Versailles" to listOf("Palace of Versailles"),
        "Disneyland Paris" to listOf("Disneyland Paris"),
        "Ménagerie im Jardin des Plantes" to listOf("Ménagerie du Jardin des plantes")
    )

    /* Coordinate-checked identities for the most prominent or ambiguous targets. */
    private val pinnedWikidataIds: Map<String, List<String>> = mapOf(
        "Oniria Aquarium" to listOf("Q109265407"),
        "Collioure" to listOf("Q254829"),
        "Sagrada Família" to listOf("Q48435"),
        "Park Güell" to listOf("Q212867"),
        "Gotisches Viertel & Kathedrale" to listOf("Q17155"),
        "Montjuïc, Seilbahn & Burg" to listOf("Q3324323"),
        "Roc del Quer" to listOf("Q66384866")
    )

    fun identities(place: TravelPlace): List<String> =
        identityQueries[place.title].orEmpty().ifEmpty { listOf(place.imageQuery) }

    fun wikidataIds(place: TravelPlace): List<String> = pinnedWikidataIds[place.title].orEmpty()

    internal fun hasExplicitIdentity(place: TravelPlace): Boolean = identityQueries.containsKey(place.title)
}
