package com.odys.mototriptracker.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.android.gms.maps.model.LatLng
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.domain.RouteCoordinate
import com.odys.mototriptracker.domain.RouteReplayFrame

// ── Colours (namespaced to avoid clashing with other dashboard files) ─────────
internal object FullRouteColors {
    val BgDark = Color(0xFF0E0E14)
    val SurfaceDark = Color(0xFF1A1A26)
    val CardDark = Color(0xFF1C1C2A)
    val PurpleActive = Color(0xFF5B5FEF)
    val Mint = Color(0xFF5EFFC8)
    val Blue = Color(0xFF5B9EF7)
    val Yellow = Color(0xFFFACC15)
    val RouteAmber = Color(0xFFEF9F27)
    val RouteTeal = Color(0xFF1D9E75)
    val RouteCoral = Color(0xFFD85A30)
    val RouteBlue = Color(0xFF378ADD)
    val TextHint = Color(0x40FFFFFF)
    val Overlay = Color(0xB20E0E14)
}

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
internal data class WaypointStyle(val icon: ImageVector?, val color: Color)

internal fun waypointStyle(type: WaypointType): WaypointStyle = when (type) {
    WaypointType.Start -> WaypointStyle(Icons.Default.PlayArrow, FullRouteColors.Mint)
    WaypointType.End -> WaypointStyle(Icons.Default.Place, FullRouteColors.Blue)
    WaypointType.TopSpeed -> WaypointStyle(Icons.Default.Bolt, FullRouteColors.RouteCoral)
    WaypointType.Summit -> WaypointStyle(Icons.Default.Terrain, Color(0xFFD988FF))
    WaypointType.RestStop -> WaypointStyle(Icons.Default.LocalCafe, FullRouteColors.RouteTeal)
    WaypointType.TrafficLight -> WaypointStyle(null, FullRouteColors.RouteAmber)
    WaypointType.BriefStop -> WaypointStyle(null, FullRouteColors.Yellow)
    WaypointType.StopSign -> WaypointStyle(null, FullRouteColors.RouteCoral)
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

internal fun speedColor(kmh: Float): Color = when {
    kmh < 40f -> FullRouteColors.RouteAmber
    kmh < 130f -> FullRouteColors.RouteTeal
    else -> FullRouteColors.RouteCoral
}

internal fun elevColor(elevM: Float, baseElevM: Float): Color {
    val gain = elevM - baseElevM
    return when {
        gain < 10f -> FullRouteColors.RouteBlue
        gain < 50f -> FullRouteColors.RouteAmber
        else -> FullRouteColors.RouteCoral
    }
}

/**
 * Splits a list of RidePoints into contiguous segments that share the same
 * colour for the given layer. Adjacent segments overlap by one point so the
 * polylines connect without gaps.
 */

internal fun buildColoredSegments(
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

internal fun remainingReplayCoordinates(
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

