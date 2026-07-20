package de.balabucha.reisepilot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun DestinationArtwork(region: TravelRegion, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sky = when (region) {
            TravelRegion.CANET -> Color(0xFFBDEBFA)
            TravelRegion.BARCELONA -> Color(0xFFFFD8A0)
            TravelRegion.ANDORRA -> Color(0xFFD6E8D5)
            TravelRegion.PARIS -> Color(0xFFD8D5EC)
        }
        drawRect(sky)
        drawCircle(Color(0xFFFFD166), w * .10f, Offset(w * .80f, h * .20f))
        when (region) {
            TravelRegion.CANET -> {
                drawRect(Color(0xFF3EA7C4), Offset(0f, h * .52f), Size(w, h * .30f))
                drawRect(Color(0xFFE8C982), Offset(0f, h * .82f), Size(w, h * .18f))
                repeat(3) { i ->
                    val y = h * (.58f + i * .08f)
                    drawLine(Color.White.copy(alpha = .85f), Offset(w * .05f, y), Offset(w * .95f, y), h * .018f)
                }
                val sail = Path().apply {
                    moveTo(w * .40f, h * .30f); lineTo(w * .40f, h * .62f); lineTo(w * .62f, h * .58f); close()
                }
                drawPath(sail, Color.White)
                drawLine(Color(0xFF364152), Offset(w * .40f, h * .27f), Offset(w * .40f, h * .66f), w * .018f)
            }
            TravelRegion.BARCELONA -> {
                drawRect(Color(0xFF587A9B), Offset(0f, h * .72f), Size(w, h * .28f))
                drawRect(Color(0xFF7A4E3A), Offset(w * .08f, h * .48f), Size(w * .22f, h * .32f))
                drawRect(Color(0xFFB45C3D), Offset(w * .70f, h * .42f), Size(w * .20f, h * .38f))
                val church = Path().apply {
                    moveTo(w * .36f, h * .78f); lineTo(w * .40f, h * .25f); lineTo(w * .44f, h * .78f)
                    moveTo(w * .48f, h * .78f); lineTo(w * .52f, h * .16f); lineTo(w * .56f, h * .78f)
                    moveTo(w * .60f, h * .78f); lineTo(w * .64f, h * .30f); lineTo(w * .68f, h * .78f)
                }
                drawPath(church, Color(0xFF5B3B2E), style = Stroke(w * .035f))
            }
            TravelRegion.ANDORRA -> {
                val mountains = Path().apply {
                    moveTo(0f, h * .72f); lineTo(w * .28f, h * .24f); lineTo(w * .48f, h * .70f)
                    lineTo(w * .72f, h * .18f); lineTo(w, h * .72f); close()
                }
                drawPath(mountains, Color(0xFF6F8D77))
                drawRect(Color(0xFF4D9AA5), Offset(0f, h * .72f), Size(w, h * .28f))
                drawLine(Color.White.copy(alpha = .75f), Offset(w * .12f, h * .82f), Offset(w * .88f, h * .82f), h * .018f)
            }
            TravelRegion.PARIS -> {
                drawRect(Color(0xFF8FB4C9), Offset(0f, h * .78f), Size(w, h * .22f))
                val tower = Path().apply {
                    moveTo(w * .50f, h * .16f); lineTo(w * .38f, h * .82f); lineTo(w * .45f, h * .82f)
                    lineTo(w * .50f, h * .58f); lineTo(w * .55f, h * .82f); lineTo(w * .62f, h * .82f); close()
                }
                drawPath(tower, Color(0xFF344054))
                drawLine(Color(0xFF344054), Offset(w * .40f, h * .63f), Offset(w * .60f, h * .63f), h * .024f)
                drawRect(Color(0xFF8E6F62), Offset(w * .05f, h * .58f), Size(w * .24f, h * .22f))
                drawRect(Color(0xFF8E6F62), Offset(w * .71f, h * .53f), Size(w * .24f, h * .27f))
            }
        }
    }
}
