package com.odys.mototriptracker.data.camera

import android.content.Context
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class TrafficCameraPackDownloadError : Exception() {
    data object EmptyCsv : TrafficCameraPackDownloadError()
    data object MissingHeader : TrafficCameraPackDownloadError()
    data object UnsupportedCountry : TrafficCameraPackDownloadError()
    data class HttpStatus(val code: Int) : TrafficCameraPackDownloadError()
    data object EmptyResponse : TrafficCameraPackDownloadError()
}

/** Fetches and parses speedcams.world country CSVs into region packs. */
object TrafficCameraPackDownloader {
    fun csvUrl(countryCode: String): String {
        val cc = countryCode.lowercase()
        return "https://speedcams.world/downloads/$cc/$cc-all.csv"
    }

    fun parseCsv(text: String, countryCode: String): TrafficCameraRegionPack {
        val cc = countryCode.uppercase()
        val lines = text
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        val headerLine = lines.firstOrNull() ?: throw TrafficCameraPackDownloadError.EmptyCsv
        val headers = parseCsvLine(headerLine).map { it.lowercase() }
        val idIdx = headers.indexOf("id")
        val latIdx = headers.indexOf("latitude")
        val lonIdx = headers.indexOf("longitude")
        if (idIdx < 0 || latIdx < 0 || lonIdx < 0) {
            throw TrafficCameraPackDownloadError.MissingHeader
        }

        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        val cameras = buildList {
            for (line in lines.drop(1)) {
                val cols = parseCsvLine(line)
                val needed = maxOf(idIdx, latIdx, lonIdx)
                if (cols.size <= needed) continue
                val rawId = cols[idIdx].trim()
                val lat = cols[latIdx].toDoubleOrNull()
                val lon = cols[lonIdx].toDoubleOrNull()
                if (rawId.isEmpty() ||
                    lat == null || lon == null ||
                    !lat.isFinite() || !lon.isFinite() ||
                    kotlin.math.abs(lat) > 90 || kotlin.math.abs(lon) > 180
                ) {
                    continue
                }
                add(
                    TrafficCamera(
                        id = "osm:node/$rawId",
                        latitude = lat,
                        longitude = lon,
                        kind = TrafficCameraKind.Speed,
                    )
                )
                minLat = minOf(minLat, lat)
                maxLat = maxOf(maxLat, lat)
                minLon = minOf(minLon, lon)
                maxLon = maxOf(maxLon, lon)
            }
        }

        if (cameras.isEmpty()) {
            minLat = 0.0
            maxLat = 0.0
            minLon = 0.0
            maxLon = 0.0
        }

        return TrafficCameraRegionPack(
            id = "country_$cc",
            name = "Country $cc traffic cameras",
            version = 1,
            south = minLat,
            west = minLon,
            north = maxLat,
            east = maxLon,
            cameras = cameras,
        )
    }

    fun parseCsvLine(line: String): List<String> = line.split(",", ignoreCase = false, limit = 0)

    suspend fun download(
        countryCode: String,
        httpClient: OkHttpClient,
    ): TrafficCameraRegionPack = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(csvUrl(countryCode))
            .header("User-Agent", "MotoTripTracker/1.0 (Android; motorcycle trip tracker)")
            .header("Accept", "text/csv")
            .build()
        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> throw TrafficCameraPackDownloadError.UnsupportedCountry
                !in 200..299 -> throw TrafficCameraPackDownloadError.HttpStatus(response.code)
            }
            val text = response.body?.string().orEmpty()
            if (text.isEmpty()) throw TrafficCameraPackDownloadError.EmptyResponse
            parseCsv(text, countryCode)
        }
    }
}

