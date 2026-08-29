package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwistinessCalculatorTest {
    @Test
    fun scoreCombinesCornersAndLateralG() {
        val straight = TwistinessCalculator.score(2, 20.0, 0.2)
        val twisty = TwistinessCalculator.score(40, 20.0, 0.7)
        assertTrue(straight < twisty)
        assertTrue(twisty >= 25)
    }

    @Test
    fun shortRidesScoreZero() {
        assertEquals(0.0, TwistinessCalculator.score(5, 0.2, 0.5), 0.001)
    }
}

class RouteReplayEngineTest {
    @Test
    fun interpolatesBetweenPoints() {
        val points = listOf(
            RoutePointEntity(latitude = 0.0, longitude = 0.0, timestamp = 0L, speedMps = 0f),
            RoutePointEntity(latitude = 0.0, longitude = 0.001, timestamp = 10_000L, speedMps = 10f)
        )
        val engine = RouteReplayEngine(points)
        val mid = engine.frame(5.0)!!
        assertEquals(0.0005, mid.longitude, 0.0002)
    }
}
