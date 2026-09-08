package com.odys.mototriptracker.data.navigation

/**
 * Motorcycle-aware travel time from a car (traffic) ETA.
 *
 * Bikes can filter, so car delays are only partly applied. A personal filter
 * benefit is learned from completed navigations (actual vs car estimate).
 */
object MotoTravelEstimator {
    /** Assumed free-flow motorcycle pace for baseline (urban / peri-urban). */
    const val FREE_FLOW_SPEED_MPS: Double = 50.0 / 3.6
    const val DEFAULT_FILTER_BENEFIT: Double = 0.45

    data class Estimate(
        val carTravelTimeSeconds: Double,
        val baselineTravelTimeSeconds: Double,
        /** Excess of car ETA over free-flow baseline (traffic / lights / congestion). */
        val trafficDelaySeconds: Double,
        val motoTravelTimeSeconds: Double,
        val filterBenefit: Double,
    )

    fun estimate(
        distanceMeters: Double,
        carTravelTimeSeconds: Double,
        filterBenefit: Double = DEFAULT_FILTER_BENEFIT,
    ): Estimate {
        val baseline = maxOf(distanceMeters / FREE_FLOW_SPEED_MPS, 45.0)
        val delay = maxOf(0.0, carTravelTimeSeconds - baseline)
        val benefit = filterBenefit.coerceIn(0.15, 0.75)
        val moto = baseline + delay * (1.0 - benefit)
        return Estimate(
            carTravelTimeSeconds = carTravelTimeSeconds,
            baselineTravelTimeSeconds = baseline,
            trafficDelaySeconds = delay,
            motoTravelTimeSeconds = maxOf(moto, 30.0),
            filterBenefit = benefit,
        )
    }

    /** Returns the updated filter benefit after a completed navigation. */
    fun learnedBenefit(result: NavTimingResult, currentBenefit: Double): Double {
        if (result.carEstimateSeconds <= 60 || result.actualSeconds <= 30) return currentBenefit
        val estimate = estimate(
            distanceMeters = result.distanceMeters,
            carTravelTimeSeconds = result.carEstimateSeconds,
            filterBenefit = currentBenefit,
        )
        if (estimate.trafficDelaySeconds <= 30) return currentBenefit

        val observedBenefit =
            1.0 - ((result.actualSeconds - estimate.baselineTravelTimeSeconds) / estimate.trafficDelaySeconds)
        val clamped = observedBenefit.coerceIn(0.15, 0.75)
        return (0.72 * currentBenefit.coerceIn(0.15, 0.75) + 0.28 * clamped).coerceIn(0.15, 0.75)
    }

    fun formatMinutes(seconds: Double): String {
        val minutes = maxOf(1, kotlin.math.round(seconds / 60.0).toInt())
        return "$minutes min"
    }
}

/** Outcome of one guided navigation leg (Start → clear / arrive). */
data class NavTimingResult(
    val distanceMeters: Double,
    val carEstimateSeconds: Double,
    val motoEstimateSeconds: Double,
    val actualSeconds: Double,
) {
    /** Positive means finished faster than the car ETA. */
    val savedVersusCarSeconds: Double get() = carEstimateSeconds - actualSeconds

    val summaryLine: String
        get() {
            val actualMin = MotoTravelEstimator.formatMinutes(actualSeconds)
            return when {
                savedVersusCarSeconds >= 45 -> {
                    val saved = MotoTravelEstimator.formatMinutes(savedVersusCarSeconds)
                    "You did it in $actualMin — $saved faster than car traffic ETA"
                }
                savedVersusCarSeconds <= -45 -> {
                    val slower = MotoTravelEstimator.formatMinutes(-savedVersusCarSeconds)
                    "Took $actualMin — $slower slower than car traffic ETA"
                }
                else -> "Done in $actualMin — close to car traffic ETA"
            }
        }
}
