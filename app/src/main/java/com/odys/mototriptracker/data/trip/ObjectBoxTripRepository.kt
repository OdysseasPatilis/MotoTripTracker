package com.odys.mototriptracker.data.trip

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity_
import com.odys.mototriptracker.data.waypoint.AdvancedWaypointAnalyzer
import com.odys.mototriptracker.domain.TripRepository
import com.odys.mototriptracker.domain.TripStats
import com.odys.mototriptracker.domain.TwistinessCalculator
import com.odys.mototriptracker.domain.model.RoutePoint
import com.odys.mototriptracker.domain.model.Trip
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectBoxTripRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    boxStore: BoxStore,
) : TripRepository {

    private val tripBox = boxStore.boxFor(TripEntity::class.java)
    private val routePointBox = boxStore.boxFor(RoutePointEntity::class.java)

    override fun startNewTrip(startTimeMs: Long): Long {
        val newTrip = TripEntity(startTime = startTimeMs)
        val id = tripBox.put(newTrip)
        AppLogger.i(AppLogger.Category.PERSISTENCE, "Created trip id=$id start=$startTimeMs")
        return id
    }

    override fun addRoutePointAndUpdateStats(
        tripId: Long,
        lat: Double,
        lng: Double,
        alt: Double,
        speedMps: Float,
        timeMs: Long,
        runningStats: TripStats,
    ) {
        val trip = tripBox.get(tripId)
        if (trip == null) {
            AppLogger.e(AppLogger.Category.PERSISTENCE, "addRoutePoint: trip id=$tripId not found")
            return
        }

        val point = RoutePointEntity(
            latitude = lat,
            longitude = lng,
            altitude = alt,
            speedMps = speedMps,
            timestamp = timeMs,
        )
        point.trip.target = trip
        routePointBox.put(point)

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

    override fun saveTrip(tripId: Long, finalStats: TripStats) {
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
            maxLateralGForce = finalStats.maxLateralGForce.toDouble(),
        ).toFloat()

        val savedEntities = routePointEntitiesForMap(tripId)
        AppLogger.d(
            AppLogger.Category.PERSISTENCE,
            "Finalizing trip id=$tripId points=${savedEntities.size} ${AppLogger.tripSummary(finalStats)}",
        )

        val latLngList = savedEntities.map { LatLng(it.latitude, it.longitude) }
        if (latLngList.isNotEmpty()) {
            trip.encodedRoutePolyline = PolyUtil.encode(latLngList)
        }
        tripBox.put(trip)
        AppLogger.i(
            AppLogger.Category.PERSISTENCE,
            "Trip stats+polyline saved id=$tripId dist=${trip.distanceMeters}m " +
                "polyline=${!trip.encodedRoutePolyline.isNullOrBlank()} verts=${latLngList.size}",
        )

        val updatedWaypoints = runBlocking(Dispatchers.IO) {
            try {
                AdvancedWaypointAnalyzer.analyzeAndMarkWaypoints(
                    context = context,
                    points = savedEntities,
                    totalDistanceMeters = finalStats.distanceMeters,
                )
            } catch (t: Throwable) {
                AppLogger.e(AppLogger.Category.WAYPOINT, "Waypoint analysis failed", t)
                emptyList()
            }
        }
        if (updatedWaypoints.isNotEmpty()) {
            routePointBox.put(updatedWaypoints)
            AppLogger.i(
                AppLogger.Category.WAYPOINT,
                "Marked ${updatedWaypoints.size} waypoints for trip id=$tripId",
            )
        }
    }

    override fun updateWaypointSubtitles(points: List<RoutePoint>) {
        if (points.isEmpty()) return
        val entities = points.mapNotNull { point ->
            val entity = routePointBox.get(point.id) ?: return@mapNotNull null
            entity.waypointSubtitle = point.waypointSubtitle
            entity
        }
        if (entities.isNotEmpty()) {
            routePointBox.put(entities)
        }
    }

    override fun getTrips(): List<Trip> {
        val trips = tripBox.all.sortedByDescending { it.startTime }.map { it.toDomain() }
        AppLogger.d(AppLogger.Category.PERSISTENCE, "Loaded ${trips.size} trips")
        return trips
    }

    override fun getTrip(id: Long): Trip? {
        val trip = tripBox.get(id)
        if (trip == null) {
            AppLogger.w(AppLogger.Category.PERSISTENCE, "getTrip: id=$id not found")
            return null
        }
        return trip.toDomain()
    }

    override fun updateTripTitle(id: Long, title: String) {
        val trip = tripBox.get(id) ?: return
        trip.title = title.trim()
        tripBox.put(trip)
        AppLogger.i(AppLogger.Category.PERSISTENCE, "Renamed trip id=$id to '${trip.title}'")
    }

    override fun toggleFavorite(id: Long): Boolean {
        val trip = tripBox.get(id) ?: return false
        trip.isFavorite = !trip.isFavorite
        tripBox.put(trip)
        AppLogger.i(AppLogger.Category.PERSISTENCE, "Favorite trip id=$id → ${trip.isFavorite}")
        return trip.isFavorite
    }

    override fun deleteTrip(id: Long) {
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

    override fun getWaypointsForTrip(tripId: Long): List<RoutePoint> =
        routePointBox.query()
            .equal(RoutePointEntity_.tripId, tripId)
            .equal(RoutePointEntity_.isWaypoint, true)
            .build()
            .find()
            .map { it.toDomain() }

    override fun getRoutePointsForMap(tripId: Long): List<RoutePoint> =
        routePointEntitiesForMap(tripId).map { it.toDomain() }

    private fun routePointEntitiesForMap(tripId: Long): List<RoutePointEntity> =
        routePointBox.query()
            .equal(RoutePointEntity_.tripId, tripId)
            .order(RoutePointEntity_.timestamp)
            .build()
            .find()
}
