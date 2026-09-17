package com.odys.mototriptracker.data.navigation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.net.toUri
import com.odys.mototriptracker.domain.Geo
import com.odys.mototriptracker.domain.RouteCoordinate
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationService @Inject constructor(
    @ApplicationContext context: Context,
    private val voice: NavigationVoicePrompt,
    private val destinationHistory: DestinationSearchHistory,
    private val motoTravelEstimator: MotoTravelEstimatorStore,
    private val destinationSearcher: DestinationSearcher,
    private val directionsRouter: DirectionsRouter,
) {
    private val context = context

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(NavigationState(isVoiceEnabled = voice.isEnabled))
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    var onRouteApplied: ((List<RouteCoordinate>, Double) -> Unit)? = null
    var onRouteCleared: (() -> Unit)? = null

    private var originLat: Double? = null
    private var originLng: Double? = null
    private var totalRouteDistanceMeters = 0.0
    private var totalTravelTimeSeconds = 0.0
    private var plannedCarTravelTimeSeconds = 0.0
    private var plannedMotoTravelTimeSeconds = 0.0
    private var navigationStartedAtMs: Long? = null
    private var arrivalCandidateSinceMs: Long? = null
    private var nearestRouteDistanceMeters = 0.0
    private var lastRecalculateAtMs = 0L
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var approachedStepId: String? = null
    private var announcedStepId: String? = null
    private var routeRequestGeneration = 0L

    fun updateSearchQuery(query: String) {
        if (query == _state.value.searchQuery) return
        _state.update {
            it.copy(
                searchQuery = query,
                searchError = null,
                isSearching = query.isNotBlank(),
                searchResults = if (query.isBlank()) emptyList() else it.searchResults
            )
        }
        if (query.isBlank()) {
            searchJob?.cancel()
            _state.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(isSearching = true, searchError = null) }
            val results = destinationSearcher.search(query.trim(), originLat, originLng)
            if (_state.value.searchQuery.trim() != query.trim()) return@launch
            _state.update {
                it.copy(
                    searchResults = results,
                    isSearching = false,
                    searchError = if (results.isEmpty()) {
                        "No places found. Try the workplace name plus city, e.g. \"Acme Athens\"."
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun updateOrigin(latitude: Double, longitude: Double) {
        originLat = latitude
        originLng = longitude
        val state = _state.value
        if (state.isPreviewing &&
            state.previewRoutes.isEmpty() &&
            !state.isRouting &&
            state.hasDestination
        ) {
            computeRoute(isRecalculation = false, requestAlternates = true)
        }
        if (!state.hasRoute) return
        recomputeRemaining(latitude, longitude)
        if (!state.isNavigating) return
        advanceStepIfNeeded(latitude, longitude)
        checkOffRouteAndRecalculate(latitude, longitude)
        checkArrival(latitude, longitude)
    }

    fun selectSearchResult(result: NavigationSearchResult) {
        scope.launch {
            val latLng = when {
                result.latitude != null && result.longitude != null ->
                    result.latitude to result.longitude
                result.placeId.isNotBlank() ->
                    destinationSearcher.resolvePlace(result.placeId, result.title)
                else -> null
            }
            if (latLng == null) {
                AppLogger.w(AppLogger.Category.UI, "Could not resolve place: ${result.title}")
                _state.update { it.copy(searchError = "Couldn't open that place. Try another result.") }
                return@launch
            }
            destinationSearcher.resetAutocompleteSession()
            beginPreview(
                latitude = latLng.first,
                longitude = latLng.second,
                name = result.title,
                subtitle = result.subtitle,
            )
        }
    }

    fun selectHistoryEntry(entry: DestinationHistoryEntry) {
        beginPreview(
            latitude = entry.latitude,
            longitude = entry.longitude,
            name = entry.name,
            subtitle = entry.subtitle,
        )
    }

    fun setDestination(latitude: Double, longitude: Double, name: String, subtitle: String = "") {
        beginPreview(latitude, longitude, name, subtitle)
    }

    suspend fun resolveMapPlace(
        placeId: String,
        fallbackName: String,
        latitude: Double,
        longitude: Double,
    ): PickedMapPlace = destinationSearcher.resolveMapPlace(
        placeId = placeId,
        fallbackName = fallbackName,
        latitude = latitude,
        longitude = longitude,
    )

    fun beginPreview(latitude: Double, longitude: Double, name: String, subtitle: String = "") {
        routeRequestGeneration += 1
        destinationHistory.add(name = name, subtitle = subtitle, latitude = latitude, longitude = longitude)
        approachedStepId = null
        announcedStepId = null
        voice.stop()
        _state.update {
            it.copy(
                destinationLatitude = latitude,
                destinationLongitude = longitude,
                destinationName = name,
                searchResults = emptyList(),
                searchQuery = "",
                isSearching = false,
                searchError = null,
                previewErrorMessage = null,
                previewRoutes = emptyList(),
                selectedRouteId = null,
                routeCoordinates = emptyList(),
                steps = emptyList(),
                currentStepIndex = 0,
                distanceToNextManeuverMeters = 0.0,
                distanceRemainingMeters = 0.0,
                etaEpochMs = null,
                isOffRoute = false,
                isRecalculating = false,
                phase = NavigationPhase.Previewing,
            )
        }
        computeRoute(isRecalculation = false, requestAlternates = true)
    }

    fun selectPreviewRoute(id: String) {
        val state = _state.value
        if (!state.isPreviewing) return
        val option = state.previewRoutes.firstOrNull { it.id == id } ?: return
        applyPreviewSelection(option)
    }

    fun confirmStartNavigation() {
        val option = _state.value.selectedPreviewRoute ?: return
        if (!_state.value.isPreviewing) return
        navigationStartedAtMs = System.currentTimeMillis()
        plannedCarTravelTimeSeconds = option.expectedTravelTimeSeconds
        plannedMotoTravelTimeSeconds = option.motoTravelTimeSeconds
        arrivalCandidateSinceMs = null
        _state.update {
            it.copy(
                phase = NavigationPhase.Navigating,
                lastTimingResult = null,
                plannedCarTravelTimeSeconds = option.expectedTravelTimeSeconds,
                plannedMotoTravelTimeSeconds = option.motoTravelTimeSeconds,
            )
        }
        applyRoute(
            DirectionsResult(
                distanceMeters = option.distanceMeters,
                carTravelTimeSeconds = option.expectedTravelTimeSeconds,
                motoTravelTimeSeconds = option.motoTravelTimeSeconds,
                trafficDelaySeconds = option.trafficDelaySeconds,
                coordinates = option.coordinates,
                steps = option.steps,
            ),
            isRecalculation = false,
        )
        AppLogger.i(
            AppLogger.Category.UI,
            "Navigation started car=${option.expectedTravelTimeSeconds.toInt()}s " +
                "moto=${option.motoTravelTimeSeconds.toInt()}s"
        )
    }

    fun cancelPreview() {
        clear()
    }

    fun clear() {
        clear(stopVoice = true)
    }

    fun clear(stopVoice: Boolean) {
        if (navigationStartedAtMs != null) {
            finalizeTimingIfNeeded()
        }
        routeJob?.cancel()
        searchJob?.cancel()
        routeRequestGeneration += 1
        // Keep origin so the next destination can route immediately.
        totalRouteDistanceMeters = 0.0
        totalTravelTimeSeconds = 0.0
        plannedCarTravelTimeSeconds = 0.0
        plannedMotoTravelTimeSeconds = 0.0
        navigationStartedAtMs = null
        arrivalCandidateSinceMs = null
        nearestRouteDistanceMeters = 0.0
        approachedStepId = null
        announcedStepId = null
        if (stopVoice) voice.stop()
        val voiceEnabled = _state.value.isVoiceEnabled
        val timing = _state.value.lastTimingResult
        _state.value = NavigationState(
            isVoiceEnabled = voiceEnabled,
            lastTimingResult = timing,
        )
        onRouteCleared?.invoke()
        AppLogger.i(AppLogger.Category.UI, "Navigation cleared")
    }

    fun dismissTimingResult() {
        _state.update { it.copy(lastTimingResult = null) }
    }

    fun toggleVoice() {
        val enabled = !_state.value.isVoiceEnabled
        voice.isEnabled = enabled
        _state.update { it.copy(isVoiceEnabled = enabled) }
        if (!enabled) voice.stop()
        AppLogger.i(AppLogger.Category.UI, "Navigation voice ${if (enabled) "on" else "off"}")
    }


    fun openInGoogleMaps() {
        val lat = _state.value.destinationLatitude ?: return
        val lng = _state.value.destinationLongitude ?: return
        val uri = "google.navigation:q=$lat,$lng&mode=d".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun computeRoute(isRecalculation: Boolean, requestAlternates: Boolean = false) {
        val oLat = originLat
        val oLng = originLng
        val destLat = _state.value.destinationLatitude
        val destLng = _state.value.destinationLongitude
        if (oLat == null || oLng == null || destLat == null || destLng == null) {
            AppLogger.w(
                AppLogger.Category.UI,
                "Cannot route — origin=${oLat != null} dest=${destLat != null}"
            )
            if (_state.value.isPreviewing && !isRecalculation) {
                _state.update { it.copy(previewErrorMessage = "Waiting for your location…") }
            }
            return
        }

        routeJob?.cancel()
        routeRequestGeneration += 1
        val generation = routeRequestGeneration
        _state.update {
            it.copy(
                isRouting = !isRecalculation,
                isRecalculating = isRecalculation,
                previewErrorMessage = if (!isRecalculation) null else it.previewErrorMessage,
            )
        }
        routeJob = scope.launch {
            val wantAlternates = requestAlternates && !isRecalculation
            val routes = directionsRouter.fetchRoutes(
                oLat, oLng, destLat, destLng, alternatives = wantAlternates,
            )
            if (generation != routeRequestGeneration) return@launch
            if (isRecalculation) {
                if (!_state.value.isNavigating) return@launch
                val route = routes.firstOrNull()
                if (route == null) {
                    AppLogger.w(AppLogger.Category.UI, "All routing providers failed")
                    _state.update { it.copy(isRouting = false, isRecalculating = false) }
                    return@launch
                }
                applyRoute(route, isRecalculation = true)
                return@launch
            }
            if (!_state.value.isPreviewing) return@launch
            applyPreviewRoutes(routes)
        }
    }

    private fun applyPreviewRoutes(routes: List<DirectionsResult>) {
        if (routes.isEmpty()) {
            _state.update {
                it.copy(
                    isRouting = false,
                    isRecalculating = false,
                    previewRoutes = emptyList(),
                    selectedRouteId = null,
                    routeCoordinates = emptyList(),
                    previewErrorMessage = "Couldn't find a driving route.",
                )
            }
            return
        }
        val options = routes.map { route ->
            NavRouteOption(
                coordinates = route.coordinates,
                distanceMeters = route.distanceMeters,
                expectedTravelTimeSeconds = route.carTravelTimeSeconds,
                motoTravelTimeSeconds = route.motoTravelTimeSeconds,
                trafficDelaySeconds = route.trafficDelaySeconds,
                steps = route.steps,
            )
        }
        val first = options.first()
        applyPreviewSelection(first, allOptions = options)
        AppLogger.i(
            AppLogger.Category.UI,
            "Preview routes ready: ${options.size} option(s)"
        )
    }

    private fun applyPreviewSelection(
        option: NavRouteOption,
        allOptions: List<NavRouteOption> = _state.value.previewRoutes,
    ) {
        totalRouteDistanceMeters = option.distanceMeters
        totalTravelTimeSeconds = option.motoTravelTimeSeconds
        plannedCarTravelTimeSeconds = option.expectedTravelTimeSeconds
        plannedMotoTravelTimeSeconds = option.motoTravelTimeSeconds
        nearestRouteDistanceMeters = 0.0
        _state.update {
            it.copy(
                previewRoutes = allOptions.ifEmpty { listOf(option) },
                selectedRouteId = option.id,
                routeCoordinates = option.coordinates,
                distanceRemainingMeters = option.distanceMeters,
                etaEpochMs = if (option.motoTravelTimeSeconds > 0) {
                    System.currentTimeMillis() + (option.motoTravelTimeSeconds * 1000).toLong()
                } else {
                    null
                },
                steps = option.steps,
                currentStepIndex = 0,
                distanceToNextManeuverMeters = option.steps.firstOrNull()?.distanceMeters
                    ?: option.distanceMeters,
                isRouting = false,
                isRecalculating = false,
                isOffRoute = false,
                previewErrorMessage = null,
                plannedCarTravelTimeSeconds = option.expectedTravelTimeSeconds,
                plannedMotoTravelTimeSeconds = option.motoTravelTimeSeconds,
                phase = NavigationPhase.Previewing,
            )
        }
    }

    private fun applyRoute(route: DirectionsResult, isRecalculation: Boolean) {
        totalRouteDistanceMeters = route.distanceMeters
        totalTravelTimeSeconds = route.motoTravelTimeSeconds
        plannedCarTravelTimeSeconds = route.carTravelTimeSeconds
        plannedMotoTravelTimeSeconds = route.motoTravelTimeSeconds
        nearestRouteDistanceMeters = 0.0
        approachedStepId = null
        announcedStepId = null
        voice.stop()
        if (isRecalculation) lastRecalculateAtMs = System.currentTimeMillis()

        _state.update {
            it.copy(
                routeCoordinates = route.coordinates,
                distanceRemainingMeters = route.distanceMeters,
                etaEpochMs = if (route.motoTravelTimeSeconds > 0) {
                    System.currentTimeMillis() + (route.motoTravelTimeSeconds * 1000).toLong()
                } else {
                    null
                },
                steps = route.steps,
                currentStepIndex = 0,
                distanceToNextManeuverMeters = route.steps.firstOrNull()?.distanceMeters ?: route.distanceMeters,
                isRouting = false,
                isRecalculating = false,
                isOffRoute = false,
                phase = NavigationPhase.Navigating,
                previewRoutes = emptyList(),
                selectedRouteId = null,
                previewErrorMessage = null,
                plannedCarTravelTimeSeconds = route.carTravelTimeSeconds,
                plannedMotoTravelTimeSeconds = route.motoTravelTimeSeconds,
            )
        }
        onRouteApplied?.invoke(route.coordinates, route.motoTravelTimeSeconds)
        AppLogger.i(
            AppLogger.Category.UI,
            "Route ${if (isRecalculation) "recalculated" else "computed"}: " +
                "${route.distanceMeters.toInt()}m, ${route.steps.size} steps, " +
                "car=${route.carTravelTimeSeconds.toInt()}s moto=${route.motoTravelTimeSeconds.toInt()}s"
        )
    }

    private fun finalizeTimingIfNeeded() {
        val started = navigationStartedAtMs ?: return
        if (!_state.value.isNavigating) return
        if (plannedCarTravelTimeSeconds <= 0) return
        val actual = (System.currentTimeMillis() - started) / 1000.0
        if (actual < 45) return

        val result = NavTimingResult(
            distanceMeters = totalRouteDistanceMeters,
            carEstimateSeconds = plannedCarTravelTimeSeconds,
            motoEstimateSeconds = if (plannedMotoTravelTimeSeconds > 0) {
                plannedMotoTravelTimeSeconds
            } else {
                plannedCarTravelTimeSeconds
            },
            actualSeconds = actual,
        )
        motoTravelEstimator.learn(from = result)
        navigationStartedAtMs = null
        _state.update { it.copy(lastTimingResult = result) }
        AppLogger.i(
            AppLogger.Category.UI,
            "Nav timing actual=${actual.toInt()}s car=${result.carEstimateSeconds.toInt()}s " +
                "moto=${result.motoEstimateSeconds.toInt()}s savedVsCar=${result.savedVersusCarSeconds.toInt()}s"
        )
    }

    private fun checkArrival(latitude: Double, longitude: Double) {
        val destLat = _state.value.destinationLatitude
        val destLng = _state.value.destinationLongitude
        if (destLat == null || destLng == null) {
            arrivalCandidateSinceMs = null
            return
        }

        val toDestination = Geo.distanceMeters(latitude, longitude, destLat, destLng)
        val tick = NavigationProgressLogic.arrivalTick(
            toDestinationMeters = toDestination,
            distanceRemainingMeters = _state.value.distanceRemainingMeters,
            currentStepIndex = _state.value.currentStepIndex,
            stepCount = _state.value.steps.size,
            candidateSinceMs = arrivalCandidateSinceMs,
            nowMs = System.currentTimeMillis(),
        )
        if (tick.candidateSinceMs != null && arrivalCandidateSinceMs == null) {
            AppLogger.d(
                AppLogger.Category.UI,
                "Arrival candidate dest=${toDestination.toInt()}m " +
                    "remaining=${_state.value.distanceRemainingMeters.toInt()}m"
            )
        }
        arrivalCandidateSinceMs = tick.candidateSinceMs
        if (tick.shouldComplete) completeArrival()
    }

    private fun completeArrival() {
        if (!_state.value.isNavigating) return
        arrivalCandidateSinceMs = null
        AppLogger.i(AppLogger.Category.UI, "Arrived at destination — ending navigation")
        hapticSuccess()
        finalizeTimingIfNeeded()
        clear(stopVoice = false)
        voice.speak("You have arrived")
    }

    private fun hapticSuccess() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        }
    }

    private fun recomputeRemaining(latitude: Double, longitude: Double) {
        val nearest = NavigationProgressLogic.nearestOnRoute(
            latitude, longitude, _state.value.routeCoordinates
        ) ?: return
        nearestRouteDistanceMeters = nearest.distanceMeters
        val etaEpochMs = NavigationProgressLogic.etaEpochMs(
            remainingMeters = nearest.remainingMeters,
            totalRouteDistanceMeters = totalRouteDistanceMeters,
            totalTravelTimeSeconds = totalTravelTimeSeconds,
            nowMs = System.currentTimeMillis(),
            fallbackEtaEpochMs = _state.value.etaEpochMs,
        )
        _state.update {
            it.copy(distanceRemainingMeters = nearest.remainingMeters, etaEpochMs = etaEpochMs)
        }
    }

    private fun advanceStepIfNeeded(latitude: Double, longitude: Double) {
        val steps = _state.value.steps
        if (steps.isEmpty()) {
            _state.update { it.copy(distanceToNextManeuverMeters = it.distanceRemainingMeters) }
            return
        }

        val current = steps.getOrNull(_state.value.currentStepIndex)
        if (current != null) {
            val toEnd = NavigationProgressLogic.distanceToStepEnd(latitude, longitude, current)
            _state.update { it.copy(distanceToNextManeuverMeters = toEnd) }
            maybeAnnounceApproach(current, toEnd)
        }

        val index = NavigationProgressLogic.advancedStepIndex(
            latitude = latitude,
            longitude = longitude,
            steps = steps,
            currentIndex = _state.value.currentStepIndex,
        )

        if (index != _state.value.currentStepIndex) {
            approachedStepId = null
            val next = steps.getOrNull(index)
            val distanceToManeuver = next?.let {
                NavigationProgressLogic.distanceToStepEnd(latitude, longitude, it)
            } ?: _state.value.distanceRemainingMeters
            _state.update {
                it.copy(currentStepIndex = index, distanceToNextManeuverMeters = distanceToManeuver)
            }
            if (next != null) {
                AppLogger.i(
                    AppLogger.Category.UI,
                    "Advanced to step ${index + 1}/${steps.size}: ${next.instruction}"
                )
                announceStep(next)
            }
            lightHaptic()
        }
    }

    private fun maybeAnnounceApproach(step: NavStep, distanceMeters: Double) {
        if (!NavigationProgressLogic.shouldAnnounceApproach(
                distanceMeters,
                alreadyApproached = approachedStepId == step.id,
            )
        ) return
        approachedStepId = step.id
        val distance = NavigationState.formatDistance(distanceMeters)
        voice.speak("In $distance, ${step.instruction}")
    }

    private fun announceStep(step: NavStep) {
        if (announcedStepId == step.id) return
        announcedStepId = step.id
        voice.speak(step.instruction)
    }

    private fun lightHaptic() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25)
            }
        }
    }

    private fun checkOffRouteAndRecalculate(latitude: Double, longitude: Double) {
        val state = _state.value
        if (!state.hasDestination || !state.hasRoute || state.isRouting || state.isRecalculating) return

        if (NavigationProgressLogic.isOffRoute(nearestRouteDistanceMeters)) {
            _state.update { it.copy(isOffRoute = true) }
            val now = System.currentTimeMillis()
            if (now - lastRecalculateAtMs >= RECALCULATE_COOLDOWN_MS) {
                lastRecalculateAtMs = now
                computeRoute(isRecalculation = true)
            }
        } else if (state.isOffRoute &&
            NavigationProgressLogic.shouldClearOffRoute(nearestRouteDistanceMeters)
        ) {
            _state.update { it.copy(isOffRoute = false) }
        }
    }

    companion object {
        private const val RECALCULATE_COOLDOWN_MS = 12_000L
        private const val SEARCH_DEBOUNCE_MS = 350L
    }
}
