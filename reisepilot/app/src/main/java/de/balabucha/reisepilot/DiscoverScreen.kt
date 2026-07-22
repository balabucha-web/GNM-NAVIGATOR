package de.balabucha.reisepilot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
    val automaticRegion = remember(location?.lat, location?.lon) {
        DestinationCatalog.nearestRegion(location)
    }
    var selectedRegion by rememberSaveable { mutableStateOf(automaticRegion ?: TravelRegion.CANET) }
    var followLocation by rememberSaveable { mutableStateOf(true) }
    var kind by rememberSaveable { mutableStateOf<PlaceKind?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPlace by remember { mutableStateOf<TravelPlace?>(null) }

    LaunchedEffect(automaticRegion) {
        if (followLocation && automaticRegion != null) selectedRegion = automaticRegion
    }

    val places = remember(selectedRegion, kind, query, followLocation, location?.lat, location?.lon) {
        DiscoverLogic.filter(
            region = selectedRegion,
            kind = kind,
            query = query,
            location = location,
            followLocation = followLocation && selectedRegion == automaticRegion
        )
    }
    val spontaneous = remember(selectedRegion) { DiscoverLogic.spontaneous(selectedRegion) }

    Page(
        title = "Entdecken",
        subtitle = "Ausflugsziele · passende Bilder, Parkplätze und Route",
        modifier = modifier
    ) {
        item {
            RegionControl(
                selectedRegion = selectedRegion,
                automaticRegion = automaticRegion,
                locationAvailable = location != null,
                followLocation = followLocation,
                onAuto = {
                    followLocation = true
                    automaticRegion?.let { selectedRegion = it }
                    kind = null
                    query = ""
                }
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TravelRegion.entries, key = { it.name }) { region ->
                    FilterChip(
                        selected = selectedRegion == region,
                        onClick = {
                            selectedRegion = region
                            followLocation = false
                            kind = null
                            query = ""
                        },
                        label = { Text(region.label) }
                    )
                }
            }
        }

        item {
            RegionPlanCard(selectedRegion)
        }

        if (spontaneous.isNotEmpty()) {
            item {
                Text("Spontan passend", style = MaterialTheme.typography.titleMedium)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(spontaneous, key = { "quick:${it.region.name}:${it.title}" }) { place ->
                        SuggestionChip(place = place, onClick = { selectedPlace = place })
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Ziel suchen") },
                placeholder = { Text("z. B. Strand, Aquarium, Einkauf …") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (query.isNotBlank()) {
                        TextButton(onClick = { query = "" }) { Text("Löschen") }
                    }
                }
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = kind == null,
                        onClick = { kind = null },
                        label = { Text("Beste zuerst") }
                    )
                }
                items(PlaceKind.entries, key = { it.name }) { item ->
                    FilterChip(
                        selected = kind == item,
                        onClick = { kind = item },
                        label = { Text(item.label) }
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${places.size} passende Ziele", fontWeight = FontWeight.Bold)
                Text("Bild antippen für Details & Route", color = Muted, fontSize = 12.sp)
            }
        }

        if (places.isEmpty()) {
            item {
                WarningCard(
                    "Keine Treffer",
                    "Suchbegriff oder Filter ändern.",
                    Light.GREY
                )
            }
        }

        items(
            items = places,
            key = { "place:${it.region.name}:${it.title}" }
        ) { place ->
            PlaceListItem(
                place = place,
                distanceKm = DestinationCatalog.distanceKm(location, place),
                onClick = { selectedPlace = place }
            )
        }
    }

    selectedPlace?.let { place ->
        PlaceDetailSheet(
            activity = activity,
            place = place,
            distanceKm = DestinationCatalog.distanceKm(location, place),
            onDismiss = { selectedPlace = null }
        )
    }
}

@Composable
private fun RegionControl(
    selectedRegion: TravelRegion,
    automaticRegion: TravelRegion?,
    locationAvailable: Boolean,
    followLocation: Boolean,
    onAuto: () -> Unit
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lamp(if (locationAvailable) Light.GREEN else Light.GREY, 14.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(selectedRegion.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        !locationAvailable -> "Ohne Tracking ist Canet die Startregion. Region jederzeit manuell wählen."
                        followLocation && automaticRegion != null -> "Automatisch nach Standort · erkannt: ${automaticRegion.label}"
                        followLocation -> "Unterwegs · keine Urlaubsregion im Umkreis erkannt. Auswahl bleibt manuell nutzbar."
                        else -> "Manuell gewählt · automatische Standortauswahl pausiert"
                    },
                    color = Muted,
                    fontSize = 13.sp
                )
            }
            if (!followLocation && locationAvailable) {
                TextButton(onClick = onAuto) { Text("Auto") }
            }
        }
    }
}

