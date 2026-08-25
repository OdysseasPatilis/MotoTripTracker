package com.odys.mototriptracker.ui.dashboard

// ─────────────────────────────────────────────────────────────────────────────
// build.gradle.kts (module level) — add:
//
//   implementation("com.google.maps.android:maps-compose:8.2.1")
//
// AndroidManifest.xml — add inside <application>:
//
//   <meta-data
//       android:name="com.google.android.geo.API_KEY"
//       android:value="${MAPS_API_KEY}" />
//
// local.properties — add:
//   MAPS_API_KEY=your_key_here
//
// res/raw/dark_map_style.json — create with the JSON at the bottom of this file.
// ─────────────────────────────────────────────────────────────────────────────

import android.R.attr.onClick
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.launch

// ── Colours ───────────────────────────────────────────────────────────────────
private val BgDark = Color(0xFF0E0E14)
private val SurfaceDark = Color(0xFF1A1A26)
private val CardDark = Color(0xFF1C1C2A)
private val PurpleActive = Color(0xFF5B5FEF)
private val Mint = Color(0xFF5EFFC8)
private val Blue = Color(0xFF5B9EF7)
private val Yellow = Color(0xFFFACC15)
private val RouteAmber = Color(0xFFEF9F27)
private val RouteTeal = Color(0xFF1D9E75)
private val RouteCoral = Color(0xFFD85A30)
private val RouteBlue = Color(0xFF378ADD)
private val TextHint = Color(0x40FFFFFF)
private val Overlay = Color(0xB20E0E14)

// ── Enums & models ────────────────────────────────────────────────────────────
enum class MapLayer { Speed, Elevation }
enum class WaypointType {
    Start,
    End,
    StopSign,
    TrafficLight,
    BriefStop,
    RestStop,
    TopSpeed,
    Summit,
    Unknown
}
data class Waypoint(
    val label: String,
    val detail: String,
    val time: String,
    val type: WaypointType,
    val position: LatLng
)

/**
 * One GPS point in the ride with telemetry.
 * Replace with your real data model (Room entity, proto, etc.)
 */

data class RidePoint(
    val latLng: LatLng,
    val speedKmh: Float,
    val elevationM: Float
)

private fun speedColor(kmh: Float): Color = when {
    kmh < 40f -> RouteAmber
    kmh < 130f -> RouteTeal
    else -> RouteCoral
}

private fun elevColor(elevM: Float, baseElevM: Float): Color {
    val gain = elevM - baseElevM
    return when {
        gain < 10f -> RouteBlue
        gain < 50f -> RouteAmber
        else -> RouteCoral
    }
}

/**
 * Splits a list of RidePoints into contiguous segments that share the same
 * colour for the given layer. Adjacent segments overlap by one point so the
 * polylines connect without gaps.
 */

