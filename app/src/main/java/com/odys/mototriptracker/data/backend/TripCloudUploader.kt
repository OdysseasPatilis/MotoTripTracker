package com.odys.mototriptracker.data.backend

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.data.trip.TripRepository
import com.odys.mototriptracker.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripCloudUploader @Inject constructor(
    private val tripRepository: TripRepository,
    private val userIdStore: BackendUserIdStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun enqueueUpload(localTripId: Long) {
        if (!BackendConfig.isEnabled) {
            AppLogger.d(AppLogger.Category.APP, "Cloud upload skipped — BACKEND_BASE_URL not set")
            return
        }
        scope.launch {
            runUpload(localTripId)
        }
    }

    /** Blocking upload for manual retry from the summary screen. */
    suspend fun uploadNow(localTripId: Long) {
        if (!BackendConfig.isEnabled) {
            error("Backend URL not configured")
        }
        upload(localTripId)
    }

    private suspend fun runUpload(localTripId: Long) {
        runCatching { upload(localTripId) }
            .onSuccess {
                AppLogger.i(AppLogger.Category.APP, "Cloud upload ok trip id=$localTripId")
            }
            .onFailure { error ->
                AppLogger.e(AppLogger.Category.APP, "Cloud upload failed trip id=$localTripId", error)
            }
    }

    private suspend fun upload(localTripId: Long) = withContext(Dispatchers.IO) {
        val trip = tripRepository.getTrip(localTripId)
            ?: error("Trip $localTripId not found")
        val points = tripRepository.getRoutePointsForMap(localTripId)
        val body = buildPayload(trip, points).toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${BackendConfig.baseUrl}/v1/trips/upload")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }
        }
    }

    private fun buildPayload(trip: TripEntity, points: List<RoutePointEntity>): JSONObject {
        val routePoints = JSONArray()
        points.forEach { point ->
            routePoints.put(
                JSONObject()
                    .put("id", point.id.toCloudPointId())
                    .put("latitude", point.latitude)
                    .put("longitude", point.longitude)
                    .put("altitude", point.altitude)
                    .put("speedMps", point.speedMps.toDouble())
                    .put("timestampMs", point.timestamp)
                    .put("isWaypoint", point.isWaypoint)
                    .put("waypointType", point.waypointType)
                    .put("waypointTitle", point.waypointTitle)
                    .put("waypointSubtitle", point.waypointSubtitle),
            )
        }

        return JSONObject()
            .put("clientTripId", trip.id.toCloudTripId())
            .put("userId", userIdStore.getOrCreate())
            .put("startTimeMs", trip.startTime)
            .put("endTimeMs", trip.endTime)
            .put("distanceMeters", trip.distanceMeters.toDouble())
            .put("movingTime", trip.movingTime)
            .put("stoppedTime", trip.stoppedTime)
            .put("maxSpeed", trip.maxSpeed.toDouble() / KMH_TO_MPS)
            .put("maxGForce", trip.maxGForce.toDouble())
            .put("maxLateralGForce", trip.maxLateralGForce.toDouble())
            .put("elevationGain", trip.elevationGain.toDouble())
            .put("avgSpeed", trip.avgSpeed.toDouble() / KMH_TO_MPS)
            .put("cornerCount", trip.cornerCount)
            .put("twistinessScore", trip.twistinessScore.toDouble())
            .put("encodedRoutePolyline", trip.encodedRoutePolyline ?: JSONObject.NULL)
            .put("title", trip.title ?: JSONObject.NULL)
            .put("visibility", "PRIVATE")
            .put("routePoints", routePoints)
    }

    private fun Long.toCloudTripId(): String =
        UUID.nameUUIDFromBytes(ByteBuffer.allocate(8).putLong(this).array()).toString()

    private fun Long.toCloudPointId(): String =
        UUID.nameUUIDFromBytes(ByteBuffer.allocate(8).putLong(this).array()).toString()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val KMH_TO_MPS = 3.6
    }
}
