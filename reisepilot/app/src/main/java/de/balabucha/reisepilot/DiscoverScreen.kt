package de.balabucha.reisepilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun DiscoverScreen(activity: MainActivity, snapshot: TripSnapshot, modifier: Modifier) {
    val location = snapshot.lat?.let { lat -> snapshot.lon?.let { lon -> GeoPoint(lat, lon) } }
    val automaticRegion = remember(location?.lat, location?.lon) {
        DestinationCatalog.nearestRegion(location)
    }
    var selectedRegion by rememberSaveable { mutableStateOf(automaticRegion) }
    var followLocation by rememberSaveable { mutableStateOf(true) }
    var kind by rememberSaveable { mutableStateOf<PlaceKind?>(null) }

    LaunchedEffect(automaticRegion) {
        if (followLocation) selectedRegion = automaticRegion
    }

    val places = remember(selectedRegion, kind, followLocation, location?.lat, location?.lon) {
        val filtered = DestinationCatalog.forRegion(selectedRegion)
            .filter { kind == null || it.kind == kind }
        if (followLocation && location != null && selectedRegion == automaticRegion) {
            filtered.sortedWith(
                compareBy<TravelPlace> {
                    DestinationCatalog.distanceKm(location, it) ?: Double.MAX_VALUE
                }.thenByDescending { it.priority }
            )
        } else {
            filtered.sortedByDescending { it.priority }
        }
    }

    Page(
        title = "Ziele",
        subtitle = "Sinnvolle Ideen mit Bild, Kurzinfo und direkter Maps-Route",
        modifier = modifier
    ) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Lamp(if (location != null) Light.GREEN else Light.GREY, 14.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (location != null) "Standort: ${automaticRegion.label}" else "Noch kein Standort",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (followLocation) "Region folgt automatisch dem Standort."
                            else "Region manuell gewählt · beste Ziele zuerst.",
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                    TextButton(
                        onClick = {
                            followLocation = true
                            selectedRegion = automaticRegion
                        }
                    ) { Text("Auto") }
                }
            }
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
                        },
                        label = { Text(region.label) }
                    )
                }
            }
        }

        item { RegionSummary(selectedRegion, DestinationCatalog.forRegion(selectedRegion).size) }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = kind == null,
                        onClick = { kind = null },
                        label = { Text("Alles") }
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

        if (places.isEmpty()) {
            item {
                WarningCard(
                    "Keine Treffer",
                    "Für diesen Filter ist aktuell nichts Sinnvolles hinterlegt.",
                    Light.GREY
                )
            }
        }

        items(
            items = places,
            key = { "${it.region.name}:${it.title}" }
        ) { place ->
            PlaceCard(activity, place, location)
        }
    }
}

@Composable
private fun RegionSummary(region: TravelRegion, count: Int) {
    AppCard {
        Text(region.label, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(region.shortPlan, color = Navy, lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(region.logistics, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("$count geprüfte Ideen", color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun PlaceCard(activity: MainActivity, place: TravelPlace, location: GeoPoint?) {
    val context = LocalContext.current
    var imageFinished by remember(place.title) { mutableStateOf(false) }
    val imageUrl by produceState<String?>(
        initialValue = null,
        place.imageQuery,
        place.region.imageFallback
    ) {
        value = runCatching {
            WikiImageResolver.resolve(context, place.imageQuery, place.region.imageFallback)
        }.getOrNull()
        imageFinished = true
    }
    val distance = DestinationCatalog.distanceKm(location, place)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(regionColor(place.region), Color(0xFFE7EEF2))
                        )
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        place.region.label,
                        color = Navy.copy(alpha = 0.58f),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Text(
                        if (imageFinished) "Bild momentan nicht verfügbar" else "Bild wird geladen …",
                        color = Navy.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }

                imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = place.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Surface(
                    color = Navy.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
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

            Column(Modifier.padding(16.dp)) {
                Text(
                    place.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(place.description, color = Muted, lineHeight = 20.sp)
                if (place.tip.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tipp: ${place.tip}",
                        color = Navy,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(place.duration, color = Blue, fontWeight = FontWeight.Bold)
                    if (distance != null) {
                        Text("  ·  ${"%.1f".format(distance)} km", color = Muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { activity.openPointRoute(place.point, place.title) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Route in Google Maps") }
            }
        }
    }
}

private fun regionColor(region: TravelRegion): Color = when (region) {
    TravelRegion.CANET -> Color(0xFF9ED8E7)
    TravelRegion.BARCELONA -> Color(0xFFF1C98B)
    TravelRegion.ANDORRA -> Color(0xFFAFCDB5)
    TravelRegion.PARIS -> Color(0xFFC8C1DF)
}
