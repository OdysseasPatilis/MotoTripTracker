package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.application.NavigationState
import com.odys.mototriptracker.domain.TripStats
import com.odys.mototriptracker.ui.components.formatSecondsToTime
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import java.util.Locale

/** Glove-friendly minimum for map and nav controls. */
internal val RideTouchTarget = 48.dp

/** Turn chip stays a single row so the road ahead stays visible. */
internal val TurnChipHeight = 56.dp

/** Floating nav dial — large enough to read, small enough to leave the road. */
internal val MapHudDialSize = 172.dp

@Composable
internal fun CompactTurnChip(
    navigation: NavigationState,
    palette: AppPalette,
    onToggleVoice: () -> Unit,
    onOpenInMaps: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (navigation.isOffRoute || navigation.isRecalculating) {
        palette.routeAmber
    } else {
        palette.neonBlue
    }
    val distance = when {
        navigation.isRecalculating -> "Recalculating"
        navigation.isOffRoute -> "Off route"
        navigation.isRouting -> "Routing…"
        navigation.currentStep != null -> NavigationState.formatDistance(navigation.distanceToNextManeuverMeters)
        else -> navigation.destinationName ?: "Destination"
    }
    val maneuver = when {
        navigation.isRecalculating || navigation.isOffRoute || navigation.isRouting -> navigation.destinationName
        navigation.currentStep != null -> navigation.currentStep.instruction
        else -> null
    }
    val routeSummary = navigation.summaryText.takeIf {
        navigation.hasRoute && !navigation.isRecalculating && !navigation.isRouting
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TurnChipHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.bgPanel.copy(alpha = 0.92f))
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = maneuverIcon(navigation),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = distance,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                if (!maneuver.isNullOrBlank()) {
                    Text(
                        text = maneuver,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp,
                    )
                }
            }
            HudIconButton(
                onClick = onToggleVoice,
                icon = if (navigation.isVoiceEnabled) {
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
            )
            HudIconButton(
                onClick = onOpenInMaps,
                icon = Icons.Filled.Navigation,
                contentDescription = "Open in Google Maps",
                tint = palette.neonGreen,
            )
            HudIconButton(
                onClick = onClear,
                icon = Icons.Filled.Close,
                contentDescription = "End navigation",
                tint = palette.textSecondary,
            )
        }
        if (routeSummary != null) {
            Text(
                text = routeSummary,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.bgPanel.copy(alpha = 0.82f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun RideUtilityRail(
    isLowFuel: Boolean,
    palette: AppPalette,
    onShowFuelSettings: () -> Unit,
    onShowPetrolStations: () -> Unit,
    onShowWeather: () -> Unit,
    onShowDestination: () -> Unit,
    modifier: Modifier = Modifier,
    showDestination: Boolean = true,
    weatherEnabled: Boolean = true,
) {
    var fuelMenu by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            HudRailButton(
                icon = Icons.Filled.LocalGasStation,
                label = "Fuel",
                contentDescription = if (isLowFuel) "Fuel, low" else "Fuel",
                tint = if (isLowFuel) palette.neonRed else palette.neonGreen,
                onClick = { fuelMenu = true },
                palette = palette,
            )
            DropdownMenu(
                expanded = fuelMenu,
                onDismissRequest = { fuelMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Fuel & range") },
                    onClick = {
                        fuelMenu = false
                        onShowFuelSettings()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.LocalGasStation, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Find petrol") },
                    onClick = {
                        fuelMenu = false
                        onShowPetrolStations()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Place, contentDescription = null)
                    },
                )
            }
        }
        HudRailButton(
            icon = Icons.Filled.WbSunny,
            label = "Weather",
            contentDescription = if (weatherEnabled) {
                "Route weather"
            } else {
                "Route weather unavailable"
            },
            tint = if (weatherEnabled) palette.neonBlue else palette.textSecondary,
            onClick = { if (weatherEnabled) onShowWeather() },
            palette = palette,
        )
        if (showDestination) {
            HudRailButton(
                icon = Icons.Filled.Place,
                label = "Dest",
                contentDescription = "Destination",
                tint = palette.neonBlue,
                onClick = onShowDestination,
                palette = palette,
            )
        }
    }
}

@Composable
internal fun NavGlanceStats(
    distanceKm: Float,
    tripTimeSeconds: Long,
    onMoreStats: () -> Unit,
    palette: AppPalette,
    modifier: Modifier = Modifier,
    fuelRangeSummary: String? = null,
    isLowFuel: Boolean = false,
) {
    Surface(
        onClick = onMoreStats,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(min = RideTouchTarget),
        shape = RoundedCornerShape(12.dp),
        color = palette.bgPanel.copy(alpha = 0.9f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Dist ${String.format(Locale.US, "%.1f km", distanceKm)}",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatSecondsToTime(tripTimeSeconds),
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
                Text(
                    text = "More stats",
                    color = palette.neonBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
            if (!fuelRangeSummary.isNullOrBlank()) {
                Text(
                    text = buildString {
                        append(fuelRangeSummary)
                        if (isLowFuel) append(" · Low")
                    },
                    color = if (isLowFuel) palette.neonRed else palette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RideStatsSheet(
    stats: TripStats,
    onDismiss: () -> Unit,
    palette: AppPalette = LocalAppPalette.current,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bgDeep,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Ride stats",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            RideStatsGrid(stats = stats, palette = palette)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EndRideConfirmSheet(
    onSaveRide: () -> Unit,
    onKeepRiding: () -> Unit,
    palette: AppPalette = LocalAppPalette.current,
) {
    ModalBottomSheet(
        onDismissRequest = onKeepRiding,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bgCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "End ride and save?",
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Your track, stats, and route will be saved to History.",
                color = palette.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSaveRide,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RideTouchTarget),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.neonBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = null,
                        tint = palette.bgDeep,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Save ride",
                        color = palette.bgDeep,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = onKeepRiding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = RideTouchTarget),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.bgPanel),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = null,
                        tint = palette.textPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Keep riding",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HudRailButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    palette: AppPalette,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(RideTouchTarget)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.bgPanel.copy(alpha = 0.9f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
        Text(
            text = label,
            color = palette.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun HudIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(RideTouchTarget),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
