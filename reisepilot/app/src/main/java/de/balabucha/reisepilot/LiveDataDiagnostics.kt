package de.balabucha.reisepilot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import java.net.HttpURLConnection
import java.net.URL

internal data class LiveSourceStatus(
    val name: String,
    val detail: String,
    val light: Light
)

internal object LiveDataDiagnostics {
    suspend fun check(context: Context): List<LiveSourceStatus> = supervisorScope {
        val app = context.applicationContext
        val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val token = normalizeMapboxToken(settings.getString("mapbox_token", "").orEmpty())
        val tankerKey = settings.getString("tankerkoenig_key", "").orEmpty().trim()
        val collioure = DestinationCatalog.places.first { it.title == "Collioure" }

        listOf(
            async(Dispatchers.IO) {
                probe("OSM-Parkplätze") {
                    val count = ParkingResolver.liveProbe(collioure)
                    require(count > 0) { "keine Parkobjekte geliefert" }
                    "$count Parkobjekte bei Collioure · Server-Fallback aktiv"
                }
            },
            async(Dispatchers.IO) {
                runCatching { WikiImageResolver.liveCandidateCount(collioure) }
                    .fold(
                        onSuccess = { count ->
                            if (count > 0) {
                                LiveSourceStatus(
                                    "Zielbilder",
                                    "$count passende Wikimedia-Kandidaten · Offline-Grundbild zusätzlich",
                                    Light.GREEN
                                )
                            } else {
                                LiveSourceStatus(
                                    "Zielbilder",
                                    "Offline-Vorschau vorhanden · Online-Galerie derzeit ohne Treffer",
                                    Light.YELLOW
                                )
                            }
                        },
                        onFailure = {
                            LiveSourceStatus(
                                "Zielbilder",
                                "Offline-Vorschau vorhanden · Online-Galerie derzeit nicht erreichbar",
                                Light.YELLOW
                            )
                        }
                    )
            },
            async(Dispatchers.IO) {
                probe("Deutschland-Verkehr") {
                    val result = GermanTrafficClient.load(Stage.SATURDAY)
                    require(result.error == null) { result.error.orEmpty() }
                    "Autobahn-App antwortet · ${result.events.size} relevante Meldungen"
                }
            },
            async(Dispatchers.IO) {
                probe("Frankreich-Verkehr") {
                    val result = BisonTrafficClient.load(Stage.SUNDAY)
                    require(result.error == null) { result.error.orEmpty() }
                    "Bison Futé antwortet · ${result.events.size} relevante Meldungen"
                }
            },
            async(Dispatchers.IO) {
                probe("Frankreich-Diesel") {
                    val count = FuelPriceClient.liveProbeFrance()
                    require(count > 0) { "keine Dieselpreise um Canet" }
                    "$count offizielle Preise um Canet geladen"
                }
            },
            async(Dispatchers.IO) {
                probe("Spanien-Diesel") {
                    val count = FuelPriceClient.liveProbeSpain()
                    require(count > 0) { "keine Dieselpreise um Barcelona" }
                    "$count offizielle Preise um Barcelona geladen"
                }
            },
            async(Dispatchers.IO) {
                if (tankerKey.length < 30) {
                    LiveSourceStatus(
                        "Deutschland-Diesel",
                        "Tankerkönig-API-Key fehlt · geplante Tankstopps bleiben verfügbar",
                        Light.YELLOW
                    )
                } else {
                    runCatching { FuelPriceClient.liveProbeGermany(tankerKey) }
                        .fold(
                            onSuccess = { count ->
                                if (count > 0) {
                                    LiveSourceStatus(
                                        "Deutschland-Diesel",
                                        "$count Tankerkönig-Preise um Schwerin geladen",
                                        Light.GREEN
                                    )
                                } else {
                                    LiveSourceStatus(
                                        "Deutschland-Diesel",
                                        "API-Key vorhanden, aktuell keine Preise im Suchbereich · Fallback-Stopps aktiv",
                                        Light.YELLOW
                                    )
                                }
                            },
                            onFailure = { error ->
                                LiveSourceStatus(
                                    "Deutschland-Diesel",
                                    "Preisquelle derzeit nicht erreichbar · Fallback-Stopps aktiv${error.message?.let { ": ${it.take(70)}" }.orEmpty()}",
                                    Light.YELLOW
                                )
                            }
                        )
                }
            },
            async(Dispatchers.IO) {
                probe("Basiskarte") {
                    val connection = URL("https://tiles.openfreemap.org/styles/liberty").openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 8_000
                        connection.readTimeout = 12_000
                        connection.setRequestProperty("Accept", "application/json")
                        connection.setRequestProperty("User-Agent", "ReisePilot/5.1 Android family travel app")
                        val code = connection.responseCode
                        require(code in 200..299) { "OpenFreeMap HTTP $code" }
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        require(body.contains("\"version\":8") || body.contains("\"version\": 8")) {
                            "Kartenstil ungültig"
                        }
                        "OpenFreeMap-Kartenstil erreichbar"
                    } finally {
                        connection.disconnect()
                    }
                }
            },
            async(Dispatchers.IO) {
                if (!mapboxTokenLooksValid(token)) {
                    LiveSourceStatus("Live-Route", "Mapbox-Token nicht eingerichtet", Light.YELLOW)
                } else {
                    probe("Live-Route") {
                        val route = MapboxClient.route(token, TripConfig.hotel, TripConfig.canet)
                        require(route.distanceM > 100_000) { "unplausible Route" }
                        "Mapbox-Verkehrsroute antwortet · ${route.distanceM / 1_000} km"
                    }
                }
            }
        ).awaitAll()
    }

    private inline fun probe(name: String, block: () -> String): LiveSourceStatus =
        runCatching { LiveSourceStatus(name, block(), Light.GREEN) }
            .getOrElse { error ->
                LiveSourceStatus(
                    name,
                    error.message?.take(135) ?: "nicht erreichbar",
                    Light.RED
                )
            }
}