private fun buildColoredSegments(
    points: List<RidePoint>,
    layer: MapLayer,
    baseElev: Float
): List<Pair<List<LatLng>, Color>> {
    if (points.size < 2) return emptyList()
    val segments = mutableListOf<Pair<List<LatLng>, Color>>()
    var segStart = 0
    var currentColor = if (layer == MapLayer.Speed)
        speedColor(points[0].speedKmh)
    else
        elevColor(points[0].elevationM, baseElev)

    for (i in 1..points.lastIndex) {
        val nextColor = if (layer == MapLayer.Speed)
            speedColor(points[i].speedKmh)
        else
            elevColor(points[i].elevationM, baseElev)

        val isLast = i == points.lastIndex
        if (nextColor != currentColor || isLast) {
            val endIdx = if (isLast) i else i
            segments += points.subList(segStart, endIdx + 1).map { it.latLng } to currentColor
            segStart = i
            currentColor = nextColor
        }
    }
    return segments
}

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun FullRouteScreenGMaps(
    summary: TripEntity,
    ridePoints: List<RidePoint>,
    waypoints: List<Waypoint> ,
    onBack: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val palette = LocalAppPalette.current
    var activeLayer by remember { mutableStateOf(MapLayer.Speed) }
    var isParentScrollEnabled by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState() // Hoist the screen scroll state
    val cameraState = rememberCameraPositionState() // Hoist the map camera state
    val coroutineScope = rememberCoroutineScope() // Needed for smooth animations

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgDeep)
            .verticalScroll(state = rememberScrollState(), enabled = isParentScrollEnabled)
            .padding(bottom = 32.dp)
    ) {
        RouteTopBar(onBack = onBack, onShare = onShare, palette = palette)
        Spacer(Modifier.height(4.dp))
        RouteMapCard(
            ridePoints = ridePoints,
            waypoints = waypoints,
            activeLayer = activeLayer,
            onLayerChange = { activeLayer = it },
            onMapTouch = { isTouched -> isParentScrollEnabled = !isTouched },
            cameraState = cameraState,
            palette = palette
        )
        Spacer(Modifier.height(12.dp))
        WaypointsPanel(summary, waypoints, palette, onWaypointClick = { latLng ->
            // WHEN A WAYPOINT IS CLICKED:
            coroutineScope.launch {
                // 1. Smoothly scroll the screen back to the top so they can see the map
                scrollState.animateScrollTo(0)

                // 2. Smoothly animate the Google Map camera to zoom in on the waypoint!
                // 16f is a great zoom level for street-level detail
                cameraState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            }
        })
        Spacer(Modifier.height(12.dp))
        ProfileChart(summary, ridePoints, activeLayer, palette)
        Spacer(Modifier.height(12.dp))
        LegendPills(activeLayer = activeLayer, palette = palette)
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────
@Composable
private fun RouteTopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
    palette: com.odys.mototriptracker.ui.theme.AppPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconCircleButton(onClick = onBack, palette = palette) {
            Icon(Icons.Default.ArrowBack, "Back", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
        }
        Text("Full route", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        IconCircleButton(shape = RoundedCornerShape(8.dp), onClick = onShare, palette = palette) {
            Icon(Icons.Default.Share, "Share", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun IconCircleButton(
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    onClick: () -> Unit,
    palette: com.odys.mototriptracker.ui.theme.AppPalette,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(palette.bgCard)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ── Map card ──────────────────────────────────────────────────────────────────
@Composable
private fun RouteMapCard(
    ridePoints: List<RidePoint>,
    waypoints: List<Waypoint>,
    activeLayer: MapLayer,
    onLayerChange: (MapLayer) -> Unit,
    onMapTouch: (Boolean) -> Unit,
    cameraState: CameraPositionState,
    palette: com.odys.mototriptracker.ui.theme.AppPalette
) {
    val bounds = remember(ridePoints) {
        // THE FIX: If the list is empty (still loading), return null safely
        if (ridePoints.isEmpty()) {
            null
        } else {
            // Otherwise, build the bounds
            val builder = LatLngBounds.builder()
            ridePoints.forEach { builder.include(it.latLng) }
            builder.build()
        }
    }


// Make sure the LaunchedEffect also expects the null safely
    LaunchedEffect(bounds) {
        if (bounds != null) {
            cameraState.move(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        }
    }


    val baseElev = remember(ridePoints) { ridePoints.firstOrNull()?.elevationM ?: 0f }
    val segments by remember(ridePoints, activeLayer) {
        derivedStateOf { buildColoredSegments(ridePoints, activeLayer, baseElev) }
    }
    val mapLatLngs = remember(ridePoints) { ridePoints.map { it.latLng } }
    val colorSpans = remember(ridePoints, activeLayer) {
        if (ridePoints.size < 2) return@remember emptyList<StyleSpan>()

        val spans = mutableListOf<StyleSpan>()
        for (i in 0 until ridePoints.size - 1) {
            val point = ridePoints[i]

            // Pick the color based on the active toggle
            val color = if (activeLayer == MapLayer.Speed) {
                speedColor(point.speedKmh)
            } else {
                elevColor(point.elevationM, baseElev)
            }

            // Convert the Compose Color to an Android ARGB Int for Google Maps
            spans.add(StyleSpan(color.toArgb()))
        }
        spans
    }

    // The big primary markers
    val startBitmap = remember { createMarkerBitmap(Mint.toArgb(), 28) }
    val endBitmap = remember { createMarkerBitmap(Blue.toArgb(), 28) }

// The premium telemetry highlights (Medium size)
    val speedBitmap = remember { createMarkerBitmap(RouteCoral.toArgb(), 24) }
    val summitBitmap = remember { createMarkerBitmap(Color(0xFFD988FF).toArgb(), 24) } // Purple
    val restBitmap = remember { createMarkerBitmap(RouteTeal.toArgb(), 24) }

// Standard pauses (Small size)
    val stopBitmap   = remember { createMarkerBitmap(Yellow.toArgb(), 18) }
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(20.dp))
            // This tells the parent scroll view "Hey, if the user touches here, let the Map handle it!"
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // "Initial" pass means we see the touch BEFORE Google Maps sees it
                        val event = awaitPointerEvent(PointerEventPass.Initial)

                        // Check if ANY finger is currently touching the screen
                        val isTouched = event.changes.any { it.pressed }

                        // Update the parent scroll state
                        onMapTouch(isTouched)
                    }
                }
            }
    ) {
        // ── Real Google Map ────────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(
                mapStyleOptions = MapStyleOptions(DARK_MAP_STYLE_JSON)
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true
            )
        ) {
            // Coloured route polylines
            if (mapLatLngs.size >= 2) {
                Polyline(
                    points = mapLatLngs,
                    spans = colorSpans, // Pass the dynamically generated colors here!
                    width = 14f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    zIndex = 1f
                )
            }

            waypoints.forEach { wp ->

                // 1. Pick the right graphic and Z-Index (so important things draw on top)
                val (bitmap, zIdx) = when (wp.type) {
                    WaypointType.Start -> Pair(startBitmap, 3f)
                    WaypointType.End -> Pair(endBitmap, 3f)

                    WaypointType.TopSpeed -> Pair(speedBitmap, 2.5f)
                    WaypointType.Summit -> Pair(summitBitmap, 2.5f)
                    WaypointType.RestStop -> Pair(restBitmap, 2f)

                    WaypointType.TrafficLight,
                    WaypointType.BriefStop,
                    WaypointType.StopSign -> Pair(stopBitmap, 1.5f)

                    else -> Pair(stopBitmap, 1f)
                }

                // 2. Draw the marker on the map
                Marker(
                    state = MarkerState(wp.position),
                    icon = BitmapDescriptorFactory.fromBitmap(bitmap),
                    title = wp.label,
                    snippet = wp.detail, // This will now show the actual street name or telemetry stat!
                    zIndex = zIdx
                )
            }
        }

        // ── Layer toggle (overlaid) ────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayerToggleButton("Speed", activeLayer == MapLayer.Speed, palette) { onLayerChange(MapLayer.Speed) }
            LayerToggleButton("Elevation", activeLayer == MapLayer.Elevation, palette) { onLayerChange(MapLayer.Elevation) }
        }

        // ── Legend bar (overlaid) ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Overlay)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val (c1, c2, c3, label) = if (activeLayer == MapLayer.Speed)
                listOf(RouteAmber, RouteTeal, RouteCoral, "Slow · Cruise · Fast")
            else
                listOf(RouteBlue, RouteAmber, RouteCoral, "Flat · Climb · Steep")
            LegendSegment(c1 as Color); LegendSegment(c2 as Color); LegendSegment(c3 as Color)
            Spacer(Modifier.width(4.dp))
            Text(label as String, color = palette.textMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LayerToggleButton(
    label: String,
    isActive: Boolean,
    palette: com.odys.mototriptracker.ui.theme.AppPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) palette.layerActive else palette.bgCard)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isActive) palette.textPrimary else palette.textMuted,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun LegendSegment(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 14.dp, height = 5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

// ── Waypoints ─────────────────────────────────────────────────────────────────
@Composable
private fun WaypointsPanel(
    summary: TripEntity,
    waypoints: List<Waypoint>,
    palette: com.odys.mototriptracker.ui.theme.AppPalette,
    onWaypointClick: (LatLng) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Route waypoints", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(formatTimestampToDate(summary.startTime), color = palette.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        waypoints.forEachIndexed { i, wp ->
            WaypointRow(wp, showLine = i < waypoints.lastIndex, palette = palette, onClick = { onWaypointClick(wp.position) })
        }
    }
}

