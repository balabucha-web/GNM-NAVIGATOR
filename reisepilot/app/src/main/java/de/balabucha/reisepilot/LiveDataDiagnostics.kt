package de.balabucha.reisepilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

internal data class LiveSourceStatus(
    val name: String,
    val detail: String,
    val light: Light
)

internal object LiveDataDiagnostics {
    const val SOURCE_COUNT = 9
    private const val SOURCE_TIMEOUT_MS = 20_000L

    private data class SourceProbe(
        val name: String,
        val block: suspend () -> LiveSourceStatus
    )

    suspend fun check(
        context: Context,
        onProgress: (List<LiveSourceStatus>) -> Unit = {}
    ): List<LiveSourceStatus> = supervisorScope {
        val app = context.applicationContext
        val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val token = normalizeMapboxToken(settings.getString("mapbox_token", "").orEmpty())
        val tankerKey = settings.getString("tankerkoenig_key", "").orEmpty().trim()
        val collioure = DestinationCatalog.places.first { it.title == "Collioure" }

        val probes = listOf(
            SourceProbe("Standort & Live-Umgebung") { currentLiveStatus(app) },
            SourceProbe("Zielbilder") {
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
            SourceProbe("Deutschland-Verkehr") {
                probe("Deutschland-Verkehr") {
                    val result = GermanTrafficClient.load(Stage.SATURDAY)
                    require(result.error == null) { result.error.orEmpty() }
                    "Autobahn-App antwortet · ${result.events.size} relevante Meldungen"
                }
            },
            SourceProbe("Frankreich-Verkehr") {
                probe("Frankreich-Verkehr") {
                    val result = BisonTrafficClient.load(Stage.SUNDAY)
                    require(result.error == null) { result.error.orEmpty() }
                    "Bison Futé antwortet · ${result.events.size} relevante Meldungen"
                }
            },
            SourceProbe("Frankreich-Diesel") {
                probe("Frankreich-Diesel") {
                    val count = FuelPriceClient.liveProbeFrance()
                    require(count > 0) { "keine Dieselpreise um Canet" }
                    "$count offizielle Preise um Canet geladen"
                }
            },
            SourceProbe("Spanien-Diesel") {
                probe("Spanien-Diesel") {
                    val count = FuelPriceClient.liveProbeSpain()
                    require(count > 0) { "keine Dieselpreise um Barcelona" }
                    "$count offizielle Preise um Barcelona geladen"
                }
            },
            SourceProbe("Deutschland-Diesel") {
                if (tankerKey.length < 30) {
                    LiveSourceStatus(
                        "Deutschland-Diesel",
                        "Tankerkönig-API-Key fehlt · Tankstellen ohne Preis bleiben über OpenStreetMap verfügbar",
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
                                        "API-Key vorhanden, aktuell keine Preise im Suchbereich · OSM-Fallback aktiv",
                                        Light.YELLOW
                                    )
                                }
                            },
                            onFailure = { error ->
                                LiveSourceStatus(
                                    "Deutschland-Diesel",
                                    "Preisquelle derzeit nicht erreichbar · OSM-Fallback aktiv${error.message?.let { ": ${it.take(70)}" }.orEmpty()}",
                                    Light.YELLOW
                                )
                            }
                        )
                }
            },
            SourceProbe("Basiskarte") {
                probe("Basiskarte") {
                    val connection = URL("https://tiles.openfreemap.org/styles/liberty").openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 6_000
                        connection.readTimeout = 8_000
                        connection.setRequestProperty("Accept", "application/json")
                        connection.setRequestProperty("User-Agent", "ReisePilot/5.6.1 Android family travel app")
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
            SourceProbe("Live-Route") {
                if (!mapboxTokenLooksValid(token)) {
                    LiveSourceStatus("Live-Route", "Mapbox-Token nicht eingerichtet · Etappenübersicht und Google Maps bleiben nutzbar", Light.YELLOW)
                } else {
                    probe("Live-Route") {
                        val route = MapboxClient.route(token, TripConfig.schwerin, TripConfig.hotel)
                        require(route.distanceM in 700_000..1_500_000) { "unplausible Hotel-Teilstrecke" }
                        "Hotel-Teilstrecke antwortet · ${route.distanceM / 1_000} km"
                    }
                }
            }
        )

        check(probes.size == SOURCE_COUNT) { "Diagnosequellen stimmen nicht mit SOURCE_COUNT überein" }
        val channel = Channel<Pair<Int, LiveSourceStatus>>(Channel.UNLIMITED)
        probes.forEachIndexed { index, source ->
            launch(Dispatchers.IO) {
                val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) { source.block() }
                    ?: LiveSourceStatus(
                        source.name,
                        "Zeitüberschreitung nach ${SOURCE_TIMEOUT_MS / 1_000} Sekunden · später erneut prüfen",
                        Light.YELLOW
                    )
                channel.send(index to result)
            }
        }

        val completed = arrayOfNulls<LiveSourceStatus>(probes.size)
        repeat(probes.size) {
            val (index, result) = channel.receive()
            completed[index] = result
            onProgress(completed.filterNotNull())
        }
        channel.close()
        completed.filterNotNull()
    }

    private suspend fun currentLiveStatus(context: Context): LiveSourceStatus {
        val permission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permission) {
            return LiveSourceStatus(
                "Standort & Live-Umgebung",
                "Standortfreigabe fehlt · Live-Tankstellen, Raststätten und Parkplätze können nicht geladen werden",
                Light.YELLOW
            )
        }

        repeat(12) {
            val state = RoadAheadStore54.state
            if (!state.loadingFuel && !state.loadingRoadside && state.dataUpdatedAt > 0L) return@repeat
            delay(750L)
        }

        val state = RoadAheadStore54.state
        val ageMs = if (state.dataUpdatedAt > 0L) System.currentTimeMillis() - state.dataUpdatedAt else Long.MAX_VALUE
        val resultCount = state.fuelOptions.size + state.roadsideStops.size
        val failed = listOf(state.fuelMessage, state.roadsideMessage, state.message)
            .any { it.contains("konnten nicht", true) || it.contains("nicht erreichbar", true) }

        return when {
            state.loadingFuel || state.loadingRoadside -> LiveSourceStatus(
                "Standort & Live-Umgebung",
                "Aktuelle Standortabfrage läuft · vorhandene Treffer: $resultCount",
                Light.YELLOW
            )
            resultCount > 0 && ageMs <= 15L * 60L * 1_000L -> LiveSourceStatus(
                "Standort & Live-Umgebung",
                "${state.fuelOptions.size} Tankstellen und ${state.roadsideStops.size} Rast-/Parkpunkte am aktuellen Standort geladen",
                if (failed) Light.YELLOW else Light.GREEN
            )
            resultCount > 0 -> LiveSourceStatus(
                "Standort & Live-Umgebung",
                "Letzter Standortstand ist älter · ${state.fuelOptions.size} Tankstellen und ${state.roadsideStops.size} Rast-/Parkpunkte im Cache",
                Light.YELLOW
            )
            failed -> LiveSourceStatus(
                "Standort & Live-Umgebung",
                listOf(state.fuelMessage, state.roadsideMessage).filter(String::isNotBlank).joinToString(" · ").take(180),
                Light.RED
            )
            else -> LiveSourceStatus(
                "Standort & Live-Umgebung",
                "Noch kein verwertbarer Standortstand · Startseite öffnen und Live-Daten erneut laden",
                Light.YELLOW
            )
        }
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
