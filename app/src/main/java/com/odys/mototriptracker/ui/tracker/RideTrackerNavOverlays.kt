package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.application.NavigationState
import com.odys.mototriptracker.ui.theme.AppPalette

@Composable
internal fun RoutePreviewCard(
    navigation: NavigationState,
    palette: AppPalette,
    onSelectRoute: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val canStart = navigation.selectedRouteId != null &&
        !navigation.isRouting &&
        navigation.previewErrorMessage == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Flag,
                contentDescription = null,
                tint = palette.neonBlue,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = navigation.destinationName ?: "Destination",
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            navigation.isRouting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = palette.neonBlue
                    )
                    Text(
                        "Finding routes…",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
                            navigation.previewErrorMessage != null -> {
                Text(
                    navigation.previewErrorMessage.orEmpty(),
                    color = palette.routeAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    navigation.previewRoutes.forEachIndexed { index, option ->
                        val selected = option.id == navigation.selectedRouteId
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) palette.neonBlue.copy(alpha = 0.18f)
                                    else Color.Transparent
                                )
                                .clickable { onSelectRoute(option.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                if (index == 0) "Fastest" else "Route ${index + 1}",
                                color = if (selected) palette.textPrimary else palette.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${NavigationState.formatDistance(option.distanceMeters)} · Moto " +
                                    NavigationState.formatDuration(option.motoTravelTimeSeconds),
                                color = if (selected) palette.textPrimary else palette.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (option.trafficDelaySeconds >= 90) {
                                Text(
                                    "Cars ${NavigationState.formatDuration(option.expectedTravelTimeSeconds)}",
                                    color = if (selected) palette.routeAmber else palette.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.bgCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = palette.textSecondary, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.neonGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start", color = palette.bgDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun TimingResultBanner(
    text: String,
    palette: AppPalette,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.Flag,
            contentDescription = null,
            tint = palette.neonGreen,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss timing summary",
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun ActiveRouteChip(
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
            IconButton(onClick = onShowWeather, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.WbSunny,
                    contentDescription = "Route weather",
                    tint = palette.neonBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        IconButton(onClick = onToggleVoice, modifier = Modifier.size(48.dp)) {
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
        IconButton(onClick = onOpenInMaps, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.Navigation,
                contentDescription = "Open in Google Maps",
                tint = palette.neonGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
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
internal fun ManeuverBanner(
    navigation: NavigationState,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    val accent = if (navigation.isOffRoute || navigation.isRecalculating) palette.routeAmber else palette.neonBlue
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = maneuverIcon(navigation),
                contentDescription = null,
                tint = palette.bgDeep,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when {
                navigation.isRecalculating -> {
                    Text(
                        "Recalculating…",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                }
                navigation.isOffRoute -> {
                    Text(
                        "Off route",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                }
                navigation.currentStep != null -> {
                    Text(
                        NavigationState.formatDistance(navigation.distanceToNextManeuverMeters),
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        navigation.currentStep.instruction,
                        color = palette.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                navigation.isRouting -> {
                    Text(
                        "Calculating route…",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                }
                else -> {
                    Text(
                        navigation.destinationName ?: "Destination",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal fun maneuverIcon(navigation: NavigationState): ImageVector {
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

/**
 * Compact next-maneuver chip for the map HUD. Height is capped at 56.dp;
 * distance is the primary line and the maneuver is secondary.
 */
@Composable
internal fun TurnChip(
    navigation: NavigationState,
    palette: AppPalette,
    modifier: Modifier = Modifier,
) {
    val step = navigation.currentStep
    val distance = NavigationState.formatDistance(navigation.distanceToNextManeuverMeters)
    val primary: String
    val secondary: String?
    when {
        navigation.isRecalculating -> {
            primary = "Recalculating…"
            secondary = null
        }
        navigation.isOffRoute -> {
            primary = "Off route"
            secondary = "Recalculating"
        }
        step != null -> {
            primary = distance
            secondary = step.instruction
        }
        navigation.isRouting -> {
            primary = "Routing…"
            secondary = null
        }
        else -> {
            primary = navigation.destinationName ?: "Destination"
            secondary = null
        }
    }
    val accent = if (navigation.isOffRoute || navigation.isRecalculating) {
        palette.routeAmber
    } else {
        palette.neonBlue
    }

    Row(
        modifier = modifier
            .height(56.dp)
            .widthIn(max = 248.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = maneuverIcon(navigation),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.widthIn(max = 190.dp)) {
            Text(
                text = primary,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    color = palette.textSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
internal fun NavGuidanceActions(
    navigation: NavigationState,
    palette: AppPalette,
    onToggleVoice: () -> Unit,
    onOpenInMaps: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudIconButton(
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
            palette = palette,
            onClick = onToggleVoice
        )
        HudIconButton(
            icon = Icons.Filled.Navigation,
            contentDescription = "Open in Google Maps",
            tint = palette.neonGreen,
            palette = palette,
            onClick = onOpenInMaps
        )
        HudIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Clear destination",
            tint = palette.textSecondary,
            palette = palette,
            onClick = onClear
        )
    }
}

@Composable
internal fun RideAccessButtons(
    palette: AppPalette,
    isLowFuel: Boolean,
    onFuel: () -> Unit,
    onWeather: () -> Unit,
    onDestination: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
    showDestination: Boolean = true,
) {
    val content: @Composable () -> Unit = {
        HudTargetButton(
            icon = Icons.Filled.LocalGasStation,
            label = "Fuel",
            tint = if (isLowFuel) palette.neonRed else palette.neonGreen,
            palette = palette,
            onClick = onFuel
        )
        HudTargetButton(
            icon = Icons.Filled.WbSunny,
            label = "Weather",
            tint = palette.neonBlue,
            palette = palette,
            onClick = onWeather
        )
        if (showDestination) {
            HudTargetButton(
                icon = Icons.Filled.Place,
                label = "Destination",
                tint = palette.neonBlue,
                palette = palette,
                onClick = onDestination
            )
        }
    }
    if (vertical) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}

@Composable
private fun HudIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun HudTargetButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    palette: AppPalette,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bgPanel.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = palette.textSecondary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