@Composable
private fun WaypointRow(
    wp: Waypoint,
    showLine: Boolean,
    palette: com.odys.mototriptracker.ui.theme.AppPalette,
    onClick: () -> Unit
) {
    // 1. Determine the exact Icon and Color based on the smart type!
    val (icon: ImageVector?, dotColor: Color) = when (wp.type) {
        WaypointType.Start -> Pair(Icons.Default.PlayArrow, Mint)
        WaypointType.End -> Pair(Icons.Default.Place, Blue)
        WaypointType.TopSpeed -> Pair(Icons.Default.Bolt, RouteCoral) // ⚡
        WaypointType.Summit -> Pair(Icons.Default.Terrain, Color(0xFFD988FF)) // ⛰️ Purple
        WaypointType.RestStop -> Pair(Icons.Default.LocalCafe, RouteTeal) // ☕
        WaypointType.TrafficLight -> Pair(null, RouteAmber) // Keep as a simple dot
        WaypointType.BriefStop -> Pair(null, Yellow) // Keep as a simple dot
        WaypointType.StopSign -> Pair(null, RouteCoral) // Keep as a simple dot
        else ->  Pair(null, Color.Gray)
    }

    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top) {
        // --- TIMELINE GRAPHIC COLUMN ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Draw an Icon if we have one, otherwise fallback to the classic ring dot
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = wp.label,
                        tint = dotColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else {
                // The classic hollow dot for standard stops
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .drawWithCache {
                            onDrawBehind {
                                drawCircle(palette.bgDeep)
                                drawCircle(dotColor, style = Stroke(2f))
                                drawCircle(dotColor, radius = 3f)
                            }
                        }
                )
            }

            // Connecting Line
            if (showLine) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(38.dp) // Made slightly taller to fit the address subtext comfortably
                        .background(Color(0x1FFFFFFF))
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // --- TEXT COLUMN ---
        Column(modifier = Modifier.weight(1f)) {
            Text(wp.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            // The detail text will now automatically display the actual Geocoded Street Name!
            Text(wp.detail, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1)
            if (showLine) Spacer(Modifier.height(20.dp))
        }

        // --- TIME COLUMN ---
        Text(wp.time, color = palette.textMuted, fontSize = 12.sp)
    }
}

