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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.domain.RouteReplayEngine
import com.odys.mototriptracker.domain.RouteReplayFrame
import com.odys.mototriptracker.ui.route.RouteReplayPanel
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import com.odys.mototriptracker.ui.theme.LocalThemeStore
import com.odys.mototriptracker.ui.theme.ThemeMode
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import java.util.Locale

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

/** Shared icon + colour used by the waypoint list and map markers. */
private data class WaypointStyle(val icon: ImageVector?, val color: Color)

private fun waypointStyle(type: WaypointType): WaypointStyle = when (type) {
    WaypointType.Start -> WaypointStyle(Icons.Default.PlayArrow, Mint)
    WaypointType.End -> WaypointStyle(Icons.Default.Place, Blue)
    WaypointType.TopSpeed -> WaypointStyle(Icons.Default.Bolt, RouteCoral)
    WaypointType.Summit -> WaypointStyle(Icons.Default.Terrain, Color(0xFFD988FF))
    WaypointType.RestStop -> WaypointStyle(Icons.Default.LocalCafe, RouteTeal)
    WaypointType.TrafficLight -> WaypointStyle(null, RouteAmber)
    WaypointType.BriefStop -> WaypointStyle(null, Yellow)
    WaypointType.StopSign -> WaypointStyle(null, RouteCoral)
    WaypointType.Unknown -> WaypointStyle(null, Color.Gray)
}

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

