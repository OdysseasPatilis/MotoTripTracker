package com.odys.mototriptracker.ui.tracker

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.odys.mototriptracker.data.camera.TrafficCamera
import com.odys.mototriptracker.data.camera.TrafficCameraKind
import com.odys.mototriptracker.data.navigation.NavRouteOption
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.ui.dashboard.LIVE_DARK_MAP_STYLE_JSON
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import com.odys.mototriptracker.ui.theme.LocalThemeStore
import com.odys.mototriptracker.ui.theme.ThemeMode
import kotlin.math.abs

@Composable
fun LiveRideMapView(
    traveledRoute: List<RouteCoordinate>,
    plannedRoute: List<RouteCoordinate>,
    previewRoutes: List<NavRouteOption> = emptyList(),
    selectedPreviewRouteId: String? = null,
    isPreviewing: Boolean = false,
    destinationLatitude: Double?,
    destinationLongitude: Double?,
    isRiding: Boolean,
    userLatitude: Double?,
    userLongitude: Double?,
    userBearing: Float,
    userSpeedMps: Float,
    trafficCameras: List<TrafficCamera> = emptyList(),
    showTrafficCameras: Boolean = false,
    hasSelectedPlace: Boolean = false,
    onVisibleRegionChanged: (
        centerLat: Double,
        centerLng: Double,
        latDelta: Double,
        lngDelta: Double,
        fetchRemote: Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
    onPoiClick: (placeId: String, name: String, lat: Double, lng: Double) -> Unit = { _, _, _, _ -> },
    onRecenter: () -> Unit = {},
    modifier: Modifier = Modifier,
    palette: AppPalette = LocalAppPalette.current,
) {
    val themeStore = LocalThemeStore.current
    val themeMode by themeStore.mode.collectAsStateWithLifecycle()
    val mapStyle = remember(themeMode) {
        if (themeMode == ThemeMode.DARK) MapStyleOptions(LIVE_DARK_MAP_STYLE_JSON) else null
    }

    val cameraPositionState = rememberCameraPositionState()
    var isFollowingUser by remember { mutableStateOf(true) }
    var skipNextIdle by remember { mutableStateOf(false) }

    val destination = remember(destinationLatitude, destinationLongitude) {
        if (destinationLatitude != null && destinationLongitude != null) {
            LatLng(destinationLatitude, destinationLongitude)
        } else {
            null
        }
    }

    val previewContentPadding = remember(isPreviewing) {
        if (isPreviewing) {
            PaddingValues(start = 48.dp, top = 72.dp, end = 48.dp, bottom = 240.dp)
        } else {
            PaddingValues(0.dp)
        }
    }

    val showRecenter = !isFollowingUser && !isPreviewing
    val recenterBottomPadding = if (hasSelectedPlace) 250.dp else 100.dp

    LaunchedEffect(isPreviewing) {
        if (isPreviewing) {
            isFollowingUser = false
        } else {
            // Leaving preview restores follow (iOS parity).
            isFollowingUser = true
        }
    }

    LaunchedEffect(
        isPreviewing,
        previewRoutes,
        selectedPreviewRouteId,
        destination,
        userLatitude,
        userLongitude,
    ) {
        if (!isPreviewing || previewRoutes.isEmpty()) return@LaunchedEffect
        val selected = previewRoutes.firstOrNull { it.id == selectedPreviewRouteId }
            ?: previewRoutes.first()
        val builder = LatLngBounds.Builder()
        var hasPoint = false
        selected.coordinates.forEach {
            builder.include(LatLng(it.latitude, it.longitude))
            hasPoint = true
        }
        destination?.let {
            builder.include(it)
            hasPoint = true
        }
        userLatitude?.let { lat ->
            userLongitude?.let { lng ->
                builder.include(LatLng(lat, lng))
                hasPoint = true
            }
        }
        if (!hasPoint) return@LaunchedEffect
        isFollowingUser = false
        skipNextIdle = true
        runCatching {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 80),
                450,
            )
        }
    }

    LaunchedEffect(userLatitude, userLongitude, userBearing, userSpeedMps, isRiding, isPreviewing, isFollowingUser) {
        if (isPreviewing || !isFollowingUser) return@LaunchedEffect
        val lat = userLatitude ?: return@LaunchedEffect
        val lng = userLongitude ?: return@LaunchedEffect
        val speedKmh = (userSpeedMps.coerceAtLeast(0f)) * 3.6f
        val position = if (isRiding) {
            val distance = 350.0 + minOf(speedKmh, 180f) * 7.0
            CameraPosition.Builder()
                .target(LatLng(lat, lng))
                .zoom(zoomFromDistance(distance))
                .bearing(if (userBearing >= 0f) userBearing else 0f)
                .tilt(55f)
                .build()
        } else {
            CameraPosition.Builder()
                .target(LatLng(lat, lng))
                .zoom(14.5f)
                .bearing(0f)
                .tilt(0f)
                .build()
        }
        skipNextIdle = true
        cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(position), 450)
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) return@LaunchedEffect

        val wasGesture =
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        val wasProgrammatic = skipNextIdle
        if (wasProgrammatic) {
            skipNextIdle = false
        }

        var following = isFollowingUser
        if (wasGesture && !isPreviewing && following) {
            following = false
            isFollowingUser = false
        }

        val projection = cameraPositionState.projection ?: return@LaunchedEffect
        val bounds = projection.visibleRegion.latLngBounds
        val center = bounds.center
        val latDelta = abs(bounds.northeast.latitude - bounds.southwest.latitude)
            .coerceAtLeast(0.002)
        val lngDelta = abs(bounds.northeast.longitude - bounds.southwest.longitude)
            .coerceAtLeast(0.002)
        val exploring = !following && !isPreviewing
        onVisibleRegionChanged(
            center.latitude,
            center.longitude,
            latDelta,
            lngDelta,
            exploring,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = previewContentPadding,
            properties = MapProperties(
                isMyLocationEnabled = true,
                isTrafficEnabled = true,
                mapStyleOptions = mapStyle,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false,
            ),
            onPOIClick = { poi ->
                isFollowingUser = false
                onPoiClick(
                    poi.placeId,
                    poi.name.ifBlank { "Selected place" },
                    poi.latLng.latitude,
                    poi.latLng.longitude,
                )
            },
        ) {
            if (isPreviewing && previewRoutes.isNotEmpty()) {
                val alternates = previewRoutes.filter { it.id != selectedPreviewRouteId }
                val selected = previewRoutes.filter { it.id == selectedPreviewRouteId }
                alternates.forEach { option ->
                    if (option.coordinates.size > 1) {
                        Polyline(
                            points = option.coordinates.map { LatLng(it.latitude, it.longitude) },
                            color = palette.neonBlue.copy(alpha = 0.35f),
                            width = 10f,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            geodesic = true,
                        )
                    }
                }
                selected.forEach { option ->
                    if (option.coordinates.size > 1) {
                        Polyline(
                            points = option.coordinates.map { LatLng(it.latitude, it.longitude) },
                            color = palette.neonBlue,
                            width = 18f,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            geodesic = true,
                        )
                    }
                }
            } else if (plannedRoute.size > 1) {
                Polyline(
                    points = plannedRoute.map { LatLng(it.latitude, it.longitude) },
                    color = palette.neonBlue,
                    width = 18f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    geodesic = true,
                )
            }

            if (traveledRoute.size > 1) {
                Polyline(
                    points = traveledRoute.map { LatLng(it.latitude, it.longitude) },
                    color = palette.mint,
                    width = 15f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    geodesic = true,
                )
            }

            destination?.let { coord ->
                Marker(
                    state = MarkerState(coord),
                    title = "Destination",
                )
            }

            if (showTrafficCameras) {
                val speedIcon = remember(palette.routeAmber) {
                    cameraMarkerIcon(palette.routeAmber.toArgb(), kind = TrafficCameraKind.Speed)
                }
                val redLightIcon = remember(palette.neonBlue) {
                    cameraMarkerIcon(palette.neonBlue.toArgb(), kind = TrafficCameraKind.RedLight)
                }
                trafficCameras.forEach { camera ->
                    key(camera.id) {
                        val position = LatLng(camera.latitude, camera.longitude)
                        val markerState = remember(camera.id) { MarkerState(position) }
                        Marker(
                            state = markerState,
                            title = if (camera.kind == TrafficCameraKind.Speed) {
                                "Speed camera"
                            } else {
                                "Red light camera"
                            },
                            icon = if (camera.kind == TrafficCameraKind.Speed) {
                                speedIcon
                            } else {
                                redLightIcon
                            },
                            anchor = Offset(0.5f, 0.5f),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showRecenter,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = recenterBottomPadding),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            IconButton(
                onClick = {
                    isFollowingUser = true
                    onRecenter()
                    val lat = userLatitude ?: return@IconButton
                    val lng = userLongitude ?: return@IconButton
                    skipNextIdle = true
                    cameraPositionState.move(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(lat, lng))
                                .zoom(if (isRiding) 16f else 14.5f)
                                .bearing(if (isRiding && userBearing >= 0f) userBearing else 0f)
                                .tilt(if (isRiding) 55f else 0f)
                                .build(),
                        ),
                    )
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(palette.bgPanel.copy(alpha = 0.92f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = "Recenter map on my location",
                    tint = palette.neonBlue,
                )
            }
        }
    }
}

private fun cameraMarkerIcon(colorArgb: Int, kind: TrafficCameraKind): BitmapDescriptor {
    val size = 72
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val cx = size / 2f
    val cy = size / 2f
    canvas.drawCircle(cx, cy, size / 2.4f, fill)

    when (kind) {
        TrafficCameraKind.Speed -> {
            val bodyLeft = cx - 14f
            val bodyTop = cy - 8f
            val bodyW = 26f
            val bodyH = 18f
            canvas.drawRoundRect(bodyLeft, bodyTop, bodyLeft + bodyW, bodyTop + bodyH, 4f, 4f, glyph)
            canvas.drawRoundRect(cx - 4f, bodyTop - 5f, cx + 8f, bodyTop + 2f, 2f, 2f, glyph)
            glyph.color = colorArgb
            canvas.drawCircle(cx + 1f, cy + 1f, 6f, glyph)
            glyph.color = android.graphics.Color.WHITE
            canvas.drawCircle(cx + 1f, cy + 1f, 3.5f, glyph)
        }
        TrafficCameraKind.RedLight -> {
            val left = cx - 7f
            val top = cy - 14f
            canvas.drawRoundRect(left, top, left + 14f, top + 28f, 4f, 4f, glyph)
            glyph.color = colorArgb
            canvas.drawCircle(cx, cy - 8f, 3.2f, glyph)
            canvas.drawCircle(cx, cy, 3.2f, glyph)
            canvas.drawCircle(cx, cy + 8f, 3.2f, glyph)
        }
    }
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun zoomFromDistance(distanceMeters: Double): Float {
    return when {
        distanceMeters <= 400 -> 17.5f
        distanceMeters <= 700 -> 16.8f
        distanceMeters <= 1000 -> 16.2f
        distanceMeters <= 1400 -> 15.6f
        else -> 15f
    }
}
