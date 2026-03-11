package com.odys.mototriptracker.data.trip

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class TripEntity(

    @Id
    var id: Long = 0,

    var startTime: Long = 0,

    var endTime: Long = 0,

    var distanceMeters: Float = 0f,

    var movingTime: Long = 0,

    var stoppedTime: Long = 0,

    var maxSpeed: Float = 0f
)