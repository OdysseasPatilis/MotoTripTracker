package com.odys.mototriptracker.data.petrol

/** Where the rider is searching from — drives adaptive radius and highway bias. */
enum class PetrolSearchContext {
    URBAN, TOWN, RURAL, HIGHWAY;

    val displayLabel: String
        get() = when (this) {
            URBAN -> "City"
            TOWN -> "Town"
            RURAL -> "Rural"
            HIGHWAY -> "Highway"
        }
}

data class PetrolSearchPlan(
    val context: PetrolSearchContext,
    val fetchRadiusMeters: Int,
    val activeRadiusMeters: Int,
    val prioritizeHighway: Boolean
) {
    val summary: String
        get() {
            val km = activeRadiusMeters / 1000.0
            val radiusText = if (km >= 10) String.format("%.0f km", km) else String.format("%.1f km", km)
            return "${context.displayLabel} · within $radiusText"
        }
}

/** Chooses search radius and highway bias from local station density and ride speed. */
object PetrolSearchStrategy {
    private const val URBAN_TARGET = 3
    private const val TOWN_TARGET = 2
    private const val RURAL_TARGET = 1
    private const val HIGHWAY_SPEED_KMH = 75.0

    fun plan(
        speedKmh: Double,
        isNearMotorway: Boolean,
        stationCountsByRadius: Map<Int, Int>
    ): PetrolSearchPlan {
        val count2km = stationCountsByRadius[2_000] ?: 0
        val count10km = stationCountsByRadius[10_000] ?: 0
        val onHighway = speedKmh >= HIGHWAY_SPEED_KMH && isNearMotorway

        val context = when {
            onHighway -> PetrolSearchContext.HIGHWAY
            count2km >= 4 -> PetrolSearchContext.URBAN
            count10km >= 2 -> PetrolSearchContext.TOWN
            else -> PetrolSearchContext.RURAL
        }

        val tiers = when (context) {
            PetrolSearchContext.URBAN -> listOf(2_000, 5_000, 10_000)
            PetrolSearchContext.TOWN -> listOf(5_000, 10_000, 20_000)
            PetrolSearchContext.RURAL -> listOf(20_000, 50_000)
            PetrolSearchContext.HIGHWAY -> listOf(10_000, 20_000, 50_000)
        }

        val target = when (context) {
            PetrolSearchContext.URBAN -> URBAN_TARGET
            PetrolSearchContext.TOWN -> TOWN_TARGET
            PetrolSearchContext.RURAL, PetrolSearchContext.HIGHWAY -> RURAL_TARGET
        }

        val active = pickActiveRadius(tiers, stationCountsByRadius, target)
        return PetrolSearchPlan(
            context = context,
            fetchRadiusMeters = tiers.last(),
            activeRadiusMeters = active,
            prioritizeHighway = context == PetrolSearchContext.HIGHWAY || (onHighway && speedKmh >= 60)
        )
    }

    fun countStations(radii: List<Int>, distances: List<Double>): Map<Int, Int> =
        radii.associateWith { radius -> distances.count { it <= radius } }

    private fun pickActiveRadius(tiers: List<Int>, counts: Map<Int, Int>, target: Int): Int {
        for (radius in tiers) {
            if ((counts[radius] ?: 0) >= target) return radius
        }
        return tiers.lastOrNull() ?: 50_000
    }
}
