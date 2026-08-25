package com.odys.mototriptracker.ui.mapper

import com.google.android.gms.maps.model.LatLng
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.ui.dashboard.RidePoint
import com.odys.mototriptracker.ui.dashboard.Waypoint
import com.odys.mototriptracker.ui.dashboard.WaypointType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun RoutePointEntity.toRidePoint(): RidePoint = RidePoint(
    latLng = LatLng(latitude, longitude),
    speedKmh = speedMps * 3.6f,
    elevationM = altitude.toFloat()
)

fun RoutePointEntity.toWaypoint(timeFormatter: SimpleDateFormat = defaultTimeFormatter()): Waypoint {
    val type = if (!waypointType.isNullOrEmpty()) {
        when (waypointType) {
            "START" -> WaypointType.Start
            "END" -> WaypointType.End
            "STOP_SIGN" -> WaypointType.StopSign
            "TRAFFIC_LIGHT" -> WaypointType.TrafficLight
            "BRIEF_STOP" -> WaypointType.BriefStop
            "REST_STOP" -> WaypointType.RestStop
            "TOP_SPEED" -> WaypointType.TopSpeed
            "SUMMIT" -> WaypointType.Summit
            else -> WaypointType.Unknown
        }
    } else {
        when (waypointTitle) {
            "Departure" -> WaypointType.Start
            "Arrival" -> WaypointType.End
            else -> WaypointType.BriefStop
        }
    }

    return Waypoint(
        label = waypointTitle.ifBlank { "Waypoint" },
        detail = waypointSubtitle,
        time = timeFormatter.format(Date(timestamp)),
        type = type,
        position = LatLng(latitude, longitude)
    )
}

private fun defaultTimeFormatter(): SimpleDateFormat =
    SimpleDateFormat("HH:mm", Locale.getDefault())
