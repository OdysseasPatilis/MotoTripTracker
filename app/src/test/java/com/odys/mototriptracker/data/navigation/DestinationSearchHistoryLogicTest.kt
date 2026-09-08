package com.odys.mototriptracker.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationSearchHistoryLogicTest {

    @Test
    fun formatDistance_usesKmAbove1000() {
        assertEquals("1.2 km", NavigationState.formatDistance(1234.0))
        assertEquals("500 m", NavigationState.formatDistance(500.0))
    }

    @Test
    fun formatDuration_roundsUpToAtLeastOneMinute() {
        assertEquals("1 min", NavigationState.formatDuration(10.0))
        assertEquals("12 min", NavigationState.formatDuration(12 * 60.0))
    }

    @Test
    fun selectedPreviewRoute_fallsBackToFirst() {
        val a = NavRouteOption(
            id = "a",
            coordinates = emptyList(),
            distanceMeters = 1000.0,
            expectedTravelTimeSeconds = 100.0,
            motoTravelTimeSeconds = 80.0,
            trafficDelaySeconds = 20.0,
            steps = emptyList(),
        )
        val b = a.copy(id = "b")
        val state = NavigationState(
            previewRoutes = listOf(a, b),
            selectedRouteId = null,
            phase = NavigationPhase.Previewing,
        )
        assertEquals("a", state.selectedPreviewRoute?.id)
        assertTrue(state.isPreviewing)
    }
}
