package com.odys.mototriptracker.data.trip

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany

@Entity
data class TripEntity(
    @Id
    var id: Long = 0,
    var startTime: Long = 0,
    var endTime: Long = 0,
    var distanceMeters: Float = 0f,
    var movingTime: Long = 0,
    var stoppedTime: Long = 0,
    var maxSpeed: Float = 0f,
    var maxGForce: Float = 0f,
    var elevationGain: Float = 0f, // Total meters climbed
    var avgSpeed: Float = 0f,       // Calculated as distance movingTime

    // NEW: The compressed Google Maps string for the "Ride Summary" screen
    var encodedRoutePolyline: String? = ""
){
    // NEW: This holds the thousands of GPS points for the "Full Route" screen.
    // The @Backlink annotation makes ObjectBox highly efficient at querying this.
    @Backlink(to = "trip")
    lateinit var routePoints: ToMany<RoutePointEntity>
}