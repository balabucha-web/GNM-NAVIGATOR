package de.balabucha.reisepilot

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

internal data class OfflinePhotoEntry(
    val region: TravelRegion,
    val index: Int,
    val isPhoto: Boolean,
    val source: String,
    val author: String,
    val licence: String,
    val licenceUrl: String
)

internal object DestinationOfflinePhotoCatalog {
    private const val CELL_WIDTH = 480
    private const val CELL_HEIGHT = 270
    private const val COLUMNS = 5
    private val sprites = LruCache<String, ImageBitmap>(2)
    private val spriteLock = Any()

    @Volatile
    private var entries: Map<String, OfflinePhotoEntry>? = null

    fun entry(context: Context, place: TravelPlace): OfflinePhotoEntry? =
        allEntries(context.applicationContext)[place.title]

    fun hasEveryDestination(context: Context): Boolean =
        DestinationCatalog.places.all { entry(context, it) != null }

    fun loadSprite(context: Context, region: TravelRegion): ImageBitmap? {
        synchronized(spriteLock) {
            sprites.get(region.name)?.let { return it }
            return runCatching {
                context.assets.open("destination_photos/${region.name.lowercase()}.webp").use { stream ->
                    requireNotNull(BitmapFactory.decodeStream(stream)).asImageBitmap()
                }
            }.getOrNull()?.also { sprites.put(region.name, it) }
        }
    }

    fun draw(scope: DrawScope, sprite: ImageBitmap, entry: OfflinePhotoEntry) = with(scope) {
        val destinationAspect = size.width / size.height.coerceAtLeast(1f)
        val sourceAspect = CELL_WIDTH.toFloat() / CELL_HEIGHT
        var sourceWidth = CELL_WIDTH
        var sourceHeight = CELL_HEIGHT
        if (destinationAspect < sourceAspect) {
            sourceWidth = (CELL_HEIGHT * destinationAspect).roundToInt().coerceIn(1, CELL_WIDTH)
        } else if (destinationAspect > sourceAspect) {
            sourceHeight = (CELL_WIDTH / destinationAspect).roundToInt().coerceIn(1, CELL_HEIGHT)
        }
        val cellX = (entry.index % COLUMNS) * CELL_WIDTH
        val cellY = (entry.index / COLUMNS) * CELL_HEIGHT
        val sourceX = cellX + (CELL_WIDTH - sourceWidth) / 2
        val sourceY = cellY + (CELL_HEIGHT - sourceHeight) / 2
        drawImage(
            image = sprite,
            srcOffset = IntOffset(sourceX, sourceY),
            srcSize = IntSize(sourceWidth, sourceHeight),
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium
        )
    }

    private fun allEntries(context: Context): Map<String, OfflinePhotoEntry> {
        entries?.let { return it }
        val loaded = runCatching {
            val raw = context.assets.open("destination_photos/index.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val places = JSONObject(raw).getJSONObject("places")
            buildMap {
                places.keys().forEach { title ->
                    val item = places.getJSONObject(title)
                    val region = runCatching { TravelRegion.valueOf(item.getString("region")) }
                        .getOrNull() ?: return@forEach
                    put(
                        title,
                        OfflinePhotoEntry(
                            region = region,
                            index = item.getInt("index"),
                            isPhoto = item.optBoolean("photo"),
                            source = item.optString("source"),
                            author = item.optString("author"),
                            licence = item.optString("license"),
                            licenceUrl = item.optString("licenseUrl")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
        entries = loaded
        return loaded
    }
}

/**
 * Always paints an instant offline preview. For the remaining illustration-only
 * entries, an exact POI photo is resolved once and cached locally. Wrong generic
 * city images are still rejected by WikiImageResolver.
 */
@Composable
internal fun DestinationOfflinePhoto(place: TravelPlace, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entry = remember(place.region, place.title) {
        DestinationOfflinePhotoCatalog.entry(context, place)
    }
    val sprite by produceState<ImageBitmap?>(initialValue = null, place.region) {
        value = withContext(Dispatchers.IO) {
            DestinationOfflinePhotoCatalog.loadSprite(context.applicationContext, place.region)
        }
    }
    val exactPhoto by produceState<String?>(initialValue = null, place.title, entry?.isPhoto) {
        value = if (entry?.isPhoto == false) {
            runCatching { WikiImageResolver.resolve(context.applicationContext, place) }.getOrNull()
        } else null
    }

    Box(modifier) {
        DestinationArtwork(place.region, Modifier.fillMaxSize())
        if (entry != null && sprite != null) {
            Canvas(Modifier.fillMaxSize()) {
                DestinationOfflinePhotoCatalog.draw(this, sprite!!, entry)
            }
        }
        exactPhoto?.let { photo ->
            AsyncImage(
                model = photo,
                contentDescription = place.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