/** On-disk country packs + meta (TTL, unsupported cooldown, LRU). */
@Singleton
class TrafficCameraPackStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    data class MetaEntry(
        var downloadedAtMs: Long? = null,
        var lastUsedAtMs: Long? = null,
        var unsupportedUntilMs: Long? = null,
    )

    private val directory: File =
        File(context.filesDir, "CameraPacks").also { it.mkdirs() }
    private val metaFile = File(directory, "camera_pack_meta.json")
    private val lock = Any()
    private var meta: MutableMap<String, MetaEntry> = loadMeta().toMutableMap()

    fun packFile(countryCode: String): File =
        File(directory, "camera_pack_${countryCode.uppercase()}.json")

    fun loadPack(countryCode: String): Pair<TrafficCameraRegionPack, Long>? {
        val cc = countryCode.uppercase()
        val file = packFile(cc)
        if (!file.exists()) return null
        val pack = runCatching {
            TrafficCameraRegionPackStore.decode(file.readText())
        }.getOrNull() ?: return null
        val downloadedAt = synchronized(lock) {
            meta[cc]?.downloadedAtMs ?: 0L
        }
        return pack to downloadedAt
    }

    fun save(pack: TrafficCameraRegionPack, countryCode: String, downloadedAtMs: Long) {
        val cc = countryCode.uppercase()
        packFile(cc).writeText(TrafficCameraRegionPackStore.encode(pack))
        synchronized(lock) {
            val entry = meta.getOrPut(cc) { MetaEntry() }
            entry.downloadedAtMs = downloadedAtMs
            entry.lastUsedAtMs = downloadedAtMs
            entry.unsupportedUntilMs = null
            persistMetaLocked()
        }
        evictIfNeeded()
    }

    fun touch(countryCode: String, atMs: Long = System.currentTimeMillis()) {
        val cc = countryCode.uppercase()
        synchronized(lock) {
            val entry = meta.getOrPut(cc) { MetaEntry() }
            entry.lastUsedAtMs = atMs
            persistMetaLocked()
        }
    }

    fun isFresh(
        countryCode: String,
        nowMs: Long = System.currentTimeMillis(),
        ttlMs: Long = DEFAULT_TTL_MS,
    ): Boolean {
        val downloadedAt = synchronized(lock) {
            meta[countryCode.uppercase()]?.downloadedAtMs
        } ?: return false
        return nowMs - downloadedAt < ttlMs
    }

    fun markUnsupported(
        countryCode: String,
        atMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = DEFAULT_UNSUPPORTED_COOLDOWN_MS,
    ) {
        val cc = countryCode.uppercase()
        synchronized(lock) {
            val entry = meta.getOrPut(cc) { MetaEntry() }
            entry.unsupportedUntilMs = atMs + cooldownMs
            persistMetaLocked()
        }
    }

    fun isUnsupported(
        countryCode: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val until = synchronized(lock) {
            meta[countryCode.uppercase()]?.unsupportedUntilMs
        } ?: return false
        return nowMs < until
    }

    fun loadAllPacks(): Map<String, TrafficCameraRegionPack> {
        val codes = synchronized(lock) { meta.keys.toList() }
        return buildMap {
            for (cc in codes) {
                loadPack(cc)?.let { put(cc, it.first) }
            }
        }
    }

    fun evictIfNeeded(maxCountries: Int = DEFAULT_MAX_COUNTRIES) {
        synchronized(lock) {
            val sorted = meta.entries
                .filter { it.value.downloadedAtMs != null }
                .sortedBy { it.value.lastUsedAtMs ?: 0L }
            if (sorted.size <= maxCountries) return
            val toRemove = sorted.take(sorted.size - maxCountries)
            for ((cc, _) in toRemove) {
                packFile(cc).delete()
                meta.remove(cc)
            }
            persistMetaLocked()
        }
    }

    private fun persistMetaLocked() {
        val root = JSONObject()
        meta.forEach { (cc, entry) ->
            root.put(
                cc,
                JSONObject()
                    .put("downloadedAtMs", entry.downloadedAtMs)
                    .put("lastUsedAtMs", entry.lastUsedAtMs)
                    .put("unsupportedUntilMs", entry.unsupportedUntilMs),
            )
        }
        runCatching { metaFile.writeText(root.toString()) }
            .onFailure {
                AppLogger.w(
                    AppLogger.Category.TRAFFIC_CAMERA,
                    "Failed persisting camera pack meta: ${it.message}",
                )
            }
    }

    private fun loadMeta(): Map<String, MetaEntry> {
        if (!metaFile.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(metaFile.readText())
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val cc = keys.next()
                    val obj = root.getJSONObject(cc)
                    put(
                        cc,
                        MetaEntry(
                            downloadedAtMs = if (obj.isNull("downloadedAtMs")) null
                            else obj.optLong("downloadedAtMs"),
                            lastUsedAtMs = if (obj.isNull("lastUsedAtMs")) null
                            else obj.optLong("lastUsedAtMs"),
                            unsupportedUntilMs = if (obj.isNull("unsupportedUntilMs")) null
                            else obj.optLong("unsupportedUntilMs"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    companion object {
        const val DEFAULT_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val DEFAULT_UNSUPPORTED_COOLDOWN_MS = 24L * 60 * 60 * 1000
        const val DEFAULT_MAX_COUNTRIES = 10
    }
}
