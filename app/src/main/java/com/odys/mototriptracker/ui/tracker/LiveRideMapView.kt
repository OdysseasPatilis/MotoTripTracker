package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.odys.mototriptracker.data.navigation.NavRouteOption
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.ui.dashboard.DARK_MAP_STYLE_JSON
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import com.odys.mototriptracker.ui.theme.LocalThemeStore
import com.odys.mototriptracker.ui.theme.ThemeMode

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
    modifier: Modifier = Modifier,
    palette: AppPalette = LocalAppPalette.current
) {
    val themeStore = LocalThemeStore.current
    val themeMode by themeStore.mode.collectAsStateWithLifecycle()
    val mapStyle = remember(themeMode) {
        if (themeMode == ThemeMode.DARK) MapStyleOptions(DARK_MAP_STYLE_JSON) else null
    }

    val cameraPositionState = rememberCameraPositionState()
    val destination = remember(destinationLatitude, destinationLongitude) {
        if (destinationLatitude != null && destinationLongitude != null) {
            LatLng(destinationLatitude, destinationLongitude)
        } else {
            null
        }
    }

    val previewContentPadding = remember(isPreviewing) {
        if (isPreviewing) {
            // Leave room for the route-preview card so destination / route stay visible.
            PaddingValues(start = 48.dp, top = 72.dp, end = 48.dp, bottom = 240.dp)
        } else {
            PaddingValues(0.dp)
        }
    }

    LaunchedEffect(
        isPreviewing,
        previewRoutes,
        selectedPreviewRouteId,
        destination,
        userLatitude,
        userLongitude
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
        // Edge padding on top of contentPadding — zooms out enough that the pin clears the card.
        runCatching {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 80),
                450
            )
        }
    }

    LaunchedEffect(userLatitude, userLongitude, userBearing, userSpeedMps, isRiding, isPreviewing) {
        if (isPreviewing) return@LaunchedEffect
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
        cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(position), 450)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        contentPadding = previewContentPadding,
        properties = MapProperties(
            isMyLocationEnabled = true,
            mapStyleOptions = mapStyle
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false
        )
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
                        geodesic = true
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
                        geodesic = true
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
                geodesic = true
            )
        }

        if (traveledRoute.size > 1) {
            Polyline(
                points = traveledRoute.map { LatLng(it.latitude, it.longitude) },
                color = palette.mint,
                width = 15f,
                startCap = RoundCap(),
                endCap = RoundCap(),
                geodesic = true
            )
        }

        destination?.let { coord ->
            Marker(
                state = MarkerState(coord),
                title = "Destination"
            )
        }
    }
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
