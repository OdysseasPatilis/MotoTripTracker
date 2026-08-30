package com.odys.mototriptracker.ui.dashboard

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.odys.mototriptracker.data.navigation.NavigationState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.odys.mototriptracker.data.fuel.FuelService
import com.odys.mototriptracker.domain.TwistinessCalculator
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.odys.mototriptracker.data.navigation.NavigationSearchResult
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.alpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideTrackerScreen(
    uiState: RideTrackerUiState,
    isLocationEnabled: Boolean,
    fuelService: FuelService,
    onStartRide: () -> Unit,
    onStopRide: () -> Unit,
    onViewHistory: () -> Unit,
    onViewLeaderboard: () -> Unit = {},
    onPauseRide: () -> Unit,
    onShowDestinationSearch: () -> Unit,
    onDismissDestinationSearch: () -> Unit,
    onShowFuelSettings: () -> Unit,
    onDismissFuelSettings: () -> Unit,
    onShowRouteWeather: () -> Unit,
    onDismissRouteWeather: () -> Unit,
    onShowPetrolStations: () -> Unit,
    onDismissPetrolStations: () -> Unit,
    onSelectPetrolStation: (com.odys.mototriptracker.data.petrol.PetrolStationRecommendation) -> Unit,
    onLoadPetrolDetails: (com.odys.mototriptracker.data.petrol.PetrolStationRecommendation) -> Unit,
    onClearPetrolDetails: () -> Unit,
    petrolPreferences: com.odys.mototriptracker.data.petrol.PetrolPreferences,
    onNavigationQueryChange: (String) -> Unit,
    onSelectNavigationResult: (NavigationSearchResult) -> Unit,
    onClearNavigation: () -> Unit,
    onOpenNavigationInMaps: () -> Unit,
    onToggleNavigationVoice: () -> Unit
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
    val isOverLimit = isTracking && !isPaused && stats.speed > effectiveSpeedLimitKmh
    val flashPhase = rememberSpeedLimitFlashPhase(isOverLimit)
    val isRiding = isTracking && !isPaused

    var optionsExpanded by remember { mutableStateOf(false) }

    KeepScreenOn()

    if (uiState.showDestinationSearch) {
        DestinationSearchSheet(
            query = navigation.searchQuery,
            results = navigation.searchResults,
            isSearching = navigation.isSearching,
            searchError = navigation.searchError,
            onQueryChange = onNavigationQueryChange,
            onSelectResult = onSelectNavigationResult,
            onDismiss = onDismissDestinationSearch
        )
    }
    if (uiState.showFuelSettings) {
        FuelSettingsSheet(
            fuelService = fuelService,
            petrolPreferences = petrolPreferences,
            tankCapacity = uiState.tankCapacityLiters,
            fuelRemaining = uiState.fuelRemainingLiters,
            consumption = uiState.fuelConsumption,
            onDismiss = onDismissFuelSettings
        )
    }
    if (uiState.showRouteWeather) {
        RouteWeatherSheet(
            weather = uiState.weather,
            onDismiss = onDismissRouteWeather
        )
    }
    if (uiState.showPetrolStations) {
        PetrolStationsSheet(
            stations = uiState.petrolStations,
            plan = uiState.petrolPlan,
            isLoading = uiState.petrolLoading,
            preferences = petrolPreferences,
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
            containerColor = palette.bgDeep,
            bottomBar = {
                TrackerBottomBar(
                    isTracking = isTracking,
                    isPaused = isPaused,
                    isLocationEnabled = isLocationEnabled,
                    palette = palette,
                    onPauseRide = onPauseRide,
                    onStopRide = onStopRide,
                    onStartRide = onStartRide
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f)
                ) {
                    LiveRideMapView(
                        traveledRoute = uiState.routeCoordinates,
                        plannedRoute = navigation.routeCoordinates,
                        destinationLatitude = navigation.destinationLatitude,
                        destinationLongitude = navigation.destinationLongitude,
                        isRiding = isRiding,
                        userLatitude = uiState.lastLatitude,
                        userLongitude = uiState.lastLongitude,
                        userBearing = uiState.lastBearing,
                        userSpeedMps = uiState.lastSpeedMps,
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
                                            .size(42.dp)
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
                                            leadingIcon = { Icon(Icons.Filled.List, contentDescription = null) }
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

                        if (navigation.hasDestination) {
                            ManeuverBanner(
                                navigation = navigation,
                                palette = palette,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (navigation.hasDestination) {
                            ActiveRouteChip(
                                navigation = navigation,
                                palette = palette,
                                onOpenInMaps = onOpenNavigationInMaps,
                                onClear = onClearNavigation,
                                onToggleVoice = onToggleNavigationVoice,
                                onShowWeather = if (uiState.weather.hasData || navigation.hasRoute) {
                                    onShowRouteWeather
                                } else {
                                    null
                                }
                            )
                        } else {
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

                Column(
                    modifier = Modifier
                        .weight(0.54f)
                        .verticalScroll(rememberScrollState())
                        .background(palette.bgDeep)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(palette.bgCard)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SpeedometerArc(
                                speedKmh = stats.speed,
                                maxSpeedKmh = maxOf(stats.maxSpeed, 260f),
                                speedLimitKmh = effectiveSpeedLimitKmh,
                                isAutoLimit = isAutoLimit,
                                flashPhase = flashPhase,
                                palette = palette
                            )
                            GForceBar(
                                value = stats.currentGForce,
                                maxValue = maxOf(stats.maxGForce, 0.01f),
                                palette = palette
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("DISTANCE", "${String.format("%.1f km", stats.distanceKm)}", Modifier.weight(1f), palette = palette)
                        StatCard("TOTAL TIME", formatSecondsToTime(stats.tripTime), Modifier.weight(1f), palette = palette)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("MOVING", formatSecondsToTime(stats.movingTime), Modifier.weight(1f), valueColor = palette.neonGreen, palette = palette)
                        StatCard("STOPPED", formatSecondsToTime(stats.stoppedTime), Modifier.weight(1f), valueColor = palette.neonRed, palette = palette)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("AVG SPEED", "${stats.avgSpeed.toInt()} km/h", Modifier.weight(1f), palette = palette)
                        StatCard("MAX SPEED", "${stats.maxSpeed.toInt()} km/h", Modifier.weight(1f), palette = palette)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("ELEVATION", "${stats.totalElevationGain.toInt()} m", Modifier.weight(1f), palette = palette)
                        StatCard("MAX G", String.format("%.2f G", stats.maxGForce), Modifier.weight(1f), valueColor = palette.neonBlue, palette = palette)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            "TWISTINESS",
                            TwistinessCalculator.formattedScore(
                                TwistinessCalculator.score(
                                    stats.cornerCount,
                                    stats.distanceKm.toDouble(),
                                    stats.maxLateralGForce.toDouble()
                                )
                            ),
                            Modifier.weight(1f),
                            valueColor = palette.neonBlue,
                            palette = palette
                        )
                        StatCard("CORNERS", "${stats.cornerCount}", Modifier.weight(1f), palette = palette)
                    }
                }
            }
        }

        OverLimitScreenFlash(
            isOverLimit = isOverLimit,
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

@Composable
private fun TrackerBottomBar(
    isTracking: Boolean,
    isPaused: Boolean,
    isLocationEnabled: Boolean,
    palette: AppPalette,
    onPauseRide: () -> Unit,
    onStopRide: () -> Unit,
    onStartRide: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bgDeep)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isTracking) {
                Button(
                    onClick = onPauseRide,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.bgPanel),
                    border = BorderStroke(1.dp, palette.pauseBorder)
                ) {
                    Text(
                        if (isPaused) "Resume" else "Pause",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = onStopRide,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.stopRed)
                ) {
                    Text("Stop", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStartRide,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLocationEnabled) palette.neonGreen else palette.startButtonDisabledBg
                    ),
                    enabled = isLocationEnabled
                ) {
                    Text(
                        if (isLocationEnabled) "Start Ride" else "Enable GPS to Start",
                        color = if (isLocationEnabled) palette.bgDeep else palette.startButtonDisabledText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveRouteChip(
    navigation: NavigationState,
    palette: AppPalette,
    onOpenInMaps: () -> Unit,
    onClear: () -> Unit,
    onToggleVoice: () -> Unit,
    onShowWeather: (() -> Unit)? = null
) {
    val summary = when {
        navigation.isRouting -> "Routing…"
        navigation.isRecalculating -> "Recalculating…"
        else -> navigation.summaryText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(palette.bgPanel.copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            summary,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onShowWeather != null && navigation.hasRoute && !navigation.isRouting) {
            IconButton(onClick = onShowWeather, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.WbSunny,
                    contentDescription = "Route weather",
                    tint = palette.neonBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        IconButton(onClick = onToggleVoice, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (navigation.isVoiceEnabled) {
                    Icons.AutoMirrored.Filled.VolumeUp
                } else {
                    Icons.AutoMirrored.Filled.VolumeOff
                },
                contentDescription = if (navigation.isVoiceEnabled) {
                    "Mute voice guidance"
                } else {
                    "Enable voice guidance"
                },
                tint = if (navigation.isVoiceEnabled) palette.neonGreen else palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onOpenInMaps, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Navigation,
                contentDescription = "Open in Google Maps",
                tint = palette.neonGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear destination",
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ManeuverBanner(
    navigation: NavigationState,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    val accent = if (navigation.isOffRoute || navigation.isRecalculating) palette.routeAmber else palette.neonBlue
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = maneuverIcon(navigation),
                contentDescription = null,
                tint = palette.bgDeep,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            when {
                navigation.isRecalculating -> {
                    Text(
                        "Recalculating…",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                navigation.isOffRoute -> {
                    Text(
                        "Off route",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                navigation.currentStep != null -> {
                    Text(
                        NavigationState.formatDistance(navigation.distanceToNextManeuverMeters),
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        navigation.currentStep.instruction,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                navigation.isRouting -> {
                    Text(
                        "Calculating route…",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                else -> {
                    Text(
                        navigation.destinationName ?: "Destination",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun maneuverIcon(navigation: NavigationState): ImageVector {
    if (navigation.isRecalculating || navigation.isOffRoute) return Icons.Filled.Sync
    val text = navigation.currentStep?.instruction?.lowercase().orEmpty()
    return when {
        "u-turn" in text || "u turn" in text -> Icons.Filled.UTurnLeft
        "roundabout" in text || "rotary" in text -> Icons.Filled.Sync
        "keep left" in text || "bear left" in text -> Icons.Filled.NorthWest
        "keep right" in text || "bear right" in text -> Icons.Filled.NorthEast
        "left" in text -> Icons.Filled.TurnLeft
        "right" in text -> Icons.Filled.TurnRight
        "destination" in text || "arrive" in text -> Icons.Filled.Flag
        "straight" in text || "continue" in text -> Icons.Filled.Straight
        else -> Icons.Filled.Navigation
    }
}

@Composable
private fun TrackingStatusRow(
    isTracking: Boolean,
    isPaused: Boolean,
    gpsQuality: GpsQuality,
    gpsAccuracyMeters: Float?,
    palette: AppPalette
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isTracking) {
            val pulse = rememberInfiniteTransition(label = "recPulse")
            val alpha by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "recAlpha"
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(if (isPaused) 1f else alpha)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isPaused) palette.textMuted else palette.stopRed)
                )
                Text(
                    text = if (isPaused) "PAUSED" else "RECORDING",
                    color = if (isPaused) palette.textMuted else palette.stopRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        GpsSignalIndicator(
            quality = gpsQuality,
            accuracyMeters = gpsAccuracyMeters,
            palette = palette
        )
    }
}

@Composable
private fun GpsSignalIndicator(
    quality: GpsQuality,
    accuracyMeters: Float?,
    palette: AppPalette
) {
    val tint = when (quality) {
        GpsQuality.EXCELLENT, GpsQuality.GOOD -> palette.neonGreen
        GpsQuality.FAIR -> palette.routeAmber
        GpsQuality.POOR -> palette.neonRed
        GpsQuality.UNKNOWN -> palette.textMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GpsBarsIcon(filledBars = quality.barCount, tint = tint)
        Text(
            text = if (accuracyMeters != null && accuracyMeters > 0f) {
                "±${accuracyMeters.toInt()}m"
            } else {
                "GPS"
            },
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GpsBarsIcon(filledBars: Int, tint: Color) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        modifier = Modifier.height(13.dp)
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((5 + index * 2.5).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (index < filledBars) tint else tint.copy(alpha = 0.25f))
            )
        }
    }
}
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    palette: AppPalette = LocalAppPalette.current
) {
    val resolvedValueColor = valueColor ?: palette.textPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.bgCard)
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(20.dp))
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = palette.textMuted, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = resolvedValueColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SpeedometerArc(
    speedKmh: Float,
    maxSpeedKmh: Float = 260f,
    speedLimitKmh: Float = 50f,
    isAutoLimit: Boolean = false,
    flashPhase: SpeedLimitFlashPhase = rememberSpeedLimitFlashPhase(speedKmh > speedLimitKmh),
    palette: AppPalette = LocalAppPalette.current
) {
    val isOverLimit = speedKmh > speedLimitKmh
    val limitPercent = (speedLimitKmh / maxSpeedKmh).coerceIn(0f, 1f)
    val speedPercent = (speedKmh / maxSpeedKmh).coerceIn(0f, 1f)
    val startAngle = 135f
    val totalSweep = 270f

    val speedNumColor by animateColorAsState(
        targetValue = if (isOverLimit) palette.stopRed else palette.textPrimary,
        animationSpec = tween(300),
        label = "speedNum"
    )

    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 20.dp.toPx()
            val radius = (minOf(size.width, size.height) - padding * 2) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val trackStyle = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val mainWidth = 9.dp.toPx()
            val mainStyle = Stroke(width = mainWidth, cap = StrokeCap.Round)

            drawArc(
                color = palette.arcTrack,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = trackStyle
            )

            if (speedPercent > 0f) {
                val accent = if (isOverLimit) palette.stopRed else palette.neonGreen
                val progressSweep = totalSweep * speedPercent

                drawIntoCanvas { canvas ->
                    val glowPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = accent.copy(alpha = 0.55f).toArgb()
                        strokeWidth = 16.dp.toPx()
                        style = android.graphics.Paint.Style.STROKE
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                    }
                    val rect = android.graphics.RectF(
                        center.x - radius,
                        center.y - radius,
                        center.x + radius,
                        center.y + radius
                    )
                    canvas.nativeCanvas.drawArc(rect, startAngle, progressSweep, false, glowPaint)
                }

                if (!isOverLimit) {
                    drawArc(
                        color = palette.neonGreen,
                        startAngle = startAngle,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = mainStyle
                    )
                } else {
                    drawArc(
                        color = palette.neonGreen.copy(alpha = 0.6f),
                        startAngle = startAngle,
                        sweepAngle = totalSweep * limitPercent,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = mainStyle
                    )
                    drawArc(
                        color = palette.stopRed,
                        startAngle = startAngle + totalSweep * limitPercent,
                        sweepAngle = totalSweep * (speedPercent - limitPercent),
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = mainStyle
                    )
                }
            }

            val limitAngleRad = Math.toRadians((startAngle + totalSweep * limitPercent).toDouble())
            val notchInner = radius - 9.dp.toPx()
            val notchOuter = radius + 9.dp.toPx()
            drawLine(
                color = palette.textPrimary.copy(alpha = 0.9f),
                start = Offset(
                    center.x + notchInner * cos(limitAngleRad).toFloat(),
                    center.y + notchInner * sin(limitAngleRad).toFloat()
                ),
                end = Offset(
                    center.x + notchOuter * cos(limitAngleRad).toFloat(),
                    center.y + notchOuter * sin(limitAngleRad).toFloat()
                ),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpeedLimitSign(
                limitKmh = speedLimitKmh.toInt(),
                isOverLimit = isOverLimit,
                isLive = isAutoLimit,
                flashPhase = flashPhase,
                palette = palette
            )

            val animatedSpeed by animateIntAsState(
                targetValue = speedKmh.toInt(),
                animationSpec = tween(1000, easing = FastOutSlowInEasing),
                label = "SpeedAnimation"
            )
            Text(
                text = animatedSpeed.toString(),
                color = speedNumColor,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 64.sp
            )
            Text(
                "km/h",
                color = palette.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
@Composable
fun GForceBar(
    value: Float,
    maxValue: Float,
    palette: AppPalette = LocalAppPalette.current
) {
    val fillFraction = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(String.format("%.2f G", value), color = palette.textPrimary,
            fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.arcTrack)
        ) {
            if (fillFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.startGradient)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(palette.gForceTick)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("MAX: ${String.format("%.2f", maxValue)} G",
            color = palette.textMuted, fontSize = 11.sp)
    }
}

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window

        // 1. Add the flag when this Composable enters the screen
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            // 2. Safely clear the flag when the user navigates away
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableStateOf(100) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val raw  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (raw >= 0 && scale > 0) level = (raw * 100 / scale)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return level
}

@Composable
fun BatteryIndicator(level: Int, palette: AppPalette = LocalAppPalette.current) {
    val color = when {
        level <= 20 -> palette.neonRed
        level <= 50 -> palette.routeAmber
        else        -> palette.neonGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(modifier = Modifier.size(width = 24.dp, height = 13.dp)) {
            val bodyW = size.width - 3.dp.toPx()
            val bodyH = size.height
            val termW = 3.dp.toPx()
            val termH = 5.dp.toPx()
            drawRoundRect(
                color = palette.batteryOutline,
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawRoundRect(
                color = palette.batteryOutline,
                topLeft = Offset(bodyW, (bodyH - termH) / 2),
                size = Size(termW, termH),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            val fillW = ((bodyW - 4.dp.toPx()) * level / 100f).coerceAtLeast(0f)
            drawRoundRect(
                color = color,
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(fillW, bodyH - 4.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        Text("$level%", color = palette.batteryLabel, fontSize = 11.sp)
    }
}
fun formatSecondsToTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatTimestampToDate(timeMillis: Long): String {
    if (timeMillis == 0L) return "--"
    val formatter = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
    return formatter.format(Date(timeMillis))
}