// ── Profile chart (elevation or speed) ───────────────────────────────────────
@Composable
private fun ProfileChart(
    summary: TripEntity,
    ridePoints: List<RidePoint>,
    activeLayer: MapLayer,
    palette: com.odys.mototriptracker.ui.theme.AppPalette
) {
    val values = remember(ridePoints, activeLayer) {
        ridePoints.map { if (activeLayer == MapLayer.Elevation) it.elevationM else it.speedKmh }
    }
    val lineColor = if (activeLayer == MapLayer.Elevation) Blue else RouteTeal
    val fillColor = if (activeLayer == MapLayer.Elevation) Color(0x1F5B9EF7) else Color(0x1F1D9E75)
    val peakVal = values.maxOrNull() ?: 0f
    val peakLabel = if (activeLayer == MapLayer.Elevation) "+${peakVal.toInt()} m peak"
    else "${peakVal.toInt()} km/h peak"
    val peakColor = if (activeLayer == MapLayer.Elevation) Blue else RouteCoral

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = if (activeLayer == MapLayer.Elevation) "ELEVATION PROFILE" else "SPEED PROFILE",
            color = palette.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.bgPanel)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val pad = 12f
                    val minV = values.minOrNull() ?: 0f
                    val maxV = values.maxOrNull() ?: 1f
                    val range = (maxV - minV).coerceAtLeast(1f)
                    val step = if (values.size > 1) (w - pad * 2) / (values.size - 1) else 0f
                    fun yFor(v: Float) = pad + (1f - (v - minV) / range) * (h - pad * 2)

                    val line = Path().apply {
                        values.forEachIndexed { i, v ->
                            if (i == 0) moveTo(pad, yFor(v)) else lineTo(pad + i * step, yFor(v))
                        }
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(pad + (values.size - 1) * step, h - pad)
                        lineTo(pad, h - pad)
                        close()
                    }
                    onDrawBehind {
                        drawPath(fill, fillColor)
                        drawPath(line, lineColor, style = Stroke(2f, cap = StrokeCap.Round))
                    }
                }
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 km", color = palette.textSecondary, fontSize = 9.sp)
            Text(peakLabel, color = peakColor, fontSize = 9.sp)
            Text("${String.format("%.1f ", summary.distanceMeters / 1000f)} km", color = palette.textSecondary, fontSize = 9.sp)
        }
    }
}

