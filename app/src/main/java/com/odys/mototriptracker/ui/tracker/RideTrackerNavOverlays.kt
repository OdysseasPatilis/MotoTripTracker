package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
                                .heightIn(min = RideTouchTarget)
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
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = RideTouchTarget),
                colors = ButtonDefaults.buttonColors(containerColor = palette.bgCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = palette.textSecondary, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = RideTouchTarget),
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
        IconButton(onClick = onDismiss, modifier = Modifier.size(RideTouchTarget)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss timing summary",
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
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

