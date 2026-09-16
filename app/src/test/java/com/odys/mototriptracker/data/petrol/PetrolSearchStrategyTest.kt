package com.odys.mototriptracker.data.petrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetrolSearchStrategyTest {

    @Test
    fun countStations_talliesWithinEachRadius() {
        val counts = PetrolSearchStrategy.countStations(
            radii = listOf(2_000, 10_000),
            distances = listOf(500.0, 3_000.0, 12_000.0),
        )
        assertEquals(1, counts[2_000])
        assertEquals(2, counts[10_000])
    }

    @Test
    fun plan_urbanWhenDenseNearOrigin() {
        val plan = PetrolSearchStrategy.plan(
            speedKmh = 30.0,
            isNearMotorway = false,
            stationCountsByRadius = mapOf(2_000 to 5, 10_000 to 8),
        )
        assertEquals(PetrolSearchContext.URBAN, plan.context)
        assertEquals(10_000, plan.fetchRadiusMeters)
        assertEquals(2_000, plan.activeRadiusMeters)
        assertFalse(plan.prioritizeHighway)
    }

    @Test
    fun plan_highwayWhenFastNearMotorway() {
        val plan = PetrolSearchStrategy.plan(
            speedKmh = 90.0,
            isNearMotorway = true,
            stationCountsByRadius = mapOf(2_000 to 0, 10_000 to 1, 20_000 to 3),
        )
        assertEquals(PetrolSearchContext.HIGHWAY, plan.context)
        assertTrue(plan.prioritizeHighway)
        assertEquals(50_000, plan.fetchRadiusMeters)
    }

    @Test
    fun plan_ruralWhenSparse() {
        val plan = PetrolSearchStrategy.plan(
            speedKmh = 40.0,
            isNearMotorway = false,
            stationCountsByRadius = mapOf(2_000 to 0, 10_000 to 0, 20_000 to 1),
        )
        assertEquals(PetrolSearchContext.RURAL, plan.context)
        assertEquals(50_000, plan.fetchRadiusMeters)
    }
}
