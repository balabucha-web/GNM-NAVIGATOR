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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers

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

    val places = remember(selectedRegion, kind, location?.lat, location?.lon) {
        DestinationCatalog.forRegion(selectedRegion)
            .filter { kind == null || it.kind == kind }
            .sortedWith(
                compareBy<TravelPlace> {
                    DestinationCatalog.distanceKm(location, it) ?: Double.MAX_VALUE
                }.thenByDescending { it.priority }
            )
    }

    Page(
        title = "Entdecken",
        subtitle = "Highlights, Kinderideen, Natur und Einkaufen in deiner Nähe",
        modifier = modifier
    ) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Lamp(if (location != null) Light.GREEN else Light.GREY, 14.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (location != null) "Standort erkannt: ${automaticRegion.label}" else "Noch kein Standort",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (followLocation) "Die Auswahl folgt automatisch deinem Aufenthaltsort."
                            else "Region wurde manuell gewählt.",
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = {
                        followLocation = true
                        selectedRegion = automaticRegion
                    }) { Text("Auto") }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TravelRegion.entries) { region ->
                    FilterChip(
                        selected = selectedRegion == region,
                        onClick = {
                            selectedRegion = region
                            followLocation = false
                        },
                        label = { Text(region.label) }
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = kind == null,
                        onClick = { kind = null },
                        label = { Text("Alles") }
                    )
                }
                items(PlaceKind.entries) { item ->
                    FilterChip(
                        selected = kind == item,
                        onClick = { kind = item },
                        label = { Text(item.label) }
                    )
                }
            }
        }

        if (places.isEmpty()) {
            item { WarningCard("Keine Treffer", "Für diesen Filter sind noch keine Punkte hinterlegt.", Light.GREY) }
        }

        items(places.size) { index ->
            PlaceCard(activity, places[index], location)
        }
    }
}

@Composable
private fun PlaceCard(activity: MainActivity, place: TravelPlace, location: GeoPoint?) {
    val context = LocalContext.current
    val imageUrl by produceState<String?>(initialValue = null, place.wikiTitle) {
        value = WikiImageResolver.resolve(context, place.wikiTitle)
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
                    .height(168.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFB8D8E5), Color(0xFFE7EEF2))
                        )
                    )
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = place.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    color = Navy.copy(alpha = 0.86f),
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
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(place.title, style = MaterialTheme.typography.titleLarge)
                        Text(place.description, color = Muted, lineHeight = 20.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(place.duration, color = Blue, fontWeight = FontWeight.Bold)
                    if (distance != null) {
                        Text("  ·  ${"%.1f".format(distance)} km entfernt", color = Muted)
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
