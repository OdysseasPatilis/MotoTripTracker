package com.odys.mototriptracker.data.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.domain.TripManager
import com.odys.mototriptracker.ui.dashboard.RidePoint
import com.odys.mototriptracker.ui.dashboard.Waypoint
import com.odys.mototriptracker.ui.dashboard.WaypointType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripViewModel(
    private val tripManager: TripManager,
    private val tripRepository: TripRepository,
    private val serviceController: TripServiceController
) : ViewModel() {

    val tripStats = tripManager.tripStats

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val _tripHistory = MutableStateFlow<List<TripEntity>>(emptyList())
    val tripHistory = _tripHistory.asStateFlow()

    private val _ridePoints = MutableStateFlow<List<RidePoint>>(emptyList())
    val ridePoints = _ridePoints.asStateFlow()

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints = _waypoints.asStateFlow()
    fun startRide() {
        if (_isTracking.value) return

        _isTracking.value = true

        // Manager handles the DB start internally now
        tripManager.startTrip()
        serviceController.startService()
    }

    fun stopRide() {
        if (!_isTracking.value) return

        _isTracking.value = false

        // 1. Manager flushes time and tells Repo to finalize the DB
        tripManager.stopTrip()

        // 2. Stop the Foreground Service
        serviceController.stopService()

        // 3. Optional: Delete junk rides under 50 meters
        val finalStats = tripStats.value
        if (finalStats.distanceMeters < 50f) {
            // Note: You would need a method in your TripManager to expose currentTripId
            // if you want to delete it here, or handle it inside the Manager directly.
            println("Ride was less than 50 meters. Too short!")
        }
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = tripRepository.getTrips()
            _tripHistory.value = history.reversed()
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            tripRepository.deleteTrip(tripId)
            loadHistory()
        }
    }
    fun loadTripDataForMap(tripId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Fetch ALL points for the Map Line
            val rawPoints = tripRepository.getRoutePointsForMap(tripId)

            _ridePoints.value = rawPoints.map {
                RidePoint(
                    latLng = LatLng(it.latitude, it.longitude),
                    speedKmh = it.speedMps * 3.6f,
                    elevationM = it.altitude.toFloat()
                )
            }

            // 2. Fetch ONLY the Waypoints for the Timeline using your specific DB query!
            val rawWaypoints = tripRepository.getWaypointsForTrip(tripId)

            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Notice we don't need `.filter { it.isWaypoint }` anymore
            // because ObjectBox already did the filtering for us!
            _waypoints.value = rawWaypoints.map { entity ->

                // --- THE LEGACY BRIDGE ---
                val type = if (!entity.waypointType.isNullOrEmpty()) {
                    // 1. Modern Trips (The new analyzer)
                    when (entity.waypointType) {
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
                    // 2. Legacy Trips (From before we added waypointType)
                    when (entity.waypointTitle) {
                        "Departure" -> WaypointType.Start
                        "Arrival" -> WaypointType.End
                        else -> WaypointType.BriefStop // Default old stops to the yellow dot
                    }
                }

                // Safely handle titles and subtitles just in case they are null too
                val safeTitle = entity.waypointTitle ?: "Waypoint"
                val safeSubtitle = entity.waypointSubtitle ?: ""

                Waypoint(
                    label = safeTitle,
                    detail = safeSubtitle,
                    time = timeFormatter.format(Date(entity.timestamp)),
                    type = type,
                    position = LatLng(entity.latitude, entity.longitude)
                )
            }
        }
    }
    fun clearMapData() {
        _ridePoints.value = emptyList()
        _waypoints.value = emptyList()
    }
}