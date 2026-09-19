package com.odys.mototriptracker.ui.tracker

import com.odys.mototriptracker.application.PickedMapPlace
import com.odys.mototriptracker.application.RideTrackerFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns map-POI pick / resolve / navigate-to-place for the ride tracker. */
class MapPlaceCoordinator @Inject constructor(
    private val facade: RideTrackerFacade,
) {
    private val _selected = MutableStateFlow<PickedMapPlace?>(null)
    private var resolveJob: Job? = null
    private var generation = 0

    val selected: StateFlow<PickedMapPlace?> = _selected.asStateFlow()

    fun onPoiClick(
        scope: CoroutineScope,
        placeId: String,
        name: String,
        latitude: Double,
        longitude: Double,
    ) {
        generation += 1
        val gen = generation
        _selected.value = PickedMapPlace(
            name = name.ifBlank { "Selected place" },
            latitude = latitude,
            longitude = longitude,
            placeId = placeId,
            isResolving = true,
        )
        resolveJob?.cancel()
        resolveJob = scope.launch {
            val resolved = facade.resolveMapPlace(
                placeId = placeId,
                fallbackName = name,
                latitude = latitude,
                longitude = longitude,
            )
            if (gen != generation) return@launch
            _selected.value = resolved.copy(isResolving = false)
        }
    }

    fun dismiss() {
        generation += 1
        resolveJob?.cancel()
        resolveJob = null
        _selected.value = null
    }

    fun goToSelected() {
        val place = _selected.value ?: return
        if (place.isResolving) return
        dismiss()
        facade.setDestination(
            latitude = place.latitude,
            longitude = place.longitude,
            name = place.name,
            subtitle = place.address,
        )
    }
}
