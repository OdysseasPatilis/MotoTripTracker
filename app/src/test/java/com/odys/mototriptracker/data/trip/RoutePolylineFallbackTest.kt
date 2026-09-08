package com.odys.mototriptracker.data.trip

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePolylineFallbackTest {

    @Test
    fun reconstructFromLatLngs_requiresAtLeastTwoPoints() {
        assertTrue(
            RoutePolylineFallback.reconstructFromLatLngs(
                listOf(LatLng(37.98, 23.72)),
                startTimeMs = 1_000L,
                endTimeMs = 2_000L,
            ).isEmpty()
        )
    }

    @Test
    fun reconstructFromLatLngs_interpolatesTimestamps() {
        val points = RoutePolylineFallback.reconstructFromLatLngs(
            decoded = listOf(
                LatLng(37.98, 23.72),
                LatLng(37.99, 23.73),
                LatLng(38.00, 23.74),
            ),
            startTimeMs = 1_000L,
            endTimeMs = 3_000L,
        )
        assertEquals(3, points.size)
        assertEquals(1_000L, points[0].timestamp)
        assertEquals(2_000L, points[1].timestamp)
        assertEquals(3_000L, points[2].timestamp)
        assertEquals(37.98, points[0].latitude, 1e-6)
        assertEquals(38.00, points[2].latitude, 1e-6)
    }

    @Test
    fun reconstructFromLatLngs_synthesizesEndWhenMissing() {
        val points = RoutePolylineFallback.reconstructFromLatLngs(
            decoded = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)),
            startTimeMs = 5_000L,
            endTimeMs = 0L,
        )
        assertEquals(2, points.size)
        assertEquals(5_000L, points[0].timestamp)
        assertEquals(6_000L, points[1].timestamp)
    }
}
