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
            TrafficCameraLogic.kindFromOsmTags(mapOf("device" to "speed_camera")),
        )
        assertEquals(
            TrafficCameraKind.Speed,
            TrafficCameraLogic.kindFromOsmTags(mapOf("enforcement" to "maxspeed")),
        )
        assertEquals(
            TrafficCameraKind.Speed,
            TrafficCameraLogic.kindFromOsmTags(mapOf("enforcement" to "speed")),
        )
        assertEquals(
            TrafficCameraKind.RedLight,
            TrafficCameraLogic.kindFromOsmTags(mapOf("enforcement" to "traffic_signals")),
        )
        assertEquals(
            TrafficCameraKind.RedLight,
            TrafficCameraLogic.kindFromOsmTags(mapOf("camera:type" to "red_light")),
        )
        assertEquals(
            TrafficCameraKind.Speed,
            TrafficCameraLogic.kindFromOsmTags(mapOf("camera:type" to "speed")),
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
                  "id": 10,
                  "lat": 37.97,
                  "lon": 23.72,
                  "tags": { "highway": "speed_camera" }
                },
                {
                  "type": "way",
                  "id": 20,
                  "center": { "lat": 37.98, "lon": 23.73 },
                  "tags": { "enforcement": "traffic_signals" }
                },
                {
                  "type": "node",
                  "id": 25,
                  "lat": 37.975,
                  "lon": 23.725,
                  "tags": { "device": "speed_camera" }
                },
                {
                  "type": "node",
                  "id": 26,
                  "lat": 37.976,
                  "lon": 23.726,
                  "tags": { "camera:type": "red_light" }
                },
                {
                  "type": "node",
                  "id": 30,
                  "lat": 37.99,
                  "lon": 23.74,
                  "tags": { "highway": "bus_stop" }
                }
              ]
            }
        """.trimIndent()
        val cameras = TrafficCameraService.parseOverpassCameras(body)
        assertEquals(4, cameras.size)
        assertEquals("osm:node/10", cameras[0].id)
        assertEquals(TrafficCameraKind.Speed, cameras[0].kind)
        assertEquals("osm:way/20", cameras[1].id)
        assertEquals(TrafficCameraKind.RedLight, cameras[1].kind)
        assertEquals(TrafficCameraKind.Speed, cameras[2].kind)
        assertEquals(TrafficCameraKind.RedLight, cameras[3].kind)
    }

    @Test
    fun parseSpeedcamsCsv() {
        val csv = """
            # comment
            id,latitude,longitude,type,maxspeed,unit,country_code,region
            123,37.97,23.72,fixed,50,kmh,GR,
            456,38.0,23.8,fixed,,,GR,
        """.trimIndent()
        val pack = TrafficCameraPackDownloader.parseCsv(csv, "GR")
        assertEquals(2, pack.cameras.size)
        assertEquals("osm:node/123", pack.cameras[0].id)
        assertEquals(TrafficCameraKind.Speed, pack.cameras[0].kind)
        assertEquals(37.97, pack.cameras[0].latitude, 0.0001)
        assertEquals("country_GR", pack.id)
        assertEquals(
            "https://speedcams.world/downloads/it/it-all.csv",
            TrafficCameraPackDownloader.csvUrl("IT"),
        )
    }

    @Test
    fun parseSpeedcamsCsv_skipsBadRows() {
        val csv = """
            id,latitude,longitude,type,maxspeed,unit,country_code,region
            bad,x,y,fixed,,,GR,
            789,40.5,22.9,fixed,,,GR,
        """.trimIndent()
        val pack = TrafficCameraPackDownloader.parseCsv(csv, "GR")
        assertEquals(1, pack.cameras.size)
        assertEquals("osm:node/789", pack.cameras[0].id)
    }

    @Test
    fun packEncodeDecode_roundTrip() {
        val pack = TrafficCameraPackDownloader.parseCsv(
            """
            id,latitude,longitude,type,maxspeed,unit,country_code,region
            1,1,1,fixed,,,IT,
            """.trimIndent(),
            "IT",
        )
        val decoded = TrafficCameraRegionPackStore.decode(TrafficCameraRegionPackStore.encode(pack))
        assertEquals(1, decoded.cameras.size)
        assertEquals("country_IT", decoded.id)
        assertTrue(TrafficCameraPackStore.DEFAULT_TTL_MS > 0)
        assertTrue(TrafficCameraPackStore.DEFAULT_UNSUPPORTED_COOLDOWN_MS > 0)
    }

    @Test
    fun countryResolver_refreshGate() {
        val t0 = 0L
        assertFalse(
            TrafficCameraCountryResolver.shouldRefresh(
                lastLat = 37.97,
                lastLng = 23.72,
                lastResolvedAtMs = t0,
                newLat = 37.971,
                newLng = 23.721,
                nowMs = t0 + 60_000,
            )
        )
        assertTrue(
            TrafficCameraCountryResolver.shouldRefresh(
                lastLat = 37.97,
                lastLng = 23.72,
                lastResolvedAtMs = t0,
                newLat = 38.2,
                newLng = 23.9,
                nowMs = t0 + 60_000,
            )
        )
        assertTrue(
            TrafficCameraCountryResolver.shouldRefresh(
                lastLat = 37.97,
                lastLng = 23.72,
                lastResolvedAtMs = t0,
                newLat = 37.971,
                newLng = 23.721,
                nowMs = t0 + 601_000,
            )
        )
    }

    @Test
    fun visibleRegion_filtersAndLimits() {
        val cameras = listOf(
            TrafficCamera(id = "a", latitude = 37.97, longitude = 23.73, kind = TrafficCameraKind.Speed),
            TrafficCamera(id = "b", latitude = 37.98, longitude = 23.74, kind = TrafficCameraKind.RedLight),
            TrafficCamera(id = "c", latitude = 40.0, longitude = 23.0, kind = TrafficCameraKind.Speed),
        )
        val region = VisibleMapRegion(
            centerLatitude = 37.975,
            centerLongitude = 23.735,
            latitudeDelta = 0.05,
            longitudeDelta = 0.05,
        )
        val visible = TrafficCameraLogic.cameras(from = cameras, region = region, limit = 10)
        assertEquals(setOf("a", "b"), visible.map { it.id }.toSet())

        val limited = TrafficCameraLogic.cameras(from = cameras, region = region, limit = 1)
        assertEquals(1, limited.size)
        assertTrue(limited[0].id == "a" || limited[0].id == "b")
    }
}
