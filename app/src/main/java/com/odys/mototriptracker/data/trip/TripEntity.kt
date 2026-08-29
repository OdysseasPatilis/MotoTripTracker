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
    var elevationGain: Float = 0f,
    var avgSpeed: Float = 0f,
    var encodedRoutePolyline: String? = "",
    /** Nullable for ObjectBox migration — older trips have null until renamed. */
    var title: String? = null,
    var isFavorite: Boolean = false,
    var maxLateralGForce: Float = 0f,
    var cornerCount: Int = 0,
    var twistinessScore: Float = 0f
) {
    @Backlink(to = "trip")
    lateinit var routePoints: ToMany<RoutePointEntity>
}