// ── Legend pills ──────────────────────────────────────────────────────────────
@Composable
private fun LegendPills(
    activeLayer: MapLayer,
    palette: com.odys.mototriptracker.ui.theme.AppPalette
) {
    val pills = if (activeLayer == MapLayer.Speed) listOf(
        Triple(RouteAmber, "Slow",   "0–40 km/h"),
        Triple(RouteTeal,  "Cruise", "40–130 km/h"),
        Triple(RouteCoral, "Fast",   "130+ km/h"),
    ) else listOf(
        Triple(RouteBlue,  "Flat",  "0–10 m"),
        Triple(RouteAmber, "Climb", "10–50 m"),
        Triple(RouteCoral, "Steep", "50 m+"),
    )
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pills.forEach { (color, label, range) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.bgPanel)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color))
                // Add maxLines = 1 to prevent wrapping
                Text(label, color = palette.textMuted, fontSize = 11.sp, maxLines = 1)
                Text(range, color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

// ── Marker bitmap ─────────────────────────────────────────────────────────────
private fun createMarkerBitmap(colorArgb: Int, sizeDp: Int): Bitmap {
    val px = sizeDp * 3
    val bmp = createBitmap(px, px)
    val canvas = AndroidCanvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb; style = Paint.Style.FILL
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, 14, 14, 20)
        style = Paint.Style.STROKE
        strokeWidth = px * 0.12f
    }
    val r = px / 2f
    canvas.drawCircle(r, r, r - border.strokeWidth, fill)
    canvas.drawCircle(r, r, r - border.strokeWidth, border)
    return bmp
}

// ── Preview ───────────────────────────────────────────────────────────────────
/*
@Preview(showBackground = true, backgroundColor = 0xFF0E0E14, widthDp = 390, heightDp = 900)
@Composable
fun FullRouteScreenPreview() {
    FullRouteScreenGMaps()
}
*/

// ── Dark map style JSON (paste into res/raw/dark_map_style.json) ──────────────
val DARK_MAP_STYLE_JSON = """
[
  { "elementType": "geometry",        "stylers": [{ "color": "#1a1a2e" }] },
  { "elementType": "labels.text.fill","stylers": [{ "color": "#6b6b8d" }] },
  { "elementType": "labels.text.stroke","stylers": [{ "color": "#0e0e14" }] },
  { "featureType": "road",            "elementType": "geometry",       "stylers": [{ "color": "#2a2a42" }] },
  { "featureType": "road.highway",    "elementType": "geometry",       "stylers": [{ "color": "#3a3a58" }] },
  { "featureType": "road",            "elementType": "labels.text.fill","stylers": [{ "color": "#4a4a6a" }] },
  { "featureType": "water",           "elementType": "geometry",       "stylers": [{ "color": "#0d1b2a" }] },
  { "featureType": "poi",             "elementType": "geometry",       "stylers": [{ "color": "#16162a" }] },
  { "featureType": "poi",             "elementType": "labels",         "stylers": [{ "visibility": "off" }] },
  { "featureType": "transit",         "stylers": [{ "visibility": "off" }] },
  { "featureType": "administrative",  "elementType": "geometry",       "stylers": [{ "color": "#2a2a42" }] }
]
""".trimIndent()
