package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.trip.TripEntity
import kotlin.math.min
import kotlin.math.roundToInt

/** Composite twistiness score (0–100) from corner density and lateral G. */
object TwistinessCalculator {
    enum class Rating(val label: String) {
        STRAIGHT("Straight"),
        FLOWING("Flowing"),
        TWISTY("Twisty"),
        EPIC("Epic twisties")
    }

    fun score(cornerCount: Int, distanceKm: Double, maxLateralGForce: Double): Double {
        if (distanceKm < 0.5 || cornerCount <= 0) return 0.0

        val cornersPer10Km = cornerCount / distanceKm * 10.0
        val densityScore = min(100.0, cornersPer10Km / 15.0 * 100.0)
        val lateralScore = min(100.0, maxOf(maxLateralGForce, 0.0) / 0.8 * 100.0)
        return min(100.0, densityScore * 0.72 + lateralScore * 0.28)
    }

    fun score(trip: TripEntity): Double {
        if (trip.twistinessScore > 0f) return trip.twistinessScore.toDouble()
        return score(
            cornerCount = trip.cornerCount,
            distanceKm = trip.distanceMeters / 1000.0,
            maxLateralGForce = trip.maxLateralGForce.toDouble()
        )
    }

    fun rating(score: Double): Rating = when {
        score < 25 -> Rating.STRAIGHT
        score < 50 -> Rating.FLOWING
        score < 75 -> Rating.TWISTY
        else -> Rating.EPIC
    }

    fun formattedScore(score: Double): String = score.roundToInt().toString()

    fun cornersPer10Km(cornerCount: Int, distanceKm: Double): Double {
        if (distanceKm < 0.5) return 0.0
        return cornerCount / distanceKm * 10.0
    }
}
