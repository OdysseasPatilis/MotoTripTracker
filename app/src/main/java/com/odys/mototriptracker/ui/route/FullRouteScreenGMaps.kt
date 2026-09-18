package com.odys.mototriptracker.ui.route

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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.rememberCameraPositionState
import com.odys.mototriptracker.domain.model.RoutePoint
import com.odys.mototriptracker.domain.model.Trip
import com.odys.mototriptracker.domain.RouteReplayEngine
import com.odys.mototriptracker.ui.route.RouteReplayPanel
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun FullRouteScreenGMaps(
    summary: Trip,
    ridePoints: List<RidePoint>,
    routePoints: List<RoutePoint> = emptyList(),
    waypoints: List<Waypoint>,
    usedPolylineFallback: Boolean = false,
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
    val replayEngine = remember(routePoints) { RouteReplayEngine(routePoints) }
    val replayFrame = remember(replayElapsed, routePoints) { replayEngine.frame(replayElapsed) }
    val replayTrail = remember(replayFrame) {
        replayFrame?.let { replayEngine.trailCoordinates(it) }.orEmpty()
    }
    val replayRemaining = remember(replayFrame, routePoints) {
        replayFrame?.let { remainingReplayCoordinates(routePoints, it) }.orEmpty()
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
            .verticalScroll(state = scrollState, enabled = isParentScrollEnabled)
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
            points = routePoints,
            palette = palette,
            onReplayPosition = { replayElapsed = it },
            onReplayPlayingChanged = { isReplayPlaying = it },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))
        WaypointsPanel(
            summary = summary,
            waypoints = waypoints,
            usedPolylineFallback = usedPolylineFallback,
            palette = palette,
            onWaypointClick = { latLng ->
                coroutineScope.launch {
                    scrollState.animateScrollTo(0)
                    cameraState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        ProfileChart(summary, ridePoints, activeLayer, palette)
        Spacer(Modifier.height(12.dp))
        LegendPills(activeLayer = activeLayer, palette = palette)
    }
}
