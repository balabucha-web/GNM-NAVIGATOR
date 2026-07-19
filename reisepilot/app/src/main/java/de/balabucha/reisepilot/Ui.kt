package de.balabucha.reisepilot

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.*
import java.time.format.DateTimeFormatter

val Navy = Color(0xFF18263F)
val Blue = Color(0xFF176B87)
val Green = Color(0xFF18794E)
val Yellow = Color(0xFFB77900)
val Red = Color(0xFFB42318)
val Bg = Color(0xFFF5F7FA)
val Muted = Color(0xFF667085)
val Line = Color(0xFFE4E7EC)

@Composable
fun ReiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, secondary = Green, background = Bg, surface = Color.White, onPrimary = Color.White, onSurface = Navy, outline = Line),
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        ),
        content = content
    )
}

enum class AppTab(val title: String, val short: String) { LIVE("Live","L"), MAP("Karte","K"), PLAN("Plan","P"), BOOKING("Buchung","B"), MORE("Mehr","M") }

@Composable
fun ReisePilotApp(activity: MainActivity, snapshot: TripSnapshot) {
    var tab by rememberSaveable { mutableStateOf(AppTab.LIVE) }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Box(Modifier.size(25.dp).background(if (tab == item) Blue else Color(0xFFE9EEF3), CircleShape), contentAlignment = Alignment.Center) {
                                Text(item.short, color = if (tab == item) Color.White else Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            AppTab.LIVE -> LiveScreen(activity, snapshot, Modifier.padding(padding))
            AppTab.MAP -> MapScreen(activity, snapshot, Modifier.padding(padding))
            AppTab.PLAN -> PlanScreen(activity, snapshot.stage, Modifier.padding(padding))
            AppTab.BOOKING -> BookingScreen(activity, Modifier.padding(padding))
            AppTab.MORE -> MoreScreen(activity, snapshot, Modifier.padding(padding))
        }
    }
}

@Composable
fun Page(title: String, subtitle: String, modifier: Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineMedium); Text(subtitle, color = Muted, fontSize = 13.sp) }
        content(); item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
fun Lamp(light: Light, size: Dp) {
    Box(Modifier.size(size).background(statusColor(light), CircleShape).border(3.dp, Color.White.copy(alpha = .35f), CircleShape))
}

@Composable
fun Metric(label: String, value: String, light: Light, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Lamp(light, 12.dp); Spacer(Modifier.width(7.dp)); Text(label, color = Muted, fontSize = 12.sp) }
            Spacer(Modifier.height(7.dp)); Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun StatusLine(label: String, value: String, light: Light) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Lamp(light, 13.dp); Spacer(Modifier.width(9.dp)); Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(value, color = Muted)
    }
}

@Composable
fun WarningCard(title: String, text: String, light: Light) {
    val color = statusColor(light)
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .10f)), border = BorderStroke(1.dp, color.copy(alpha = .25f)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) { Lamp(light, 16.dp); Spacer(Modifier.width(10.dp)); Column { Text(title, fontWeight = FontWeight.Bold, color = color); Text(text) } }
    }
}

fun statusColor(light: Light) = when (light) { Light.GREEN -> Green; Light.YELLOW -> Yellow; Light.RED -> Red; Light.GREY -> Color(0xFF98A2B3) }
fun lightText(light: Light) = when (light) { Light.GREEN -> "planmäßig"; Light.YELLOW -> "knapp"; Light.RED -> "Abweichung"; Light.GREY -> "unbekannt" }
fun formatTime(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
fun minuteText(minutes: Int) = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
fun distanceText(m: Int) = if (m < 1000) "$m m" else "${"%.1f".format(m / 1000.0)} km"
