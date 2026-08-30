package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.data.export.displayTitle
import com.odys.mototriptracker.domain.TwistinessCalculator
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.data.trip.TripRepository
import java.util.Locale
import javax.inject.Inject

enum class LeaderboardCategory {
    SPEED,
    DISTANCE,
    TURNS,
    TWISTINESS
}

data class LeaderboardEntry(
    val rank: Int,
    val tripId: Long,
    val title: String,
    val startTimeMs: Long,
    val valueLabel: String,
    val rawValue: Float
)

class GetLeaderboardUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(category: LeaderboardCategory): List<LeaderboardEntry> {
        val trips = tripRepository.getTrips()
        val ranked = when (category) {
            LeaderboardCategory.SPEED -> trips
                .filter { it.maxSpeed > 0f }
                .sortedWith(
                    compareByDescending<TripEntity> { it.maxSpeed }
                        .thenByDescending { it.startTime }
                )
                .mapIndexed { index, trip ->
                    trip.toEntry(
                        rank = index + 1,
                        valueLabel = "${trip.maxSpeed.toInt()} km/h",
                        rawValue = trip.maxSpeed
                    )
                }

            LeaderboardCategory.DISTANCE -> trips
                .filter { it.distanceMeters > 0f }
                .sortedWith(
                    compareByDescending<TripEntity> { it.distanceMeters }
                        .thenByDescending { it.startTime }
                )
                .mapIndexed { index, trip ->
                    val km = trip.distanceMeters / 1000f
                    trip.toEntry(
                        rank = index + 1,
                        valueLabel = String.format(Locale.US, "%.1f km", km),
                        rawValue = km
                    )
                }

            LeaderboardCategory.TURNS -> trips
                .filter { it.cornerCount > 0 }
                .sortedWith(
                    compareByDescending<TripEntity> { it.cornerCount }
                        .thenByDescending { it.startTime }
                )
                .mapIndexed { index, trip ->
                    val label = if (trip.cornerCount == 1) "1 turn" else "${trip.cornerCount} turns"
                    trip.toEntry(
                        rank = index + 1,
                        valueLabel = label,
                        rawValue = trip.cornerCount.toFloat()
                    )
                }

            LeaderboardCategory.TWISTINESS -> trips
                .map { trip ->
                    trip to TwistinessCalculator.score(trip)
                }
                .filter { (_, score) -> score > 0 }
                .sortedWith(
                    compareByDescending<Pair<TripEntity, Double>> { it.second }
                        .thenByDescending { it.first.startTime }
                )
                .mapIndexed { index, (trip, score) ->
                    trip.toEntry(
                        rank = index + 1,
                        valueLabel = "${TwistinessCalculator.formattedScore(score)} · ${TwistinessCalculator.rating(score).label}",
                        rawValue = score.toFloat()
                    )
                }
        }
        return ranked
    }

    private fun TripEntity.toEntry(
        rank: Int,
        valueLabel: String,
        rawValue: Float
    ): LeaderboardEntry = LeaderboardEntry(
        rank = rank,
        tripId = id,
        title = displayTitle(),
        startTimeMs = startTime,
        valueLabel = valueLabel,
        rawValue = rawValue
    )
}
