package com.odys.mototriptracker.data.road

import com.odys.mototriptracker.domain.RoadSnapLogic
import com.odys.mototriptracker.domain.RoadSnapper
import com.odys.mototriptracker.domain.SnappedRoad
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.LogThrottle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rolling GPS buffer → Google [snapToRoads] → latest [SnappedRoad].
 * Trip recording keeps raw GPS; consumers (speed limit / nav) prefer this when fresh.
 */
@Singleton
class RoadSnapService @Inject constructor(
    private val roadsClient: GoogleRoadsClient,
) : RoadSnapper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var buffer: List<RoadSnapLogic.GpsPoint> = emptyList()
    private var lastSnapRequestLat: Double? = null
    private var lastSnapRequestLng: Double? = null
    private var lastSnapRequestMs: Long = 0L
    private var snapJob: Job? = null

    private val _snapped = MutableStateFlow<SnappedRoad?>(null)
    override val snapped: StateFlow<SnappedRoad?> = _snapped.asStateFlow()

    override fun reset() {
        snapJob?.cancel()
        snapJob = null
        buffer = emptyList()
        lastSnapRequestLat = null
        lastSnapRequestLng = null
        lastSnapRequestMs = 0L
        _snapped.value = null
    }

    override fun onRawLocation(
        latitude: Double,
        longitude: Double,
        nowMs: Long,
    ) {
        buffer = RoadSnapLogic.appendPoint(buffer, latitude, longitude, nowMs)
        if (!RoadSnapLogic.shouldSnap(
                buffer = buffer,
                lastSnapLat = lastSnapRequestLat,
                lastSnapLng = lastSnapRequestLng,
                lastSnapRequestMs = lastSnapRequestMs,
                nowMs = nowMs,
            )
        ) {
            return
        }
        requestSnap(nowMs)
    }

    override fun latestFresh(nowMs: Long): SnappedRoad? {
        val snap = _snapped.value
        return snap?.takeIf { RoadSnapLogic.isFresh(it, nowMs) }
    }

    private fun requestSnap(nowMs: Long) {
        val path = buffer
        if (path.isEmpty()) return
        val latest = path.last()
        lastSnapRequestLat = latest.latitude
        lastSnapRequestLng = latest.longitude
        lastSnapRequestMs = nowMs
        snapJob?.cancel()
        snapJob = scope.launch(Dispatchers.IO) {
            val points = roadsClient.snapToRoads(path.map { it.latitude to it.longitude })
            if (points.isEmpty()) {
                if (LogThrottle.shouldLog("roads.snap.empty", 20_000L)) {
                    AppLogger.d(AppLogger.Category.SPEED_LIMIT, "snapToRoads returned empty")
                }
                return@launch
            }
            val snap = RoadSnapLogic.pickLatestSnap(
                snappedPoints = points.map { it.latitude to it.longitude },
                placeIds = points.map { it.placeId },
                originalIndices = points.map { it.originalIndex },
                rawPath = path,
                nowMs = System.currentTimeMillis(),
            ) ?: return@launch
            _snapped.value = snap
            if (LogThrottle.shouldLog("roads.snap.ok", 15_000L)) {
                AppLogger.d(
                    AppLogger.Category.SPEED_LIMIT,
                    "Snapped placeId=${snap.placeId} @ ${AppLogger.coordinate(snap.latitude, snap.longitude)}"
                )
            }
        }
    }
}
