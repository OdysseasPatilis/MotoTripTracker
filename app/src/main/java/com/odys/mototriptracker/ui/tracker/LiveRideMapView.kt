package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.ui.dashboard.DARK_MAP_STYLE_JSON
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette

@Composable
fun LiveRideMapView(
    traveledRoute: List<RouteCoordinate>,
    plannedRoute: List<RouteCoordinate>,
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
    val cameraPositionState = rememberCameraPositionState()
    val destination = remember(destinationLatitude, destinationLongitude) {
        if (destinationLatitude != null && destinationLongitude != null) {
            LatLng(destinationLatitude, destinationLongitude)
        } else {
            null
        }
    }

    LaunchedEffect(userLatitude, userLongitude, userBearing, userSpeedMps, isRiding) {
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
        properties = MapProperties(
            isMyLocationEnabled = true,
            mapStyleOptions = MapStyleOptions(DARK_MAP_STYLE_JSON)
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = isRiding,
            mapToolbarEnabled = false
        )
    ) {
        if (plannedRoute.size > 1) {
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
    }.toFloat()
}
