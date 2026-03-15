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
            maxSpeed = stats.maxSpeed,
            maxGForce = stats.maxGForce,
            elevationGain = stats.totalElevationGain,
            avgSpeed = stats.avgSpeed // The avgSpeed we calculated in TripManager
        )

        tripBox.put(entity)
        println("TripRepository: Ride saved successfully! Distance: ${entity.distanceMeters}m")
    }

    fun getTrips(): List<TripEntity> {
        return tripBox.all
    }

    fun deleteTrip(id: Long) {
        tripBox.remove(id)
        println("TripRepository: Deleted trip with ID $id")
    }
}