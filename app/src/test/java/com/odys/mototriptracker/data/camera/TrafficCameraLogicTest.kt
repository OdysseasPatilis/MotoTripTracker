package com.odys.mototriptracker.data.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficCameraLogicTest {

    @Test
    fun warnDistance_clampsLowAndHighSpeed() {
        assertEquals(250.0, TrafficCameraLogic.warnDistanceMeters(-1f), 0.01)
        assertEquals(250.0, TrafficCameraLogic.warnDistanceMeters(10f), 0.01) // 80 m raw → floor
        assertEquals(400.0, TrafficCameraLogic.warnDistanceMeters(50f), 0.01) // 400 m
        assertEquals(700.0, TrafficCameraLogic.warnDistanceMeters(120f), 0.01) // 960 → cap
    }

    @Test
    fun isAhead_treatsSlowSpeedAsAhead() {
        assertTrue(
            TrafficCameraLogic.isAhead(
                riderHeadingDegrees = 0f,
                bearingToCameraDegrees = 180.0,
                speedMps = 1f,
            )
        )
    }

    @Test
    fun isAhead_rejectsCameraClearlyBehind() {
        assertFalse(
            TrafficCameraLogic.isAhead(
                riderHeadingDegrees = 0f,
                bearingToCameraDegrees = 180.0,
                speedMps = 15f,
            )
        )
        assertTrue(
            TrafficCameraLogic.isAhead(
                riderHeadingDegrees = 0f,
                bearingToCameraDegrees = 20.0,
                speedMps = 15f,
            )
        )
    }

    @Test
    fun kindFromOsmTags_mapsSpeedAndRedLight() {
        assertEquals(
            TrafficCameraKind.Speed,
            TrafficCameraLogic.kindFromOsmTags(mapOf("highway" to "speed_camera")),
        )
        assertEquals(
            TrafficCameraKind.Speed,
            TrafficCameraLogic.kindFromOsmTags(mapOf("enforcement" to "maxspeed")),
        )
        assertEquals(
            TrafficCameraKind.RedLight,
            TrafficCameraLogic.kindFromOsmTags(mapOf("enforcement" to "traffic_signals")),
        )
        assertNull(TrafficCameraLogic.kindFromOsmTags(mapOf("highway" to "stop")))
    }

    @Test
    fun parseOverpassCameras_readsNodeAndWayCenter() {
        val body = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 37.97,
                  "lon": 23.72,
                  "tags": { "highway": "speed_camera" }
                },
                {
                  "type": "way",
                  "id": 2,
                  "center": { "lat": 37.98, "lon": 23.73 },
                  "tags": { "enforcement": "traffic_signals" }
                }
              ]
            }
        """.trimIndent()
        val cameras = TrafficCameraService.parseOverpassCameras(body)
        assertEquals(2, cameras.size)
        assertEquals("osm:node/1", cameras[0].id)
        assertEquals(TrafficCameraKind.Speed, cameras[0].kind)
        assertEquals("osm:way/2", cameras[1].id)
        assertEquals(TrafficCameraKind.RedLight, cameras[1].kind)
    }
}
