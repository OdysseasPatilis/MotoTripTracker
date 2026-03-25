package com.odys.mototriptracker.data.trip

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity_
import com.odys.mototriptracker.data.waypoint.AdvancedWaypointAnalyzer
import com.odys.mototriptracker.data.waypoint.WaypointAnalyzer
import com.odys.mototriptracker.domain.TripStats
import io.objectbox.BoxStore

class TripRepository(
    private val context: Context,
    boxStore: BoxStore
) {

    private val tripBox = boxStore.boxFor(TripEntity::class.java)
    private val routePointBox = boxStore.boxFor(RoutePointEntity::class.java)

    // 1. START: Create the blank trip immediately so it's safe on disk
    fun startNewTrip(startTimeMs: Long): Long {
        val newTrip = TripEntity(startTime = startTimeMs)
        return tripBox.put(newTrip) // Returns the generated ID
    }

    // 2. UPDATE: Save a new GPS ping and update the running totals
    fun addRoutePointAndUpdateStats(
        tripId: Long,
        lat: Double,
        lng: Double,
        alt: Double,
        speedMps: Float,
        timeMs: Long,
        runningStats: TripStats
    ) {
        val trip = tripBox.get(tripId) ?: return

        // A. Save the raw GPS point
        val point = RoutePointEntity(
            latitude = lat,
            longitude = lng,
            altitude = alt,
            speedMps = speedMps,
            timestamp = timeMs
        )
        point.trip.target = trip
        routePointBox.put(point)

        // B. Update the trip's running totals
        trip.distanceMeters = runningStats.distanceMeters
        trip.movingTime = runningStats.movingTime
        trip.stoppedTime = runningStats.stoppedTime
        trip.maxSpeed = runningStats.maxSpeed
        trip.maxGForce = runningStats.maxGForce
        trip.elevationGain = runningStats.totalElevationGain
        trip.avgSpeed = runningStats.avgSpeed

        tripBox.put(trip)
    }

    // 3. STOP: Finalize the stats and compress the Google Maps polyline
    fun saveTrip(tripId: Long, finalStats: TripStats) {
        val trip = tripBox.get(tripId) ?: return

        trip.endTime = System.currentTimeMillis()
        trip.movingTime = finalStats.movingTime
        trip.stoppedTime = finalStats.stoppedTime
        trip.avgSpeed = finalStats.avgSpeed

        // 1. Fetch all the raw GPS points
        val savedPoints = trip.routePoints
        // --- NEW: GENERATE WAYPOINTS ---
        val updatedWaypoints = AdvancedWaypointAnalyzer.analyzeAndMarkWaypoints(
             context = context, // Pass the context here!
            points = savedPoints,
            totalDistanceMeters = finalStats.distanceMeters
        )
        // Save only the modified points back to the database
        if (updatedWaypoints.isNotEmpty()) {
            routePointBox.put(updatedWaypoints)
        }

        val latLngList = savedPoints.map { LatLng(it.latitude, it.longitude) }
        if (latLngList.isNotEmpty()) {
            trip.encodedRoutePolyline = PolyUtil.encode(latLngList)
        }

        tripBox.put(trip)
        println("TripRepository: Ride saved! Polyline encoded. Distance: ${trip.distanceMeters}m")
    }

    fun getTrips(): List<TripEntity> {
        return tripBox.all
    }

    fun deleteTrip(id: Long) {
        tripBox.remove(id)
        println("TripRepository: Deleted trip with ID $id")
    }
    fun getWaypointsForTrip(tripId: Long): List<RoutePointEntity> {
        return routePointBox.query()
            .equal(RoutePointEntity_.tripId, tripId) // Link to the specific trip
            .equal(RoutePointEntity_.isWaypoint, true) // Only get the waypoints
            .build()
            .find()
    }
    // 4. FETCH: Get all GPS points for a specific ride to draw the map
    fun getRoutePointsForMap(tripId: Long): List<RoutePointEntity> {
        return routePointBox.query()
            .equal(RoutePointEntity_.tripId, tripId) // Match the trip
            .order(RoutePointEntity_.timestamp)      // Sort by time so the line draws correctly!
            .build()
            .find()
    }
}