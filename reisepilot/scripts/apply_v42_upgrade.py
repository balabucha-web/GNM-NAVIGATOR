from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/de/balabucha/reisepilot"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"{path}: expected marker not found: {old[:90]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


build = ROOT / "app/build.gradle.kts"
replace_once(build, 'versionCode = 9', 'versionCode = 10')
replace_once(build, 'versionName = "4.1"', 'versionName = "4.2"')

wiki = SRC / "WikiImage.kt"
wiki.write_text(r'''package de.balabucha.reisepilot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a relevant Wikimedia thumbnail for destination cards and details.
 * Requests are throttled, successful URLs are cached and transient failures are retried later.
 */
object WikiImageResolver {
    private val memory = ConcurrentHashMap<String, String>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(3)
    private const val RETRY_DELAY_MS = 10L * 60L * 1000L

    suspend fun resolve(context: Context, query: String, fallbackQuery: String): String? =
        withContext(Dispatchers.IO) {
            listOf(query, fallbackQuery)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .firstNotNullOfOrNull { resolveSingle(context.applicationContext, it) }
        }

    private suspend fun resolveSingle(context: Context, query: String): String? {
        memory[query]?.let { return it }
        val now = System.currentTimeMillis()
        if ((retryAfter[query] ?: 0L) > now) return null

        val prefs = context.getSharedPreferences("travel_images_v42", Context.MODE_PRIVATE)
        val cacheKey = "url_${query.hashCode()}"
        prefs.getString(cacheKey, null)?.takeIf(::usable)?.let {
            memory[query] = it
            return it
        }

        return gate.withPermit {
            memory[query]?.let { return@withPermit it }
            val result = runCatching {
                wikipediaSearchThumbnail(query) ?: commonsThumbnail(query)
            }.getOrNull()

            if (result != null) {
                memory[query] = result
                retryAfter.remove(query)
                prefs.edit().putString(cacheKey, result).apply()
            } else {
                retryAfter[query] = System.currentTimeMillis() + RETRY_DELAY_MS
            }
            result
        }
    }

    private fun wikipediaSearchThumbnail(query: String): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://en.wikipedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=0&gsrlimit=6" +
            "&gsrsearch=$encoded&prop=pageimages&piprop=thumbnail" +
            "&pithumbsize=1000&format=json&formatversion=2&origin=*"
        val root = JSONObject(http(endpoint))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        for (index in 0 until pages.length()) {
            val url = pages.optJSONObject(index)
                ?.optJSONObject("thumbnail")
                ?.optString("source")
                .orEmpty()
            if (usable(url)) return url
        }
        return null
    }

    private fun commonsThumbnail(query: String): String? {
        val search = "$query -logo -flag -map -icon"
        val encoded = URLEncoder.encode(search, "UTF-8")
        val endpoint = "https://commons.wikimedia.org/w/api.php" +
            "?action=query&generator=search&gsrnamespace=6&gsrlimit=12" +
            "&gsrsearch=$encoded&prop=imageinfo&iiprop=url%7Cmime%7Csize" +
            "&iiurlwidth=1000&format=json&formatversion=2&origin=*"
        val root = JSONObject(http(endpoint))
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        for (index in 0 until pages.length()) {
            val info = pages.optJSONObject(index)
                ?.optJSONArray("imageinfo")
                ?.optJSONObject(0)
                ?: continue
            val mime = info.optString("mime").lowercase()
            if (mime !in setOf("image/jpeg", "image/png", "image/webp")) continue
            if (info.optInt("width", 0) in 1..399) continue
            val thumbnail = info.optString("thumburl")
            val original = info.optString("url")
            val candidate = thumbnail.takeIf(::usable) ?: original.takeIf(::usable)
            if (candidate != null) return candidate
        }
        return null
    }

    private fun usable(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val lower = url.substringBefore('?').lowercase()
        return listOf(".pdf", ".djvu", ".tif", ".tiff", ".svg", ".gif").none { lower.endsWith(it) }
    }

    private fun http(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "de,en;q=0.8")
            connection.setRequestProperty("User-Agent", "ReisePilot/4.2 Android family travel app")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Wikimedia HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }
}
''', encoding="utf-8")

discover = SRC / "DiscoverScreen.kt"
replace_once(discover, 'subtitle = "Schnelle Ideen ohne Ladechaos · Bild erst im Zieldetail"', 'subtitle = "Mehr Ausflugsziele · Vorschaubilder und Route direkt verfügbar"')
thumbnail_fn = r'''
@Composable
private fun PlaceThumbnail(place: TravelPlace, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var finished by remember(place.region, place.title) { mutableStateOf(false) }
    val imageUrl by produceState<String?>(initialValue = null, place.imageQuery, place.region.imageFallback) {
        value = runCatching { WikiImageResolver.resolve(context, place.imageQuery, place.region.imageFallback) }.getOrNull()
        finished = true
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = regionColor(place.region)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(kindSymbol(place.kind), color = Navy.copy(alpha = .62f), fontWeight = FontWeight.Black, fontSize = 20.sp)
            imageUrl?.let { url ->
                AsyncImage(model = url, contentDescription = place.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (!finished) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Navy.copy(alpha = .55f))
            }
        }
    }
}

'''
replace_once(discover, '@Composable\nprivate fun PlaceListItem(', thumbnail_fn + '@Composable\nprivate fun PlaceListItem(')
old_icon = '''            Box(
                Modifier.size(46.dp).background(regionColor(place.region), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(kindSymbol(place.kind), color = Navy, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))'''
replace_once(discover, old_icon, '''            PlaceThumbnail(place, Modifier.size(78.dp))
            Spacer(Modifier.width(12.dp))''')
replace_once(discover, 'Text("Antippen für Bild & Route", color = Muted, fontSize = 12.sp)', 'Text("Bild antippen für Details & Route", color = Muted, fontSize = 12.sp)')

data = SRC / "DiscoverData.kt"
canet_anchor = '        TravelPlace(TravelRegion.CANET, "Carrefour Claira / Salanca", "Großes Einkaufsgebiet für umfangreichen Einkauf, Kleidung und Dinge, die in Canet nicht verfügbar sind.", GeoPoint(42.7757, 2.9952), PlaceKind.SHOPPING, "1–3 Std.", "Centre commercial Salanca Claira", "Nur bei echtem Bedarf – sonst unnötiger Zeitfresser.", 78),'
replace_once(data, canet_anchor, canet_anchor + r'''
        TravelPlace(TravelRegion.CANET, "Anse de Paulilles", "Geschützte kleine Bucht zwischen Weinbergen und Felsküste mit Strand, kurzen Wegen und schöner Landschaft südlich von Collioure.", GeoPoint(42.5010, 3.1284), PlaceKind.HIGHLIGHT, "2–4 Std.", "Anse de Paulilles France", "Früh kommen; die begrenzten Parkplätze füllen sich an warmen Tagen schnell.", 93),
        TravelPlace(TravelRegion.CANET, "Villefranche-de-Conflent", "Vollständig ummauerter Bergort mit Festungscharakter, kleinen Gassen und deutlichem Kontrast zum Strandurlaub.", GeoPoint(42.5872, 2.3672), PlaceKind.HIGHLIGHT, "3–5 Std.", "Villefranche de Conflent France", "Mit Grottes des Canalettes verbinden, wenn ein ganzer Ausflugstag geplant ist.", 91),
        TravelPlace(TravelRegion.CANET, "Banyuls & Biodiversarium", "Küstenort mit Meerblick und naturkundlichem Angebot; eine ruhige Alternative zu den volleren Orten rund um Collioure.", GeoPoint(42.4824, 3.1289), PlaceKind.FAMILY, "3–5 Std.", "Banyuls sur Mer Biodiversarium", "Vorher prüfen, welche Bereiche des Biodiversariums am Besuchstag geöffnet sind.", 86),
        TravelPlace(TravelRegion.CANET, "Gorges de Galamus", "Spektakuläre enge Schlucht mit Aussichtspunkten und kurzen Wegen für einen landschaftlich starken Ausflug ins Hinterland.", GeoPoint(42.8350, 2.4815), PlaceKind.NATURE, "3–5 Std.", "Gorges de Galamus France", "Straßenbreite, Zufahrt und Wetter prüfen; nicht bei Gewitter oder Zeitdruck fahren.", 88),
        TravelPlace(TravelRegion.CANET, "Lac de Villeneuve-de-la-Raho", "Großer See mit Uferwegen, Picknickmöglichkeiten und Badebereichen als entspannte Süßwasser-Alternative zum Meer.", GeoPoint(42.6369, 2.9107), PlaceKind.QUICK, "2–4 Std.", "Lac Villeneuve de la Raho", "Ideal für einen ruhigen Nachmittag; ausgewiesene Badezonen und Regeln beachten.", 83),
        TravelPlace(TravelRegion.CANET, "Luna Park Argelès", "Abendlicher Freizeitpark mit Fahrgeschäften und typischer Urlaubsatmosphäre, besonders interessant für die Kinder.", GeoPoint(42.5685, 3.0427), PlaceKind.FAMILY, "2–4 Std.", "Luna Park Argeles sur Mer", "Saisonzeiten und Öffnung am selben Tag prüfen; eher als Abendprogramm einplanen.", 84),''')

barcelona_anchor = '        TravelPlace(TravelRegion.BARCELONA, "La Roca Village", "Outlet außerhalb Barcelonas; sinnvoll auf der Rückfahrt oder als bewusst geplanter Shoppingblock.", GeoPoint(41.6105, 2.3433), PlaceKind.SHOPPING, "2–4 Std.", "La Roca Village outlet", "Nicht mit einem vollen Innenstadtprogramm verbinden.", 76),'
replace_once(data, barcelona_anchor, barcelona_anchor + r'''
        TravelPlace(TravelRegion.BARCELONA, "Casa Batlló", "Sehr bildstarkes Gaudí-Haus am Passeig de Gràcia mit ungewöhnlichen Räumen und einer familienfreundlich inszenierten Besichtigung.", GeoPoint(41.3917, 2.1649), PlaceKind.HIGHLIGHT, "1,5–2,5 Std.", "Casa Batllo Barcelona facade", "Zeitfenster buchen und höchstens mit einem weiteren großen Gaudí-Ziel kombinieren.", 97),
        TravelPlace(TravelRegion.BARCELONA, "La Pedrera", "Gaudís geschwungener Wohnblock mit Innenhöfen und markanter Dachlandschaft; architektonisch stark und zentral gelegen.", GeoPoint(41.3954, 2.1619), PlaceKind.HIGHLIGHT, "1,5–2,5 Std.", "Casa Mila La Pedrera Barcelona", "Casa Batlló und La Pedrera nicht beide unter Zeitdruck in denselben Vormittag quetschen.", 92),
        TravelPlace(TravelRegion.BARCELONA, "Recinte Modernista Sant Pau", "Weitläufiges Jugendstil-Ensemble nahe der Sagrada Família mit deutlich weniger Gedränge und vielen Fotomotiven.", GeoPoint(41.4117, 2.1744), PlaceKind.QUICK, "1,5–2,5 Std.", "Hospital Sant Pau Barcelona modernist", "Sehr logisch mit der Sagrada Família über die Avinguda de Gaudí verbinden.", 90),
        TravelPlace(TravelRegion.BARCELONA, "Museu Marítim", "Schifffahrtsmuseum in historischen Werfthallen nahe der Rambla; gute Innenoption mit großen Exponaten für Kinder.", GeoPoint(41.3758, 2.1762), PlaceKind.RAIN, "2–3 Std.", "Maritime Museum Barcelona Drassanes", "Mit Hafen und Kolumbus-Säule kombinieren; bei Hitze besonders angenehm.", 87),
        TravelPlace(TravelRegion.BARCELONA, "Schokoladenmuseum", "Kompaktes Museum mit Schokoladenfiguren und Geschichte des Kakaos; ein unkomplizierter Programmpunkt für Kinder.", GeoPoint(41.3875, 2.1811), PlaceKind.FAMILY, "1–2 Std.", "Museu de la Xocolata Barcelona", "Gut mit Born, Ciutadella und Arc de Triomf kombinierbar.", 85),
        TravelPlace(TravelRegion.BARCELONA, "Sitges", "Eleganter Küstenort südlich von Barcelona mit Altstadt, Promenade und Strand als entspannte Alternative zur Großstadt.", GeoPoint(41.2370, 1.8058), PlaceKind.NATURE, "4–7 Std.", "Sitges beach old town", "Als eigenen Halbtages- oder Tagesausflug behandeln und nicht nach einem vollen Barcelonatag anhängen.", 84),''')

andorra_anchor = '        TravelPlace(TravelRegion.ANDORRA, "Epizen", "Großes Einkaufszentrum südlich der Hauptstadt mit Hypermarkt und Parkhaus, praktisch auf der Durchfahrt.", GeoPoint(42.4623, 1.4910), PlaceKind.SHOPPING, "1–3 Std.", "Epizen Andorra shopping centre", "Sinnvoll vor der Ausreise Richtung Spanien.", 86),'
replace_once(data, andorra_anchor, andorra_anchor + r'''
        TravelPlace(TravelRegion.ANDORRA, "Sant Joan de Caselles", "Sehr gut erhaltene romanische Kirche direkt an der Talstraße bei Canillo und ein kurzer kultureller Stopp ohne großen Umweg.", GeoPoint(42.5708, 1.6068), PlaceKind.QUICK, "30–60 Min.", "Sant Joan de Caselles Andorra", "Mit Meritxell, Roc del Quer oder Canillo kombinieren; Öffnung vorab prüfen.", 89),
        TravelPlace(TravelRegion.ANDORRA, "Casa d’Areny-Plandolit", "Historisches Wohnhaus in Ordino mit original eingerichteten Räumen und gut verständlichem Einblick in das frühere Andorra.", GeoPoint(42.5568, 1.5324), PlaceKind.RAIN, "1–2 Std.", "Casa Areny Plandolit Andorra", "Ideal zusammen mit Ordino Altstadt; Führungs- und Öffnungszeiten vorher ansehen.", 84),
        TravelPlace(TravelRegion.ANDORRA, "Mirador de la Comella", "Schnell erreichbarer Aussichtspunkt oberhalb von Andorra la Vella mit weitem Blick über Hauptstadt und Bergketten.", GeoPoint(42.4963, 1.5265), PlaceKind.QUICK, "30–60 Min.", "Mirador de la Comella Andorra", "Sehr guter spontaner Fotostopp bei klarer Sicht und wenig Zeit.", 87),
        TravelPlace(TravelRegion.ANDORRA, "Estanys de Juclà", "Hochalpine Seenlandschaft im Incles-Tal für einen deutlich längeren Naturtag mit großem Bergpanorama.", GeoPoint(42.6104, 1.7165), PlaceKind.NATURE, "5–7 Std.", "Estanys de Jucla Andorra", "Nur bei stabilem Wetter, geeigneten Schuhen und genügend Zeit wählen.", 86),
        TravelPlace(TravelRegion.ANDORRA, "Vall del Madriu", "UNESCO-geschütztes Tal mit alten Steinwegen, Wald und Berglandschaft; die Strecke lässt sich flexibel anpassen.", GeoPoint(42.5025, 1.5658), PlaceKind.NATURE, "3–6 Std.", "Madriu Perafita Claror valley Andorra", "Für die Familie nur den passenden unteren Abschnitt wählen und Rückwegzeit großzügig planen.", 92),
        TravelPlace(TravelRegion.ANDORRA, "Bici Lab Andorra", "Modernes Fahrradmuseum in Andorra la Vella mit Technik, Geschichte und interaktiven Elementen als kompakte Innenoption.", GeoPoint(42.5079, 1.5222), PlaceKind.RAIN, "1–2 Std.", "Bici Lab Andorra museum", "Gut mit Altstadt, Casa de la Vall und Einkauf auf der Meritxell-Achse kombinieren.", 82),''')

paris_anchor = '        TravelPlace(TravelRegion.PARIS, "Bercy Village", "Überschaubare Fußgängerzone mit Läden und Restaurants in ehemaligen Lagerhäusern.", GeoPoint(48.8331, 2.3868), PlaceKind.SHOPPING, "1–2 Std.", "Bercy Village Paris", "Mit Parc de Bercy kombinierbar.", 79)'
replace_once(data, paris_anchor, paris_anchor + r''',
        TravelPlace(TravelRegion.PARIS, "Atelier des Lumières", "Großformatige immersive Projektionen in einer ehemaligen Gießerei und eine eindrucksvolle Innenoption für die ganze Familie.", GeoPoint(48.8610, 2.3800), PlaceKind.RAIN, "1,5–2,5 Std.", "Atelier des Lumieres Paris", "Programm und Zeitfenster vorher prüfen; gut mit Bastille oder Père-Lachaise kombinierbar.", 93),
        TravelPlace(TravelRegion.PARIS, "Musée Grévin", "Historisches Wachsfigurenmuseum mit vielen bekannten Persönlichkeiten und unterhaltsamer Inszenierung für Kinder.", GeoPoint(48.8719, 2.3423), PlaceKind.FAMILY, "2–3 Std.", "Musee Grevin Paris", "Bei Regen sinnvoll; außerhalb der Hauptzeiten deutlich angenehmer.", 90),
        TravelPlace(TravelRegion.PARIS, "Opéra Garnier", "Prunkvolles Opernhaus mit großer Treppe und reich ausgestatteten Sälen; auch ohne Vorstellung sehr sehenswert.", GeoPoint(48.8719, 2.3316), PlaceKind.HIGHLIGHT, "1–2 Std.", "Opera Garnier Paris interior", "Direkt mit Galeries Lafayette und deren Dachterrasse verbinden.", 91),
        TravelPlace(TravelRegion.PARIS, "Schloss Versailles", "Monumentales Schloss mit Spiegelsaal und weitläufigen Gärten als klassischer Tagesausflug westlich von Paris.", GeoPoint(48.8049, 2.1204), PlaceKind.HIGHLIGHT, "5–8 Std.", "Palace of Versailles gardens", "Als eigenen Tag planen, Tickets buchen und die langen Wege in den Gärten berücksichtigen.", 95),
        TravelPlace(TravelRegion.PARIS, "Disneyland Paris", "Zwei große Themenparks östlich von Paris und die stärkste ganztägige Erlebnisoption für die Kinder.", GeoPoint(48.8674, 2.7836), PlaceKind.FAMILY, "8–12 Std.", "Disneyland Paris castle", "Nur als bewusst geplanten ganzen Tag wählen; Tickets und Anfahrt vorher festlegen.", 94),
        TravelPlace(TravelRegion.PARIS, "Ménagerie im Jardin des Plantes", "Kompakter historischer Zoo in zentraler Lage, der sich gut mit Naturkundemuseum und Garten verbinden lässt.", GeoPoint(48.8437, 2.3598), PlaceKind.FAMILY, "2–4 Std.", "Menagerie Jardin des Plantes Paris", "Für einen Familientag mit Grande Galerie de l’Évolution kombinieren.", 88)''')

test = ROOT / "app/src/test/java/de/balabucha/reisepilot/DestinationCatalogTest.kt"
replace_once(test, 'at least fourteen entries", places.size >= 14', 'at least twenty entries", places.size >= 20')

more = SRC / "MoreHubScreen.kt"
fuel_card = r'''
        item {
            AppCard {
                Text("Tankstellen & Preise", style = MaterialTheme.typography.titleLarge)
                Text("Fahrt starten und Standort erlauben. ReisePilot prüft dann automatisch günstige Dieselstationen entlang der geladenen Route.", color = Muted)
                Spacer(Modifier.height(8.dp))
                StatusLine("Frankreich", "offizielle Dieselpreise ohne API-Key", Light.GREEN)
                StatusLine("Spanien", "offizielle Dieselpreise ohne API-Key", Light.GREEN)
                StatusLine("Deutschland", "Live-Preise mit Tankerkönig-Key unter Technische Einstellungen", Light.YELLOW)
                snapshot.fuelSuggestion?.let { fuel ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(fuel.name, fontWeight = FontWeight.Bold)
                    Text(buildString {
                        fuel.pricePerLitre?.let { append("${"%.3f".format(it)} €/l · ") }
                        append("${"%.1f".format(fuel.distanceAheadKm)} km voraus · ca. ${"%.1f".format(fuel.detourKm)} km Umweg")
                    }, color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { activity.openPointRoute(fuel.point, fuel.name) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Route zur Tankstelle") }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { activity.serviceAction(TripTrackingService.ACTION_RELOAD_CONFIG) }, enabled = snapshot.active, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                    Text(if (snapshot.active) "Tankstellen jetzt neu suchen" else "Zuerst Fahrt starten")
                }
                Text("Die automatische Suche beginnt spätestens nach längerer Fahrt oder bei sinkendem Tankstand und aktualisiert sich etwa alle zwölf Minuten.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
'''
replace_once(more, '        item {\n            AppCard {\n                Text("Reise-Apps", style = MaterialTheme.typography.titleLarge)', fuel_card + '        item {\n            AppCard {\n                Text("Reise-Apps", style = MaterialTheme.typography.titleLarge)')

print("Applied ReisePilot 4.2 destination images, expanded catalog and fuel-search guidance")
