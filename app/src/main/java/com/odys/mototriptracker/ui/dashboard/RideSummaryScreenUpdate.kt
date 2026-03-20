package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.odys.mototriptracker.data.trip.TripEntity

// ── Colours ──────────────────────────────────────────────────────────────────
private val BackgroundDark = Color(0xFF0E0E14)
private val SurfaceDark = Color(0xFF1A1A26)
private val CardDark = Color(0xFF1C1C2A)
private val PurpleAccent = Color(0xFF5B5FEF)
private val Mint = Color(0xFF5EFFC8)
private val Red = Color(0xFFFF5F5F)
private val Blue = Color(0xFF5B9EF7)
private val Purple = Color(0xFFC084FC)

private val TextHint = Color(0x40FFFFFF)
private val RouteAmber = Color(0xFFEF9F27)
private val RouteTeal = Color(0xFF1D9E75)
private val RouteCoral = Color(0xFFD85A30)

// ── Entry point ───────────────────────────────────────────────────────────────
@Composable
fun RideSummaryScreenUpdate(
    summary: TripEntity,
    onBack: () -> Unit = {},
    onDelete: () -> Unit = {},
    onViewRoute: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        TopBar(onBack = onBack, onDelete = onDelete)
        Spacer(Modifier.height(4.dp))
        DateCard(summary)
        Spacer(Modifier.height(10.dp))
        MapPreviewCard(
            "${String.format("%.1f ", summary.distanceMeters / 1000f)} km",
            summary.encodedRoutePolyline,
            onClick = onViewRoute
        )
        Spacer(Modifier.height(10.dp))
        StatsGrid(summary)
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(onBack: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CardDark)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = "Ride Summary",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF3A1A1A))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color(0xFFE24B4A),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Date card ─────────────────────────────────────────────────────────────────
@Composable
private fun DateCard(summary: TripEntity) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF5B5FEF), Color(0xFF7C4DFF))
                )
            )
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DATE & TIME",
                color = Color(0xAAFFFFFF),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTimestampToDate(summary.startTime),
                color = Mint,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Map preview card ──────────────────────────────────────────────────────────
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
                mapStyleOptions = MapStyleOptions(DARK_MAP_STYLE_JSON)
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
private fun RouteCanvas(modifier: Modifier = Modifier) {
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
@Composable
private fun StatsGrid(summary: TripEntity) {
    val totalTime = summary.movingTime + summary.stoppedTime
    val stats = listOf(
        StatItem("Distance",   "${String.format("%.1f ", summary.distanceMeters / 1000f)}",  "km",    TextPrimary),
        StatItem("Total time", formatSecondsToTime(totalTime),"mm:ss", TextPrimary),
        StatItem("Moving",     formatSecondsToTime(summary.movingTime),"mm:ss", Mint),
        StatItem("Stopped",    formatSecondsToTime(summary.stoppedTime),"mm:ss", Red),
        StatItem("Avg speed",  summary.avgSpeed.toInt().toString(),   "km/h",  TextPrimary),
        StatItem("Max speed",  summary.maxSpeed.toInt().toString(),  "km/h",  Blue),
        StatItem("Elevation",  "+${summary.elevationGain.toInt()}",  "meters",TextPrimary),
        StatItem("Max G",      String.format("%.2f", summary.maxGForce), "G-force",Purple),
    )

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { stat ->
                    StatCard(stat, modifier = Modifier.weight(1f))
                }
                // fill empty slot if odd number of stats
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class StatItem(
    val label: String,
    val value: String,
    val unit: String,
    val valueColor: Color
)

@Composable
private fun StatCard(stat: StatItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = stat.label.uppercase(),
            color = TextHint,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stat.value,
            color = stat.valueColor,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stat.unit,
            color = TextHint,
            fontSize = 11.sp
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────
/*
@Preview(showBackground = true, backgroundColor = 0xFF0E0E14, widthDp = 390, heightDp = 800)
@Composable
fun RideSummaryScreenPreview() {
    RideSummaryScreenUpdate()
}*/
