package com.odys.mototriptracker.ui.dashboard


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.odys.mototriptracker.ui.theme.LocalThemeStore
import com.odys.mototriptracker.ui.theme.ThemeMode

private val Mint = Color(0xFF5EFFC8)
private val Blue = Color(0xFF5B9EF7)
private val RouteAmber = Color(0xFFEF9F27)
private val RouteTeal = Color(0xFF1D9E75)
private val RouteCoral = Color(0xFFD85A30)

@Composable
fun MapPreviewCard(
    distance: String,
    encodedPolyline: String?, // NEW: Pass the string from TripEntity
    onClick: () -> Unit
) {
    // 1. Decode the string back into GPS points (Only runs when the string changes)
    val decodedPath = remember(encodedPolyline) {
        if (encodedPolyline?.isNotBlank() == true) {
            PolyUtil.decode(encodedPolyline)
        } else {
            emptyList()
        }
    }

    val cameraPositionState = rememberCameraPositionState()
    val themeStore = LocalThemeStore.current
    val themeMode by themeStore.mode.collectAsStateWithLifecycle()
    val mapStyle = remember(themeMode) {
        if (themeMode == ThemeMode.DARK) MapStyleOptions(DARK_MAP_STYLE_JSON) else null
    }

    // 2. Auto-Zoom to fit the route
    LaunchedEffect(decodedPath) {
        if (decodedPath.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            decodedPath.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()

            // We use .move() instead of .animate() here so it's instantly
            // framed when the card appears on the screen.
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 80)) // 80px padding
        }
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E24)) // Your SurfaceDark
            .clickable { onClick() }
    ) {

        // --- 3. THE REAL MAP ---
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapStyleOptions = mapStyle
            ),
            // Lock down all gestures so it feels like a static card
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                zoomGesturesEnabled = false,
                scrollGesturesEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            if (decodedPath.isNotEmpty()) {
                Polyline(
                    points = decodedPath,
                    color = Color(0xFF4DE1C1), // Your Mint color
                    width = 14f,               // Thick enough to see on a small card
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    geodesic = true
                )
            }
        }

        // --- Distance chip top-right ---
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x8C0E0E14))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(text = distance, color = Color(0xBFFFFFFF), fontSize = 11.sp)
        }

        // --- Bottom overlay: pulsing dot + label ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE61E1E24))
                .clickable { onClick() } // <-- ADD IT HERE INSTEAD!
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4DE1C1))
                )
                Spacer(Modifier.width(7.dp))

                // Keep the text simple
                Text(
                    text = "View full route  ↗",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ── Canvas: fake map + speed-coloured route ───────────────────────────────────
@Composable
internal fun RouteCanvas(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            // Grid lines
            val gridLines = buildList {
                // horizontal
                for (f in listOf(0.25f, 0.5f, 0.75f)) add(Pair(Offset(0f, h * f), Offset(w, h * f)))
                // vertical
                for (f in listOf(0.18f, 0.38f, 0.59f, 0.79f)) add(Pair(Offset(w * f, 0f), Offset(w * f, h)))
            }
            // Route control points (normalised)
            fun pt(x: Float, y: Float) = Offset(x * w, y * h)
            val slowEnd   = pt(0.32f, 0.60f)
            val midEnd    = pt(0.62f, 0.34f)
            val endPt     = pt(0.95f, 0.18f)
            val startPt   = pt(0.08f, 0.85f)
            onDrawBehind {
                // Background
                drawRect(color = Color(0xFF131320))
                // Grid
                gridLines.forEach { (a, b) ->
                    drawLine(Color(0xFF202032), a, b, strokeWidth = 1f)
                }
                // Road highlights
                drawLine(Color(0xFF2A2A42), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 2.5f)
                drawLine(Color(0xFF2A2A42), Offset(w * 0.38f, 0f), Offset(w * 0.38f, h), strokeWidth = 2.5f)

                val stroke = Stroke(width = 12f, cap = StrokeCap.Round)
                val thinStroke = Stroke(width = 4f, cap = StrokeCap.Round)

                // Glow pass
                val glowPath = Path().apply {
                    moveTo(startPt.x, startPt.y)
                    cubicTo(startPt.x + 40, startPt.y - 20, slowEnd.x - 30, slowEnd.y + 20, slowEnd.x, slowEnd.y)
                    cubicTo(slowEnd.x + 20, slowEnd.y - 30, midEnd.x - 40, midEnd.y + 20, midEnd.x, midEnd.y)
                    cubicTo(midEnd.x + 40, midEnd.y - 20, endPt.x - 60, endPt.y + 10, endPt.x, endPt.y)
                }
                drawPath(glowPath, Color(0x145EFFC8), style = stroke)
                // Segment 1: slow (amber)
                val seg1 = Path().apply {
                    moveTo(startPt.x, startPt.y)
                    cubicTo(startPt.x + 40, startPt.y - 20, slowEnd.x - 30, slowEnd.y + 20, slowEnd.x, slowEnd.y)
                }
                drawPath(seg1, RouteAmber, style = thinStroke)
                // Segment 2: cruise (teal)
                val seg2 = Path().apply {
                    moveTo(slowEnd.x, slowEnd.y)
                    cubicTo(slowEnd.x + 20, slowEnd.y - 30, midEnd.x - 40, midEnd.y + 20, midEnd.x, midEnd.y)
                }
                drawPath(seg2, RouteTeal, style = thinStroke)
                // Segment 3: fast (coral)
                val seg3 = Path().apply {
                    moveTo(midEnd.x, midEnd.y)
                    cubicTo(midEnd.x + 40, midEnd.y - 20, endPt.x - 60, endPt.y + 10, endPt.x, endPt.y)
                }
                drawPath(seg3, RouteCoral, style = thinStroke)
                // Start marker
                drawCircle(Color(0xFF0E0E14), radius = 10f, center = startPt)
                drawCircle(Mint, radius = 10f, center = startPt, style = Stroke(width = 2.5f))
                drawCircle(Mint, radius = 4f, center = startPt)
                // End marker
                drawCircle(Blue.copy(alpha = 0.2f), radius = 14f, center = endPt)
                drawCircle(Color(0xFF0E0E14), radius = 9f, center = endPt)
                drawCircle(Blue, radius = 9f, center = endPt, style = Stroke(width = 2.5f))
                drawCircle(Blue, radius = 3.5f, center = endPt)
            }
        }
    )
}

// ── Stats grid ────────────────────────────────────────────────────────────────