@Composable
private fun RegionPlanCard(region: TravelRegion) {
    AppCard {
        Text("Sinnvoller Aufbau", color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        Text(region.shortPlan, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(region.logistics, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SuggestionChip(place: TravelPlace, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = regionColor(place.region).copy(alpha = .42f),
        border = androidx.compose.foundation.BorderStroke(1.dp, regionColor(place.region))
    ) {
        Column(Modifier.widthIn(min = 170.dp, max = 230.dp).padding(12.dp)) {
            Text(place.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(place.duration, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlaceThumbnail(place: TravelPlace, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var finished by remember(place.region, place.title) { mutableStateOf(false) }
    val imageUrl by produceState<String?>(initialValue = null, place.region, place.title, place.imageQuery) {
        value = runCatching { WikiImageResolver.resolve(context, place) }.getOrNull()
        finished = true
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = regionColor(place.region)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DestinationArtwork(place.region, Modifier.fillMaxSize())
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

@Composable
private fun PlaceListItem(
    place: TravelPlace,
    distanceKm: Double?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(destinationCardTag(place)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaceThumbnail(place, Modifier.size(78.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(place.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    place.description,
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    buildString {
                        append(place.duration)
                        distanceKm?.let { append(" · ").append("%.1f".format(it)).append(" km") }
                        append(" · ").append(place.kind.label)
                    },
                    color = Blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", color = Blue, fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceDetailSheet(
    activity: MainActivity,
    place: TravelPlace,
    distanceKm: Double?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var imageFinished by remember(place.title) { mutableStateOf(false) }
    val gallery by produceState<List<String>>(
        initialValue = emptyList(),
        place.region,
        place.title,
        place.imageQuery
    ) {
        val firstPhoto = runCatching {
            WikiImageResolver.resolveGallery(context, place, 1)
        }.getOrDefault(emptyList())
        if (firstPhoto.isNotEmpty()) {
            value = firstPhoto
            imageFinished = true
        }

        val fullGallery = runCatching {
            WikiImageResolver.resolveGallery(context, place, 8)
        }.getOrDefault(firstPhoto)
        if (fullGallery.isNotEmpty()) value = fullGallery
        imageFinished = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Bg
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().height(250.dp).background(
                    Brush.linearGradient(listOf(regionColor(place.region), Color(0xFFE7EEF2)))
                )
            ) {
                DestinationArtwork(place.region, Modifier.fillMaxSize())
                if (gallery.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(gallery, key = { index, url -> "$index:$url" }) { index, url ->
                            Box(Modifier.width(350.dp).fillMaxHeight()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "${place.title} · Foto ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Navy.copy(alpha = .82f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                ) {
                                    Text(
                                        "${index + 1} / ${gallery.size}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(kindSymbol(place.kind), color = Navy.copy(alpha = .55f), fontSize = 48.sp, fontWeight = FontWeight.Black)
                        Text(
                            when {
                                !imageFinished -> "Passende Fotos werden geladen …"
                                else -> "Kein eindeutig passendes Foto gefunden"
                            },
                            color = Navy.copy(alpha = .65f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    color = Navy.copy(alpha = .88f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                ) {
                    Text(
                        place.kind.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Column(Modifier.padding(18.dp)) {
                Text(place.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(7.dp))
                Text(place.description, color = Navy, lineHeight = 21.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(place.duration) })
                    distanceKm?.let { distance ->
                        AssistChip(onClick = {}, label = { Text("${"%.1f".format(distance)} km") })
                    }
                }
                if (place.tip.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    WarningCard("Praktischer Tipp", place.tip, Light.GREEN)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        activity.openPointRoute(place.point, place.title)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Route in Google Maps") }
                Spacer(Modifier.height(14.dp))
                ParkingSection(activity = activity, place = place)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Zurück zur Liste") }
            }
        }
    }
}

@Composable
private fun ParkingSection(activity: MainActivity, place: TravelPlace) {
    val context = LocalContext.current
    var refreshKey by remember(place.region, place.title) { mutableIntStateOf(0) }
    var loading by remember(place.region, place.title) { mutableStateOf(true) }
    var selectedId by remember(place.region, place.title) {
        mutableStateOf(ParkingSelectionStore.selectedFor(context, place)?.spot?.id)
    }
    val curated = remember(place.region, place.title) { ParkingCatalog.recommendations(place) }
    val result by produceState(
        initialValue = ParkingSearchResult(curated, 0L),
        place.region,
        place.title,
        refreshKey
    ) {
        loading = true
        value = runCatching {
            ParkingResolver.nearby(context, place, forceRefresh = refreshKey > 0)
        }.getOrElse {
            ParkingSearchResult(curated, System.currentTimeMillis(), message = "Parkdaten konnten nicht geladen werden")
        }
        loading = false
    }

    Column(Modifier.fillMaxWidth().testTag("parking-section")) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Parken", style = MaterialTheme.typography.titleLarge)
                Text("Nahe Parkplätze statt nur Route zum eigentlichen Ziel", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = { refreshKey++ }, enabled = !loading) {
                Text(if (loading) "Lädt …" else "Aktualisieren")
            }
        }
        Spacer(Modifier.height(8.dp))
        WarningCard("Parkstrategie", ParkingCatalog.advice(place), Light.YELLOW)
        Spacer(Modifier.height(10.dp))

        if (loading && result.spots.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Parkplätze in der Nähe werden geladen …", color = Muted)
            }
        }

        result.spots.forEach { spot ->
            ParkingSpotCard(
                spot = spot,
                selected = selectedId == spot.id,
                onNavigate = { activity.openPointRoute(spot.point, spot.name) },
                onToggleSaved = {
                    if (selectedId == spot.id) {
                        ParkingSelectionStore.remove(context, place)
                        selectedId = null
                    } else {
                        ParkingSelectionStore.save(context, place, spot)
                        selectedId = spot.id
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (result.spots.isEmpty() && !loading) {
            WarningCard(
                "Keine sichere Empfehlung gefunden",
                "Die Karten-Suche bleibt verfügbar. Vor Ort Beschilderung und Zufahrt prüfen.",
                Light.GREY
            )
            Spacer(Modifier.height(8.dp))
        }
        if (result.message.isNotBlank()) {
            Text(result.message, color = Yellow, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        OutlinedButton(
            onClick = { activity.openMapSearch("Parkplatz nahe ${place.title}") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp)
        ) { Text("Weitere Parkplätze in Google Maps") }
        Text(
            "Parkdaten: OpenStreetMap · Preise, freie Plätze, Höhe und Öffnung am Besuchstag prüfen.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
private fun ParkingSpotCard(
    spot: ParkingSpot,
    selected: Boolean,
    onNavigate: () -> Unit,
    onToggleSaved: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("parking-spot:${spot.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (spot.recommended) Green.copy(alpha = .07f) else Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (spot.recommended) Green.copy(alpha = .35f) else Line
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    color = if (spot.recommended) Green else Blue,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(
                        "P",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(spot.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${spot.kind.label} · ${distanceText(spot.distanceToDestinationM)} zum Ziel · ca. ${spot.walkingMinutes} Min. zu Fuß",
                        color = Blue,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                if (spot.recommended) {
                    Text("EMPFOHLEN", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                buildList {
                    add(spot.fee.label)
                    spot.capacity?.let { add("$it Plätze") }
                    if (spot.supervised == true) add("überwacht gemeldet")
                    if (spot.openingHours.isNotBlank()) add(spot.openingHours)
                    if (spot.maxHeight.isNotBlank()) add("max. ${spot.maxHeight.replace('.', ',')} m")
                }.joinToString(" · "),
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            if (spot.note.isNotBlank()) {
                Text(spot.note, color = Navy, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(onClick = onNavigate, modifier = Modifier.weight(1f)) { Text("Navigation") }
                TextButton(
                    onClick = onToggleSaved,
                    modifier = Modifier.weight(1f).testTag("parking-save:${spot.id}")
                ) { Text(if (selected) "Von Karte entfernen" else "Auf Karte merken") }
            }
        }
    }
}

private fun kindSymbol(kind: PlaceKind): String = when (kind) {
    PlaceKind.HIGHLIGHT -> "★"
    PlaceKind.FAMILY -> "2+"
    PlaceKind.NATURE -> "⌁"
    PlaceKind.QUICK -> "↗"
    PlaceKind.RAIN -> "☂"
    PlaceKind.SHOPPING -> "€"
}

private fun regionColor(region: TravelRegion): Color = when (region) {
    TravelRegion.CANET -> Color(0xFF9ED8E7)
    TravelRegion.BARCELONA -> Color(0xFFF1C98B)
    TravelRegion.ANDORRA -> Color(0xFFAFCDB5)
    TravelRegion.PARIS -> Color(0xFFC8C1DF)
}

internal fun destinationCardTag(place: TravelPlace): String =
    "destination-card:${place.region.name}:${place.title}"
