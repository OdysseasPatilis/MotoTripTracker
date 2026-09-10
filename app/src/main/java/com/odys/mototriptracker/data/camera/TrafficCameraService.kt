package com.odys.mototriptracker.data.camera

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.edit
import com.odys.mototriptracker.data.navigation.NavigationVoicePrompt
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficCameraService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val regionPackStore: TrafficCameraRegionPackStore,
    private val packStore: TrafficCameraPackStore,
    private val countryResolver: TrafficCameraCountryResolver,
    private val voice: NavigationVoicePrompt,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
    private val packHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val _nearbyCameras = MutableStateFlow<List<TrafficCamera>>(emptyList())
    val nearbyCameras: StateFlow<List<TrafficCamera>> = _nearbyCameras.asStateFlow()

    private val _activeAlert = MutableStateFlow<TrafficCameraAlert?>(null)
    val activeAlert: StateFlow<TrafficCameraAlert?> = _activeAlert.asStateFlow()

    private val _downloadStatus =
        MutableStateFlow<TrafficCameraPackDownloadStatus>(TrafficCameraPackDownloadStatus.Idle)
    val downloadStatus: StateFlow<TrafficCameraPackDownloadStatus> = _downloadStatus.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cacheById: MutableMap<String, CachedEntry> = loadCache().toMutableMap()
    private val liveById = mutableMapOf<String, TrafficCamera>()
    private var downloadedPacksByCountry: MutableMap<String, TrafficCameraRegionPack> =
        packStore.loadAllPacks().toMutableMap()
    private val announcedIds = mutableSetOf<String>()
    private var lastFetchLat: Double? = null
    private var lastFetchLng: Double? = null
    private var lastFetchTimeMs: Long = 0L
    private var preferredEndpointIndex = 0
    private var fetchJob: Job? = null
    private var packJob: Job? = null
    private var alertClearJob: Job? = null
    private var statusClearJob: Job? = null
    private var downloadingCountry: String? = null
    private var alertsEnabled = false
    private var isFetching = false

    init {
        AppLogger.i(
            AppLogger.Category.TRAFFIC_CAMERA,
            "TrafficCameraService ready packs=${regionPackStore.packs.size} " +
                "downloaded=${downloadedPacksByCountry.size} cache=${cacheById.size}",
        )
    }

    fun refresh(location: Location, alertsEnabled: Boolean = true) {
        this.alertsEnabled = alertsEnabled
        publishNearby(location)
        if (alertsEnabled) {
            evaluateAlert(location)
        } else if (_activeAlert.value != null) {
            _activeAlert.value = null
        }
        ensureCountryPack(location, alertsEnabled)

        if (!shouldFetch(location)) return
        fetchJob?.cancel()
        fetchJob = scope.launch {
            fetchOverpass(location)
        }
    }

    fun reset() {
        fetchJob?.cancel()
        fetchJob = null
        packJob?.cancel()
        packJob = null
        alertClearJob?.cancel()
        alertClearJob = null
        statusClearJob?.cancel()
        statusClearJob = null
        downloadingCountry = null
        _downloadStatus.value = TrafficCameraPackDownloadStatus.Idle
        _activeAlert.value = null
        announcedIds.clear()
        _nearbyCameras.value = emptyList()
        // Keep pack + disk cache + last live results for the next ride.
        AppLogger.i(AppLogger.Category.TRAFFIC_CAMERA, "Traffic camera alerts reset")
    }

    private fun allKnownCameras(): List<TrafficCamera> {
        val byId = linkedMapOf<String, TrafficCamera>()
        regionPackStore.packs.forEach { pack ->
            pack.cameras.forEach { byId[it.id] = it }
        }
        downloadedPacksByCountry.values.forEach { pack ->
            pack.cameras.forEach { byId[it.id] = it }
        }
        cacheById.values.forEach { byId[it.camera.id] = it.camera }
        liveById.values.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    private fun publishNearby(location: Location) {
        _nearbyCameras.value = allKnownCameras()
            .map { it to distanceMeters(location, it) }
            .filter { it.second <= NEARBY_RADIUS_METERS }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun ensureCountryPack(location: Location, alertsEnabled: Boolean) {
        // Avoid canceling an in-flight download on every GPS tick.
        if (packJob?.isActive == true) return
        packJob = scope.launch {
            try {
                ensureCountryPackAsync(location, alertsEnabled)
            } finally {
                packJob = null
            }
        }
    }

    private suspend fun ensureCountryPackAsync(location: Location, alertsEnabled: Boolean) {
        val country = countryResolver.resolve(location) ?: return
        if (packStore.isUnsupported(country)) return

        packStore.loadPack(country)?.let { (loaded, _) ->
            packStore.touch(country)
            downloadedPacksByCountry[country] = loaded
            publishNearby(location)
            if (alertsEnabled) evaluateAlert(location)
            if (packStore.isFresh(country)) return
        }

        if (downloadingCountry == country) return
        downloadingCountry = country
        val localeName = runCatching {
            Locale.Builder().setRegion(country).build().displayCountry
                .takeIf { it.isNotBlank() && !it.equals(country, ignoreCase = true) }
        }.getOrNull()
        _downloadStatus.value = TrafficCameraPackDownloadStatus.Downloading(country, localeName)

        try {
            val pack = TrafficCameraPackDownloader.download(country, packHttpClient)
            packStore.save(pack, country, System.currentTimeMillis())
            downloadedPacksByCountry[country] = pack
            downloadingCountry = null
            _downloadStatus.value = TrafficCameraPackDownloadStatus.Idle
            publishNearby(location)
            if (alertsEnabled) evaluateAlert(location)
            AppLogger.i(
                AppLogger.Category.TRAFFIC_CAMERA,
                "Downloaded camera pack $country count=${pack.cameras.size}",
            )
        } catch (_: TrafficCameraPackDownloadError.UnsupportedCountry) {
            packStore.markUnsupported(country)
            downloadingCountry = null
            showTransientFailure("Camera pack unavailable — using live data")
            AppLogger.i(AppLogger.Category.TRAFFIC_CAMERA, "No camera pack for $country")
        } catch (t: Throwable) {
            downloadingCountry = null
            showTransientFailure("Camera pack unavailable — using live data")
            AppLogger.w(
                AppLogger.Category.TRAFFIC_CAMERA,
                "Camera pack download failed $country: ${t.message}",
            )
        }
    }

    private fun showTransientFailure(message: String) {
        _downloadStatus.value = TrafficCameraPackDownloadStatus.Failed(message)
        statusClearJob?.cancel()
        statusClearJob = scope.launch {
            delay(4_000)
            if (_downloadStatus.value is TrafficCameraPackDownloadStatus.Failed) {
                _downloadStatus.value = TrafficCameraPackDownloadStatus.Idle
            }
        }
    }

    private fun evaluateAlert(location: Location) {
        val speed = if (location.hasSpeed()) location.speed else -1f
        val warn = TrafficCameraLogic.warnDistanceMeters(speed)

        announcedIds.toList().forEach { id ->
            val camera = allKnownCameras().firstOrNull { it.id == id }
            if (camera == null) {
                announcedIds.remove(id)
                return@forEach
            }
            if (distanceMeters(location, camera) > warn + CLEAR_APPROACH_EXTRA_METERS) {
                announcedIds.remove(id)
            }
        }

        val course = if (location.hasBearing()) location.bearing else -1f
        val ahead = allKnownCameras().mapNotNull { camera ->
            val distance = distanceMeters(location, camera)
            if (distance > warn) return@mapNotNull null
            val bearing = TrafficCameraLogic.bearingDegrees(
                location.latitude,
                location.longitude,
                camera.latitude,
                camera.longitude,
            )
            if (!TrafficCameraLogic.isAhead(course, bearing, speed)) return@mapNotNull null
            camera to distance
        }.sortedBy { it.second }

        val (camera, distance) = ahead.firstOrNull() ?: return

        if (announcedIds.contains(camera.id)) {
            if (_activeAlert.value?.camera?.id == camera.id) {
                _activeAlert.value = TrafficCameraAlert(camera, distance)
            }
            return
        }

        announcedIds.add(camera.id)
        val alert = TrafficCameraAlert(camera, distance)
        _activeAlert.value = alert
        voice.speak(camera.speakText)
        hapticMedium()
        AppLogger.i(
            AppLogger.Category.TRAFFIC_CAMERA,
            "Camera alert ${camera.kind} ${distance.toInt()}m id=${camera.id}",
        )
        scheduleAlertClear()
    }

    private fun scheduleAlertClear() {
        alertClearJob?.cancel()
        alertClearJob = scope.launch {
            delay(ALERT_BANNER_MS)
            _activeAlert.value = null
        }
    }

    private fun shouldFetch(location: Location): Boolean {
        if (isFetching) return false
        val lastLat = lastFetchLat
        val lastLng = lastFetchLng
        if (lastLat == null || lastLng == null || lastFetchTimeMs == 0L) return true
        val results = FloatArray(1)
        Location.distanceBetween(lastLat, lastLng, location.latitude, location.longitude, results)
        val moved = results[0] >= MIN_FETCH_DISTANCE_METERS
        val waited = System.currentTimeMillis() - lastFetchTimeMs >= MIN_FETCH_INTERVAL_MS
        return moved || waited
    }

    private suspend fun fetchOverpass(location: Location) {
        isFetching = true
        try {
            val lat = location.latitude
            val lon = location.longitude
            val query = """
                [out:json][timeout:15];
                (
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["highway"="speed_camera"];
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["device"="speed_camera"];
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["enforcement"="maxspeed"];
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["enforcement"="speed"];
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["enforcement"="traffic_signals"];
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["camera:type"="speed"];
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["camera:type"="speed_camera"];
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["camera:type"="red_light"];
                  nwr(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["camera:type"="traffic_signals"];
                  relation(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["type"="enforcement"]["enforcement"="maxspeed"];
                  relation(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["type"="enforcement"]["enforcement"="traffic_signals"];
                  relation(around:$OVERPASS_RADIUS_METERS,$lat,$lon)["type"="enforcement"]["enforcement"="speed"];
                );
                out center tags;
            """.trimIndent()

            val cameras = queryCameras(query)
            lastFetchLat = location.latitude
            lastFetchLng = location.longitude
            lastFetchTimeMs = System.currentTimeMillis()

            if (cameras == null) {
                AppLogger.w(
                    AppLogger.Category.TRAFFIC_CAMERA,
                    "Overpass camera fetch failed @ ${AppLogger.coordinate(lat, lon)}",
                )
                return
            }

            cameras.forEach { camera ->
                liveById[camera.id] = camera
                cacheById[camera.id] = CachedEntry(camera, System.currentTimeMillis())
            }
            pruneAndPersistCache()
            publishNearby(location)
            if (alertsEnabled) evaluateAlert(location)
            AppLogger.i(
                AppLogger.Category.TRAFFIC_CAMERA,
                "Overpass cameras +${cameras.size} live=${liveById.size}",
            )
        } finally {
            isFetching = false
        }
    }

    private suspend fun queryCameras(query: String): List<TrafficCamera>? {
        val endpoints = rotatedEndpoints()
        endpoints.forEachIndexed { rotationIndex, endpoint ->
            val cameras = requestCameras(endpoint, query)
            if (cameras != null) {
                preferredEndpointIndex =
                    (preferredEndpointIndex + rotationIndex) % OVERPASS_ENDPOINTS.size
                return cameras
            }
        }
        return null
    }

    private fun rotatedEndpoints(): List<String> {
        val list = OVERPASS_ENDPOINTS.toMutableList()
        if (preferredEndpointIndex in list.indices) {
            val preferred = list.removeAt(preferredEndpointIndex)
            list.add(0, preferred)
        }
        return list
    }

    private suspend fun requestCameras(endpoint: String, query: String): List<TrafficCamera>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder().add("data", query).build()
                val request = Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLogger.w(
                            AppLogger.Category.TRAFFIC_CAMERA,
                            "Overpass HTTP ${response.code} from $endpoint",
                        )
                        return@runCatching null
                    }
                    parseOverpassCameras(response.body?.string().orEmpty())
                }
            }.onFailure {
                AppLogger.w(
                    AppLogger.Category.TRAFFIC_CAMERA,
                    "Overpass $endpoint failed: ${it.message}",
                )
            }.getOrNull()
        }

    private fun pruneAndPersistCache() {
        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS
        cacheById = cacheById.filterValues { it.savedAtMs >= cutoff }.toMutableMap()
        if (cacheById.size > MAX_CACHE_ENTRIES) {
            val keep = cacheById.values.sortedByDescending { it.savedAtMs }.take(MAX_CACHE_ENTRIES)
            cacheById = keep.associateBy { it.camera.id }.toMutableMap()
        }
        val array = JSONArray()
        cacheById.values.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.camera.id)
                    .put("lat", entry.camera.latitude)
                    .put("lon", entry.camera.longitude)
                    .put(
                        "kind",
                        when (entry.camera.kind) {
                            TrafficCameraKind.Speed -> "speed"
                            TrafficCameraKind.RedLight -> "redLight"
                        },
                    )
                    .put("savedAtMs", entry.savedAtMs),
            )
        }
        prefs.edit { putString(KEY_CACHE, array.toString()) }
    }

    private fun loadCache(): Map<String, CachedEntry> {
        val raw = prefs.getString(KEY_CACHE, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            val cutoff = System.currentTimeMillis() - CACHE_TTL_MS
            buildMap {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val savedAt = obj.optLong("savedAtMs", 0L)
                    if (savedAt < cutoff) continue
                    val kind = when (obj.optString("kind")) {
                        "speed" -> TrafficCameraKind.Speed
                        "redLight" -> TrafficCameraKind.RedLight
                        else -> continue
                    }
                    val camera = TrafficCamera(
                        id = obj.getString("id"),
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lon"),
                        kind = kind,
                    )
                    put(camera.id, CachedEntry(camera, savedAt))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun hapticMedium() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    private data class CachedEntry(
        val camera: TrafficCamera,
        val savedAtMs: Long,
    )

    companion object {
        private const val PREFS_NAME = "moto_app_prefs"
        private const val KEY_CACHE = "moto_traffic_camera_cache_v1"
        private const val NEARBY_RADIUS_METERS = 3_000.0
        private const val OVERPASS_RADIUS_METERS = 2_500
        private const val MIN_FETCH_INTERVAL_MS = 45_000L
        private const val MIN_FETCH_DISTANCE_METERS = 400f
        private const val CLEAR_APPROACH_EXTRA_METERS = 80.0
        private const val ALERT_BANNER_MS = 6_000L
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_CACHE_ENTRIES = 2_000
        private const val USER_AGENT = "MotoTripTracker/1.0 (Android; motorcycle trip tracker)"

        private val OVERPASS_ENDPOINTS = listOf(
            "https://lz4.overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter",
        )

        fun parseOverpassCameras(body: String): List<TrafficCamera> {
            if (body.isBlank()) return emptyList()
            val root = JSONObject(body)
            val elements = root.optJSONArray("elements") ?: return emptyList()
            return buildList {
                for (i in 0 until elements.length()) {
                    val element = elements.getJSONObject(i)
                    val tagsJson = element.optJSONObject("tags") ?: continue
                    val tags = buildMap {
                        val keys = tagsJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            put(key, tagsJson.optString(key))
                        }
                    }
                    val kind = TrafficCameraLogic.kindFromOsmTags(tags) ?: continue
                    val center = element.optJSONObject("center")
                    val lat = element.optDouble("lat", Double.NaN).takeIf { it.isFinite() }
                        ?: center?.optDouble("lat", Double.NaN)?.takeIf { it.isFinite() }
                        ?: continue
                    val lon = element.optDouble("lon", Double.NaN).takeIf { it.isFinite() }
                        ?: center?.optDouble("lon", Double.NaN)?.takeIf { it.isFinite() }
                        ?: continue
                    val type = element.optString("type", "node")
                    val id = element.optLong("id", -1L)
                    if (id < 0) continue
                    add(
                        TrafficCamera(
                            id = "osm:$type/$id",
                            latitude = lat,
                            longitude = lon,
                            kind = kind,
                        ),
                    )
                }
            }
        }

        fun distanceMeters(location: Location, camera: TrafficCamera): Double {
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                camera.latitude,
                camera.longitude,
                results,
            )
            return results[0].toDouble()
        }
    }
}
