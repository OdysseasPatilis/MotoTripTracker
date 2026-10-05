package com.odys.mototriptracker.ui.tracker

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.odys.mototriptracker.application.NavigationState
import com.odys.mototriptracker.ui.tracker.DestinationSearchSheet
import com.odys.mototriptracker.ui.tracker.LiveRideMapView
import com.odys.mototriptracker.ui.tracker.RideTrackerUiState
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.odys.mototriptracker.application.TrafficCameraAlert
import com.odys.mototriptracker.application.TrafficCameraKind
import com.odys.mototriptracker.domain.TrafficCameraPackDownloadStatus
import com.odys.mototriptracker.application.PickedMapPlace
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.core.net.toUri
import com.odys.mototriptracker.ui.tracker.FuelSettingsSheet
import com.odys.mototriptracker.ui.tracker.PetrolStationsSheet
import com.odys.mototriptracker.ui.tracker.RouteWeatherSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.odys.mototriptracker.application.NavigationSearchResult
import com.odys.mototriptracker.application.DestinationHistoryEntry
import com.odys.mototriptracker.application.PetrolStationRecommendation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odys.mototriptracker.domain.GpsQuality
import com.odys.mototriptracker.domain.TripStats
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import com.odys.mototriptracker.ui.theme.LocalThemeStore
import com.odys.mototriptracker.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideTrackerScreen(
    uiState: RideTrackerUiState,
    isLocationEnabled: Boolean,
    onStartRide: () -> Unit,
    onStopRide: () -> Unit,
    onViewHistory: () -> Unit,
    onViewLeaderboard: () -> Unit = {},
    onPauseRide: () -> Unit,
    onShowDestinationSearch: () -> Unit,
    onDismissDestinationSearch: () -> Unit,
    onShowFuelSettings: () -> Unit,
    onDismissFuelSettings: () -> Unit,
    onToggleFuelBrand: (String) -> Unit,
    onToggleFuelOctane: (Int) -> Unit,
    onFillUpFuel: () -> Unit,
    onSaveFuelSettings: (Double?, Double?, Double?) -> Unit,
    onShowRouteWeather: () -> Unit,
    onDismissRouteWeather: () -> Unit,
    onShowPetrolStations: () -> Unit,
    onDismissPetrolStations: () -> Unit,
    onSelectPetrolStation: (PetrolStationRecommendation) -> Unit,
    onLoadPetrolDetails: (PetrolStationRecommendation) -> Unit,
    onClearPetrolDetails: () -> Unit,
    isPreferredBrand: (String?) -> Boolean,
    brandCatalog: List<String>,
    onNavigationQueryChange: (String) -> Unit,
    onSelectNavigationResult: (NavigationSearchResult) -> Unit,
    historyDestinations: List<DestinationHistoryEntry> = emptyList(),
    onSelectHistoryDestination: (DestinationHistoryEntry) -> Unit = {},
    onRemoveHistoryDestination: (String) -> Unit = {},
    onClearNavigation: () -> Unit,
    onConfirmStartNavigation: () -> Unit = {},
    onCancelNavigationPreview: () -> Unit = {},
    onSelectPreviewRoute: (String) -> Unit = {},
    onOpenNavigationInMaps: () -> Unit,
    onToggleNavigationVoice: () -> Unit,
    onDismissTimingResult: () -> Unit = {},
    onVisibleMapRegionChanged: (Double, Double, Double, Double, Boolean) -> Unit = { _, _, _, _, _ -> },
    onMapPoiClick: (String, String, Double, Double) -> Unit = { _, _, _, _ -> },
    onDismissMapPlace: () -> Unit = {},
    onGoToMapPlace: () -> Unit = {},
    onMapRecenter: () -> Unit = {},
) {
    val stats = uiState.stats
    val isTracking = uiState.isTracking
    val isPaused = uiState.isPaused
    val navigation = uiState.navigation

    val palette = LocalAppPalette.current
    val themeStore = LocalThemeStore.current
    val themeMode by themeStore.mode.collectAsStateWithLifecycle()

    val effectiveSpeedLimitKmh = themeStore.effectiveLimitKmh(stats.roadSpeedLimitKmh).toFloat()
    val isAutoLimit = themeStore.hasAutoLimit(stats.roadSpeedLimitKmh)
    // Sign / dial warn as soon as you exceed the limit; full-screen flash only at +10 km/h.
    val isOverLimit = isTracking && !isPaused && stats.speed > effectiveSpeedLimitKmh
    val shouldFlashScreen = isTracking && !isPaused &&
        stats.speed >= effectiveSpeedLimitKmh + SCREEN_FLASH_TOLERANCE_KMH
    val mapHud = navigation.isNavigating
    val showsMainDial =
        !mapHud && !navigation.isPreviewing && uiState.selectedMapPlace == null
    val dialSpeedKmh = if (mapHud && !isTracking) uiState.lastSpeedMps * 3.6f else stats.speed
    val dialOverLimit = !isPaused && dialSpeedKmh > effectiveSpeedLimitKmh
    val flashPhase = rememberSpeedLimitFlashPhase(dialOverLimit || isOverLimit)
    val isRiding = isTracking && !isPaused

    var optionsExpanded by remember { mutableStateOf(false) }
    var timingBanner by remember { mutableStateOf<String?>(null) }
    var confirmEndRide by remember { mutableStateOf(false) }
    var showMoreStats by remember { mutableStateOf(false) }

    LaunchedEffect(navigation.lastTimingResult) {
        val result = navigation.lastTimingResult ?: return@LaunchedEffect
        timingBanner = result.summaryLine
        delay(8_000)
        if (timingBanner == result.summaryLine) {
            timingBanner = null
            onDismissTimingResult()
        }
    }

    KeepScreenOn()

    if (uiState.showDestinationSearch) {
        DestinationSearchSheet(
            query = navigation.searchQuery,
            results = navigation.searchResults,
            history = historyDestinations,
            isSearching = navigation.isSearching,
            searchError = navigation.searchError,
            onQueryChange = onNavigationQueryChange,
            onSelectResult = onSelectNavigationResult,
            onSelectHistory = onSelectHistoryDestination,
            onRemoveHistory = onRemoveHistoryDestination,
            onDismiss = onDismissDestinationSearch
        )
    }
    if (uiState.showFuelSettings) {
        FuelSettingsSheet(
            tankCapacity = uiState.tankCapacityLiters,
            fuelRemaining = uiState.fuelRemainingLiters,
            consumption = uiState.fuelConsumption,
            preferredBrands = uiState.preferredBrands,
            preferredOctanes = uiState.preferredOctanes,
            brandCatalog = brandCatalog,
            onToggleBrand = onToggleFuelBrand,
            onToggleOctane = onToggleFuelOctane,
            onFillUp = onFillUpFuel,
            onSave = onSaveFuelSettings,
            onDismiss = onDismissFuelSettings,
        )
    }
    if (uiState.showRouteWeather) {
        RouteWeatherSheet(
            weather = uiState.weather,
            onDismiss = onDismissRouteWeather
        )
    }
    if (confirmEndRide) {
        EndRideConfirmSheet(
            onSaveRide = {
                confirmEndRide = false
                onStopRide()
            },
            onKeepRiding = { confirmEndRide = false },
        )
    }
    if (showMoreStats) {
        RideStatsSheet(
            stats = stats,
            onDismiss = { showMoreStats = false },
        )
    }
    if (uiState.showPetrolStations) {
        PetrolStationsSheet(
            stations = uiState.petrolStations,
            plan = uiState.petrolPlan,
            isLoading = uiState.petrolLoading,
            isPreferredBrand = isPreferredBrand,
            preferredOctanes = uiState.preferredOctanes,
            googleDetails = uiState.petrolDetails,
            googleDetailsLoading = uiState.petrolDetailsLoading,
            onGo = onSelectPetrolStation,
            onRequestDetails = onLoadPetrolDetails,
            onClearDetails = onClearPetrolDetails,
            onDismiss = onDismissPetrolStations
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                LiveRideMapView(
                    traveledRoute = uiState.routeCoordinates,
                    plannedRoute = navigation.routeCoordinates,
                    previewRoutes = navigation.previewRoutes,
                    selectedPreviewRouteId = navigation.selectedRouteId,
                    isPreviewing = navigation.isPreviewing,
                    destinationLatitude = navigation.destinationLatitude,
                    destinationLongitude = navigation.destinationLongitude,
                    isRiding = isRiding,
                    isNavigating = navigation.isNavigating,
                    isRecalculating = navigation.isRecalculating,
                    distanceToNextManeuverMeters = navigation.distanceToNextManeuverMeters,
                    userLatitude = uiState.lastLatitude,
                    userLongitude = uiState.lastLongitude,
                    userBearing = uiState.lastBearing,
                    userSpeedMps = uiState.lastSpeedMps,
                    trafficCameras = uiState.nearbyTrafficCameras,
                    showTrafficCameras = true,
                    hasSelectedPlace = uiState.selectedMapPlace != null,
                    onVisibleRegionChanged = onVisibleMapRegionChanged,
                    onPoiClick = onMapPoiClick,
                    onRecenter = onMapRecenter,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Flush to the left edge of the map.
                        Row(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 0.dp,
                                        bottomStart = 0.dp,
                                        topEnd = 14.dp,
                                        bottomEnd = 14.dp
                                    )
                                )
                                .background(palette.bgPanel.copy(alpha = 0.88f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GpsSignalIndicator(
                                quality = stats.gpsQuality,
                                accuracyMeters = stats.gpsAccuracyMeters,
                                palette = palette
                            )
                            BatteryIndicator(rememberBatteryLevel(), palette)
                        }

                        if (!isRiding) {
                            // Flush to the right edge of the map.
                            Box {
                                IconButton(
                                    onClick = { optionsExpanded = true },
                                    modifier = Modifier
                                        .size(if (mapHud) RideTouchTarget else 42.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 14.dp,
                                                bottomStart = 14.dp,
                                                topEnd = 0.dp,
                                                bottomEnd = 0.dp
                                            )
                                        )
                                        .background(palette.bgPanel.copy(alpha = 0.88f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreHoriz,
                                        contentDescription = "Options",
                                        tint = palette.textPrimary
                                    )
                                }
                                DropdownMenu(
                                    expanded = optionsExpanded,
                                    onDismissRequest = { optionsExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Ride History") },
                                        onClick = {
                                            optionsExpanded = false
                                            onViewHistory()
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Leaderboard") },
                                        onClick = {
                                            optionsExpanded = false
                                            onViewLeaderboard()
                                        },
                                        leadingIcon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fuel & Range") },
                                        onClick = {
                                            optionsExpanded = false
                                            onShowFuelSettings()
                                        },
                                        leadingIcon = { Icon(Icons.Filled.LocalGasStation, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Find Petrol") },
                                        onClick = {
                                            optionsExpanded = false
                                            onShowPetrolStations()
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("${themeMode.toggled().label} Mode") },
                                        onClick = {
                                            optionsExpanded = false
                                            themeStore.toggleTheme()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (themeMode == ThemeMode.DARK) Icons.Filled.LightMode
                                                else Icons.Filled.DarkMode,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (navigation.isNavigating) {
                        CompactTurnChip(
                            navigation = navigation,
                            palette = palette,
                            onToggleVoice = onToggleNavigationVoice,
                            onOpenInMaps = onOpenNavigationInMaps,
                            onClear = onClearNavigation,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                        navigation.trafficHintText?.let { hint ->
                            Text(
                                hint,
                                color = palette.routeAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(palette.bgPanel.copy(alpha = 0.82f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    uiState.trafficCameraAlert?.let { alert ->
                        TrafficCameraBanner(
                            alert = alert,
                            palette = palette,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                    when (val status = uiState.trafficCameraDownloadStatus) {
                        TrafficCameraPackDownloadStatus.Idle -> Unit
                        is TrafficCameraPackDownloadStatus.Downloading -> {
                            TrafficCameraStatusBanner(
                                text = "Downloading cameras for ${status.countryName ?: status.countryCode}…",
                                palette = palette,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                        }
                        is TrafficCameraPackDownloadStatus.Failed -> {
                            TrafficCameraStatusBanner(
                                text = status.message,
                                palette = palette,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                        }
                    }
                }

                if (mapHud) {
                    val weatherEnabled = uiState.weather.hasData || navigation.hasRoute
                    RideUtilityRail(
                        isLowFuel = uiState.isLowFuel,
                        palette = palette,
                        onShowFuelSettings = onShowFuelSettings,
                        onShowPetrolStations = onShowPetrolStations,
                        onShowWeather = onShowRouteWeather,
                        onShowDestination = onShowDestinationSearch,
                        showDestination = true,
                        weatherEnabled = weatherEnabled,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp, bottom = 120.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (mapHud) {
                        val hudDialMax = maxOf(180f, dialSpeedKmh, effectiveSpeedLimitKmh)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SpeedometerArc(
                                speedKmh = dialSpeedKmh,
                                maxSpeedKmh = hudDialMax,
                                speedLimitKmh = effectiveSpeedLimitKmh,
                                isAutoLimit = isAutoLimit,
                                flashPhase = flashPhase,
                                palette = palette,
                                dialSize = MapHudDialSize,
                                floating = true,
                                speedReadoutColor = palette.neonBlue,
                            )
                            if (isTracking) {
                                NavGlanceStats(
                                    distanceKm = stats.distanceKm,
                                    tripTimeSeconds = stats.tripTime,
                                    onMoreStats = { showMoreStats = true },
                                    palette = palette,
                                    fuelRangeSummary = uiState.fuelRangeSummary,
                                    isLowFuel = uiState.isLowFuel,
                                )
                            }
                        }
                    }

                    if (showsMainDial) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(20.dp))
                                .background(palette.bgCard.copy(alpha = 0.72f))
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SpeedometerArc(
                                speedKmh = dialSpeedKmh,
                                maxSpeedKmh = maxOf(stats.maxSpeed, 260f),
                                speedLimitKmh = effectiveSpeedLimitKmh,
                                isAutoLimit = isAutoLimit,
                                flashPhase = flashPhase,
                                palette = palette,
                                dialSize = 260.dp,
                                floating = true,
                            )
                            GForceBar(
                                value = stats.currentGForce,
                                maxValue = maxOf(stats.maxGForce, 0.01f),
                                palette = palette
                            )
                            RideStatsGrid(
                                stats = stats,
                                palette = palette,
                            )
                        }
                    }

                    if (!mapHud) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.selectedMapPlace?.let { place ->
                                if (!navigation.isPreviewing && !navigation.isNavigating) {
                                    MapPlaceGoCard(
                                        place = place,
                                        palette = palette,
                                        onDismiss = onDismissMapPlace,
                                        onGo = onGoToMapPlace,
                                    )
                                }
                            }
                            when {
                                navigation.isPreviewing -> {
                                    RoutePreviewCard(
                                        navigation = navigation,
                                        palette = palette,
                                        onSelectRoute = onSelectPreviewRoute,
                                        onStart = onConfirmStartNavigation,
                                        onCancel = onCancelNavigationPreview,
                                    )
                                }
                                else -> {
                                    timingBanner?.let { banner ->
                                        TimingResultBanner(
                                            text = banner,
                                            palette = palette,
                                            onDismiss = {
                                                timingBanner = null
                                                onDismissTimingResult()
                                            }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            onClick = onShowDestinationSearch,
                                            shape = RoundedCornerShape(999.dp),
                                            color = palette.bgPanel.copy(alpha = 0.82f),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Search,
                                                    contentDescription = null,
                                                    tint = palette.textSecondary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Text(
                                                    "Set destination",
                                                    color = palette.textSecondary,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = onShowPetrolStations,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(palette.bgPanel.copy(alpha = 0.82f))
                                        ) {
                                            Icon(
                                                Icons.Filled.LocalGasStation,
                                                contentDescription = "Nearest petrol",
                                                tint = if (uiState.isLowFuel) palette.neonRed else palette.neonGreen
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = palette.bgPanel.copy(alpha = 0.82f),
                                        modifier = Modifier.wrapContentWidth()
                                    ) {
                                        Text(
                                            text = buildString {
                                                append(uiState.fuelRangeSummary)
                                                if (uiState.isLowFuel) append(" · Low")
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            color = if (uiState.isLowFuel) palette.neonRed else palette.textSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TrackerBottomBar(
                        isTracking = isTracking,
                        isPaused = isPaused,
                        isLocationEnabled = isLocationEnabled,
                        palette = palette,
                        onPauseRide = onPauseRide,
                        onStopRide = { confirmEndRide = true },
                        onStartRide = onStartRide
                    )
                }
            }
        }

        OverLimitScreenFlash(
            isActive = shouldFlashScreen,
            flashPhase = flashPhase,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = uiState.discardBanner != null || uiState.petrolMessage != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            uiState.discardBanner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = palette.bgPanel.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = banner,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } ?: uiState.petrolMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = palette.bgPanel.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
