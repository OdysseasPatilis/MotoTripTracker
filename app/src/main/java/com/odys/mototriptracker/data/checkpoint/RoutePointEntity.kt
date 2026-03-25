package com.odys.mototriptracker.data.checkpoint

import com.odys.mototriptracker.data.trip.TripEntity
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity
data class RoutePointEntity(
    @Id var id: Long = 0,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var altitude: Double = 0.0, // For the blue elevation chart
    var speedMps: Float = 0f,   // For drawing the Slow/Cruise/Fast colored segments
    var timestamp: Long = 0,

    // NEW: To populate that "Route waypoints" list in your UI
    var waypointType: String? = null, // "START", "STOP_SIGN", "TRAFFIC_LIGHT", "REST_STOP", "TOP_SPEED", "SUMMIT", "END"
    var isWaypoint: Boolean = false,
    var waypointTitle: String = "", // e.g., "Brief stop" or "Departure"
    var waypointSubtitle: String = "" // e.g., "Traffic light - 2.0km - 01:02 pause"
) {
    // Links this point to the parent trip
    lateinit var trip: ToOne<TripEntity>
}