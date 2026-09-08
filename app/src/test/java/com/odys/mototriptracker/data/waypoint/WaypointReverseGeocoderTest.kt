package com.odys.mototriptracker.data.waypoint

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaypointReverseGeocoderTest {

    @Test
    fun looksLikeCoordinates_detectsDegreeFallback() {
        assertTrue(WaypointReverseGeocoder.looksLikeCoordinates("37.9838° N, 23.7275° E"))
        assertFalse(WaypointReverseGeocoder.looksLikeCoordinates("Leoforos Vasilissis Sofias"))
        assertFalse(WaypointReverseGeocoder.looksLikeCoordinates(""))
    }
}
