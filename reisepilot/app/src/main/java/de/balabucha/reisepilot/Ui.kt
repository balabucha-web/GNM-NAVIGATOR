package de.balabucha.reisepilot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Navy = Color(0xFF13253D)
val Blue = Color(0xFF126782)
val Teal = Color(0xFF008C8C)
val Green = Color(0xFF177A50)
val Yellow = Color(0xFFB77800)
val Red = Color(0xFFB42318)
val Bg = Color(0xFFF1F5F7)
val Muted = Color(0xFF64748B)
val Line = Color(0xFFDCE5EA)
val SoftBlue = Color(0xFFE6F2F6)

@Composable
fun ReiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Teal,
            tertiary = Green,
            background = Bg,
            surface = Color.White,
            surfaceVariant = SoftBlue,
            onPrimary = Color.White,
            onSurface = Navy,
            outline = Line
        ),
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        ),
        content = content
    )
}

enum class AppTab(val title: String) {
    START("Start"),
    ROUTE("Karte"),
    DISCOVER("Ziele"),
    PACKING("Packliste"),
    MORE("Mehr")
}

private fun AppTab.icon() = when (this) {
    AppTab.START -> Icons.Filled.Home
    AppTab.ROUTE -> Icons.Filled.Map
    AppTab.DISCOVER -> Icons.Filled.Explore
    AppTab.PACKING -> Icons.Filled.Checklist
    AppTab.MORE -> Icons.Filled.MoreHoriz
}

@Composable
fun ReisePilotApp(activity: MainActivity, snapshot: TripSnapshot) {
    var tab by rememberSaveable { mutableStateOf(AppTab.START) }
    SideEffect { activity.updateKeepScreenOn(snapshot.active) }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Navy,
                            indicatorColor = Blue,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        ),
                        icon = { Icon(item.icon(), item.title) },
                        label = { Text(item.title, maxLines = 1, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        val pageModifier = Modifier.padding(padding)
        when (tab) {
            AppTab.START -> VisualLiveScreen52(activity, snapshot, pageModifier)
            AppTab.ROUTE -> MapScreen51(activity, snapshot, pageModifier)
            AppTab.DISCOVER -> VisualDiscoverScreen51(activity, snapshot, pageModifier)
            AppTab.PACKING -> PackingListScreen(activity, pageModifier) { tab = AppTab.MORE }
            AppTab.MORE -> MoreHubScreen51(activity, snapshot, pageModifier)
        }
    }
}

@Composable
fun Page(title: String, subtitle: String, modifier: Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier.fillMaxSize().testTag("page-list:$title"),
        contentPadding = PaddingValues(start = 15.dp, top = 18.dp, end = 15.dp, bottom = 86.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 13.sp)
                }
                Surface(color = SoftBlue, shape = RoundedCornerShape(13.dp)) {
                    Text(
                        "ReisePilot",
                        color = Blue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        content()
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, detail: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (detail.isNotBlank()) Text(detail, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Lamp(light: Light, size: Dp) {
    Box(
        Modifier.size(size)
            .background(statusColor(light), CircleShape)
            .border(2.dp, Color.White.copy(alpha = .7f), CircleShape)
    )
}

@Composable
fun Metric(label: String, value: String, light: Light, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lamp(light, 10.dp)
                Spacer(Modifier.width(7.dp))
                Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
fun StatusLine(label: String, value: String, light: Light) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Lamp(light, 12.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(value, color = Muted, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun WarningCard(title: String, text: String, light: Light) {
    val color = statusColor(light)
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .09f)),
        border = BorderStroke(1.dp, color.copy(alpha = .22f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Lamp(light, 15.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.height(3.dp))
                Text(text)
            }
        }
    }
}

fun statusColor(light: Light) = when (light) {
    Light.GREEN -> Green
    Light.YELLOW -> Yellow
    Light.RED -> Red
    Light.GREY -> Color(0xFF94A3B8)
}

fun lightText(light: Light) = when (light) {
    Light.GREEN -> "planmäßig"
    Light.YELLOW -> "knapp"
    Light.RED -> "Abweichung"
    Light.GREY -> "unbekannt"
}

fun formatTime(epoch: Long) = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))

fun minuteText(minutes: Int) = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
fun distanceText(m: Int) = if (m < 1000) "$m m" else "${"%.1f".format(m / 1000.0)} km"
