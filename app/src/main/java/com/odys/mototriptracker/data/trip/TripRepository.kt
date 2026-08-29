package com.odys.mototriptracker.data.trip

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity_
import com.odys.mototriptracker.data.waypoint.AdvancedWaypointAnalyzer
import com.odys.mototriptracker.domain.TripStats
import com.odys.mototriptracker.domain.TwistinessCalculator
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.BoxStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    boxStore: BoxStore
) {

    private val tripBox = boxStore.boxFor(TripEntity::class.java)
    private val routePointBox = boxStore.boxFor(RoutePointEntity::class.java)

    // 1. START: Create the blank trip immediately so it's safe on disk
    fun startNewTrip(startTimeMs: Long): Long {
        val newTrip = TripEntity(startTime = startTimeMs)
        val id = tripBox.put(newTrip)
        AppLogger.i(AppLogger.Category.PERSISTENCE, "Created trip id=$id start=$startTimeMs")
        return id
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
        val trip = tripBox.get(tripId)
        if (trip == null) {
            AppLogger.e(AppLogger.Category.PERSISTENCE, "addRoutePoint: trip id=$tripId not found")
            return
        }

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
        trip.maxLateralGForce = runningStats.maxLateralGForce
        trip.cornerCount = runningStats.cornerCount

        tripBox.put(trip)
    }

    // 3. STOP: Finalize the stats and compress the Google Maps polyline
    fun saveTrip(tripId: Long, finalStats: TripStats) {
        val trip = tripBox.get(tripId)
        if (trip == null) {
            AppLogger.e(AppLogger.Category.PERSISTENCE, "saveTrip: trip id=$tripId not found")
            return
        }

        trip.endTime = System.currentTimeMillis()
        trip.movingTime = finalStats.movingTime
        trip.stoppedTime = finalStats.stoppedTime
        trip.avgSpeed = finalStats.avgSpeed
        trip.maxSpeed = finalStats.maxSpeed
        trip.maxGForce = finalStats.maxGForce
        trip.elevationGain = finalStats.totalElevationGain
        trip.distanceMeters = finalStats.distanceMeters
        trip.maxLateralGForce = finalStats.maxLateralGForce
        trip.cornerCount = finalStats.cornerCount
        trip.twistinessScore = TwistinessCalculator.score(
            cornerCount = finalStats.cornerCount,
            distanceKm = finalStats.distanceMeters / 1000.0,
            maxLateralGForce = finalStats.maxLateralGForce.toDouble()
        ).toFloat()

        // 1. Fetch all the raw GPS points
        val savedPoints = trip.routePoints
        AppLogger.d(
            AppLogger.Category.PERSISTENCE,
            "Finalizing trip id=$tripId points=${savedPoints.size} ${AppLogger.tripSummary(finalStats)}"
        )
        // --- NEW: GENERATE WAYPOINTS ---
        val updatedWaypoints = try {
            AdvancedWaypointAnalyzer.analyzeAndMarkWaypoints(
                context = context,
                points = savedPoints,
                totalDistanceMeters = finalStats.distanceMeters
            )
        } catch (t: Throwable) {
            AppLogger.e(AppLogger.Category.WAYPOINT, "Waypoint analysis failed", t)
            emptyList()
        }
        // Save only the modified points back to the database
        if (updatedWaypoints.isNotEmpty()) {
            routePointBox.put(updatedWaypoints)
            AppLogger.i(
                AppLogger.Category.WAYPOINT,
                "Marked ${updatedWaypoints.size} waypoints for trip id=$tripId"
            )
        }

        val latLngList = savedPoints.map { LatLng(it.latitude, it.longitude) }
        if (latLngList.isNotEmpty()) {
            trip.encodedRoutePolyline = PolyUtil.encode(latLngList)
        }

        tripBox.put(trip)
        AppLogger.i(
            AppLogger.Category.PERSISTENCE,
            "Ride saved id=$tripId dist=${trip.distanceMeters}m polyline=${!trip.encodedRoutePolyline.isNullOrBlank()}"
        )
    }

    fun getTrips(): List<TripEntity> {
        // Newest first — favorites live on their own History tab.
        val trips = tripBox.all.sortedByDescending { it.startTime }
        AppLogger.d(AppLogger.Category.PERSISTENCE, "Loaded ${trips.size} trips")
        return trips
    }

    fun getTrip(id: Long): TripEntity? {
        val trip = tripBox.get(id)
        if (trip == null) {
            AppLogger.w(AppLogger.Category.PERSISTENCE, "getTrip: id=$id not found")
        }
        return trip
    }

    fun updateTripTitle(id: Long, title: String) {
        val trip = tripBox.get(id) ?: return
        trip.title = title.trim()
        tripBox.put(trip)
        AppLogger.i(AppLogger.Category.PERSISTENCE, "Renamed trip id=$id to '${trip.title}'")
    }

    fun toggleFavorite(id: Long): Boolean {
        val trip = tripBox.get(id) ?: return false
        trip.isFavorite = !trip.isFavorite
        tripBox.put(trip)
        AppLogger.i(AppLogger.Category.PERSISTENCE, "Favorite trip id=$id → ${trip.isFavorite}")
        return trip.isFavorite
    }

    fun deleteTrip(id: Long) {
        val points = routePointBox.query()
            .equal(RoutePointEntity_.tripId, id)
            .build()
            .find()
        if (points.isNotEmpty()) {
            routePointBox.remove(points)
        }
        tripBox.remove(id)
        AppLogger.i(AppLogger.Category.PERSISTENCE, "Deleted trip id=$id")
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