private fun remainingReplayCoordinates(
    points: List<RoutePointEntity>,
    frame: RouteReplayFrame
): List<RouteCoordinate> {
    val remainingStart = minOf(frame.segmentIndex + 1, points.size - 1)
    if (remainingStart >= points.size - 1) return emptyList()
    val coords = points.subList(remainingStart, points.size).map {
        RouteCoordinate(it.latitude, it.longitude)
    }.toMutableList()
    coords.add(0, RouteCoordinate(frame.latitude, frame.longitude))
    return coords
}

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun FullRouteScreenGMaps(
    summary: TripEntity,
    ridePoints: List<RidePoint>,
    routePointEntities: List<RoutePointEntity> = emptyList(),
    waypoints: List<Waypoint> ,
    onBack: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val palette = LocalAppPalette.current
    var activeLayer by remember { mutableStateOf(MapLayer.Speed) }
    var isParentScrollEnabled by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val cameraState = rememberCameraPositionState()
    val coroutineScope = rememberCoroutineScope()
    var replayElapsed by remember { mutableDoubleStateOf(0.0) }
    var isReplayPlaying by remember { mutableStateOf(false) }
    val replayEngine = remember(routePointEntities) { RouteReplayEngine(routePointEntities) }
    val replayFrame = remember(replayElapsed, routePointEntities) { replayEngine.frame(replayElapsed) }
    val replayTrail = remember(replayFrame) {
        replayFrame?.let { replayEngine.trailCoordinates(it) }.orEmpty()
    }
    val replayRemaining = remember(replayFrame, routePointEntities) {
        replayFrame?.let { remainingReplayCoordinates(routePointEntities, it) }.orEmpty()
    }
    val isReplayActive = isReplayPlaying || replayElapsed > 0.0
    val replayMarkerLatLng = replayFrame?.let { LatLng(it.latitude, it.longitude) }

    // Zoom once when play starts, then follow with throttled instant moves (no animate → no lag).
    LaunchedEffect(isReplayPlaying) {
        if (!isReplayPlaying) return@LaunchedEffect
        val start = snapshotFlow {
            replayEngine.frame(replayElapsed)?.let { LatLng(it.latitude, it.longitude) }
        }.filterNotNull().first()
        cameraState.animate(CameraUpdateFactory.newLatLngZoom(start, 16f))
        var lastMoveAt = 0L
        snapshotFlow {
            replayEngine.frame(replayElapsed)?.let { LatLng(it.latitude, it.longitude) }
        }
            .filterNotNull()
            .collect { latLng ->
                val now = System.currentTimeMillis()
                if (now - lastMoveAt >= 100L) {
                    lastMoveAt = now
                    cameraState.move(CameraUpdateFactory.newLatLng(latLng))
                }
            }
    }

    LaunchedEffect(isReplayActive) {
        if (!isReplayActive && ridePoints.isNotEmpty()) {
            val builder = LatLngBounds.builder()
            ridePoints.forEach { builder.include(it.latLng) }
            cameraState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), 80))
        }
    }

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
            palette = palette,
            isReplayActive = isReplayActive,
            replayTrail = replayTrail.map { LatLng(it.latitude, it.longitude) },
            replayRemaining = replayRemaining.map { LatLng(it.latitude, it.longitude) },
            replayMarker = replayMarkerLatLng
        )
        Spacer(Modifier.height(8.dp))
        RouteReplayPanel(
            points = routePointEntities,
            palette = palette,
            onReplayPosition = { replayElapsed = it },
            onReplayPlayingChanged = { isReplayPlaying = it },
            modifier = Modifier.padding(horizontal = 20.dp)
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.textPrimary, modifier = Modifier.size(18.dp))
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
    palette: com.odys.mototriptracker.ui.theme.AppPalette,
    isReplayActive: Boolean = false,
    replayTrail: List<LatLng> = emptyList(),
    replayRemaining: List<LatLng> = emptyList(),
    replayMarker: LatLng? = null
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
    LaunchedEffect(bounds, isReplayActive) {
        if (bounds != null && !isReplayActive) {
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

    val density = LocalDensity.current
    val startPainter = rememberVectorPainter(Icons.Default.PlayArrow)
    val endPainter = rememberVectorPainter(Icons.Default.Place)
    val speedPainter = rememberVectorPainter(Icons.Default.Bolt)
    val summitPainter = rememberVectorPainter(Icons.Default.Terrain)
    val restPainter = rememberVectorPainter(Icons.Default.LocalCafe)

    val startBitmap = remember(density) {
        createIconBadgeBitmap(startPainter, Mint, density, sizeDp = 40f)
    }
    val endBitmap = remember(density) {
        createIconBadgeBitmap(endPainter, Blue, density, sizeDp = 40f)
    }
    val speedBitmap = remember(density) {
        createIconBadgeBitmap(speedPainter, RouteCoral, density, sizeDp = 36f)
    }
    val summitBitmap = remember(density) {
        createIconBadgeBitmap(summitPainter, Color(0xFFD988FF), density, sizeDp = 36f)
    }
    val restBitmap = remember(density) {
        createIconBadgeBitmap(restPainter, RouteTeal, density, sizeDp = 36f)
    }
    val trafficBitmap = remember { createHollowDotBitmap(RouteAmber.toArgb()) }
    val briefStopBitmap = remember { createHollowDotBitmap(Yellow.toArgb()) }
    val stopSignBitmap = remember { createHollowDotBitmap(RouteCoral.toArgb()) }
    val unknownBitmap = remember { createHollowDotBitmap(android.graphics.Color.GRAY) }
    val riderBitmap = remember { createRiderMarkerBitmap(Mint.toArgb()) }
    val replayMarkerState = remember { MarkerState() }
    val themeStore = LocalThemeStore.current
    val themeMode by themeStore.mode.collectAsStateWithLifecycle()
    val mapStyle = remember(themeMode) {
        if (themeMode == ThemeMode.DARK) {
            MapStyleOptions(DARK_MAP_STYLE_JSON)
        } else {
            null
        }
    }
    LaunchedEffect(replayMarker) {
        replayMarker?.let { replayMarkerState.position = it }
    }
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
                mapStyleOptions = mapStyle
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
            // Coloured route polylines — hidden during replay so the trail draws progressively
            if (!isReplayActive && mapLatLngs.size >= 2) {
                Polyline(
                    points = mapLatLngs,
                    spans = colorSpans,
                    width = 14f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    zIndex = 1f
                )
            }

            if (isReplayActive) {
                if (replayRemaining.size >= 2) {
                    Polyline(
                        points = replayRemaining,
                        color = palette.textSecondary.copy(alpha = 0.35f),
                        width = 10f,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        zIndex = 2f
                    )
                }
                if (replayTrail.size >= 2) {
                    Polyline(
                        points = replayTrail,
                        color = Mint,
                        width = 18f,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        zIndex = 4f
                    )
                }
            }
            if (replayMarker != null) {
                Marker(
                    state = replayMarkerState,
                    icon = BitmapDescriptorFactory.fromBitmap(riderBitmap),
                    anchor = Offset(0.5f, 0.5f),
                    title = "Rider",
                    zIndex = 5f,
                    flat = true
                )
            }

            waypoints.forEach { wp ->
                val (bitmap, zIdx) = when (wp.type) {
                    WaypointType.Start -> Pair(startBitmap, 3f)
                    WaypointType.End -> Pair(endBitmap, 3f)
                    WaypointType.TopSpeed -> Pair(speedBitmap, 2.5f)
                    WaypointType.Summit -> Pair(summitBitmap, 2.5f)
                    WaypointType.RestStop -> Pair(restBitmap, 2f)
                    WaypointType.TrafficLight -> Pair(trafficBitmap, 1.5f)
                    WaypointType.BriefStop -> Pair(briefStopBitmap, 1.5f)
                    WaypointType.StopSign -> Pair(stopSignBitmap, 1.5f)
                    WaypointType.Unknown -> Pair(unknownBitmap, 1f)
                }

                Marker(
                    state = MarkerState(wp.position),
                    icon = BitmapDescriptorFactory.fromBitmap(bitmap),
                    anchor = Offset(0.5f, 0.5f),
                    title = wp.label,
                    snippet = wp.detail,
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
    val style = waypointStyle(wp.type)
    val icon = style.icon
    val dotColor = style.color

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
            Text("${String.format(Locale.US, "%.1f ", summary.distanceMeters / 1000f)} km", color = palette.textSecondary, fontSize = 9.sp)
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

// ── Marker bitmaps (match waypoint list chips / hollow dots) ──────────────────
private fun createIconBadgeBitmap(
    painter: Painter,
    tint: Color,
    density: Density,
    sizeDp: Float = 40f
): Bitmap {
    val px = with(density) { sizeDp.dp.roundToPx().coerceAtLeast(1) }
    val bmp = createBitmap(px, px)
    val androidCanvas = AndroidCanvas(bmp)
    val composeCanvas = ComposeCanvas(androidCanvas)
    val drawSize = Size(px.toFloat(), px.toFloat())

    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = composeCanvas,
        size = drawSize
    ) {
        val radius = size.minDimension / 2f
        drawCircle(color = tint.copy(alpha = 0.22f), radius = radius)
        drawCircle(
            color = tint.copy(alpha = 0.5f),
            radius = radius - 1.5f,
            style = Stroke(width = 2.5f)
        )
        val iconSize = size.minDimension * 0.55f
        val inset = (size.minDimension - iconSize) / 2f
        translate(inset, inset) {
            with(painter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(tint)
                )
            }
        }
    }
    return bmp
}

/** Hollow ring + core — matches the list timeline dots for stops. */
private fun createHollowDotBitmap(colorArgb: Int, sizePx: Int = 54): Bitmap {
    val bmp = createBitmap(sizePx, sizePx)
    val canvas = AndroidCanvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = sizePx * 0.32f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, 14, 14, 20)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r, fill)

    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.09f
    }
    canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2f, ring)

    val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.1f, core)
    return bmp
}

/** Soft glow ring + solid core — matches iOS replay rider annotation. */
private fun createRiderMarkerBitmap(colorArgb: Int): Bitmap {
    val px = 84
    val bmp = createBitmap(px, px)
    val canvas = AndroidCanvas(bmp)
    val cx = px / 2f
    val cy = px / 2f

    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(
            70,
            android.graphics.Color.red(colorArgb),
            android.graphics.Color.green(colorArgb),
            android.graphics.Color.blue(colorArgb)
        )
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, px * 0.42f, glow)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, px * 0.22f, fill)

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, 14, 14, 20)
        style = Paint.Style.STROKE
        strokeWidth = px * 0.06f
    }
    canvas.drawCircle(cx, cy, px * 0.22f, border)
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
