package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.petrol.OpeningHoursEvaluator
import com.odys.mototriptracker.application.RideTrackerFacade
import com.odys.mototriptracker.application.PetrolStationRecommendation
import com.odys.mototriptracker.ui.theme.AppPalette
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StationCard(
    rank: Int,
    station: PetrolStationRecommendation,
    isPreferredBrand: (String?) -> Boolean,
    preferredOctanes: Set<Int>,
    palette: AppPalette,
    onGo: () -> Unit,
    onDetails: () -> Unit
) {
    val dimmed = station.openStatus == OpeningHoursEvaluator.Status.CLOSED
    val preferred = isPreferredBrand(station.brand ?: station.name)
    val accent = if (preferred) palette.neonGreen else palette.neonBlue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.55f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.14f),
                        palette.bgCard
                    )
                )
            )
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$rank",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                station.name,
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusChip(
                    label = formatDistance(station.distanceMeters),
                    tint = palette.neonBlue,
                    compact = true
                )
                StatusChip(
                    label = openLabel(station.openStatus),
                    tint = when (station.openStatus) {
                        OpeningHoursEvaluator.Status.OPEN -> palette.neonGreen
                        OpeningHoursEvaluator.Status.CLOSED -> palette.neonRed
                        OpeningHoursEvaluator.Status.UNKNOWN -> palette.routeAmber
                    },
                    compact = true
                )
                if (station.hoursFromGoogle) {
                    StatusChip(label = "Google", tint = palette.neonBlue, compact = true)
                }
                if (preferred) {
                    StatusChip(label = "Preferred", tint = palette.neonGreen, compact = true)
                }
                if (station.isHighwayAccessible) {
                    StatusChip(label = "Highway", tint = palette.neonBlue, compact = true)
                }
            }
            station.address?.let {
                Text(
                    it,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    station.displayOctanes(preferredOctanes),
                    color = when {
                        station.availableOctanes.isEmpty() -> palette.textSecondary
                        station.availableOctanes.intersect(preferredOctanes).isNotEmpty() -> palette.neonGreen
                        else -> palette.routeAmber
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                station.rating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = palette.routeAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            String.format(Locale.US, "%.1f", rating),
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGo,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.neonGreen),
                    contentPadding = ButtonDefaults.ContentPadding,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = palette.bgDeep,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Go", color = palette.bgDeep, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDetails,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.neonBlue.copy(alpha = 0.55f))
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = palette.neonBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Details", color = palette.neonBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

