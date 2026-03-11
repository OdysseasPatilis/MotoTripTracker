package com.odys.mototriptracker.data.trip

import com.odys.mototriptracker.domain.TripStats
import io.objectbox.BoxStore

class TripRepository(
    boxStore: BoxStore
) {

    private val tripBox = boxStore.boxFor(TripEntity::class.java)

    fun saveTrip(stats: TripStats) {

        val entity = TripEntity(
            startTime = stats.tripStartTime,
            endTime = System.currentTimeMillis(),
            distanceMeters = stats.distanceMeters,
            movingTime = stats.movingTime,
            stoppedTime = stats.stoppedTime,
            maxSpeed = stats.maxSpeed
        )

        tripBox.put(entity)
    }

    fun getTrips(): List<TripEntity> {
        return tripBox.all
    }
}