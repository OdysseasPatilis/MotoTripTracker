package com.odys.mototriptracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.export.displayTitle
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.odys.mototriptracker.ui.components.formatSecondsToTime

internal sealed class HistoryListItem {
    data class DayHeader(val label: String, val key: String) : HistoryListItem()
    data class Ride(val trip: TripEntity) : HistoryListItem()
}

@Composable
internal fun DayDivider(
    label: String,
    palette: AppPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            color = palette.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(palette.divider)
        )
    }
}

@Composable
fun RideHistoryCard(
    ride: TripEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    palette: AppPalette = LocalAppPalette.current
) {
    val totalTime = ride.movingTime + ride.stoppedTime

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.bgCard)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(90.dp)
                .background(palette.startGradient)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.bgSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.DirectionsBike,
                    contentDescription = null,
                    tint = palette.neonGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ride.displayTitle(),
                    color = palette.neonGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatSecondsToTime(totalTime)} duration",
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${String.format(Locale.US, "%.1f km", ride.distanceMeters / 1000f)}  ·  ${ride.avgSpeed.toInt()} km/h avg" +
                        if (ride.cornerCount > 0) "  ·  ${ride.cornerCount} corners" else "",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (ride.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (ride.isFavorite) "Unfavorite" else "Favorite",
                    tint = if (ride.isFavorite) palette.neonGreen else palette.textMuted
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = palette.emptyText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

internal fun buildHistoryListItems(rides: List<TripEntity>): List<HistoryListItem> {
    if (rides.isEmpty()) return emptyList()

    val items = mutableListOf<HistoryListItem>()
    var lastDayKey: String? = null

    rides.forEach { ride ->
        val dayKey = dayKey(ride.startTime)
        if (dayKey != lastDayKey) {
            items += HistoryListItem.DayHeader(label = dayLabel(ride.startTime), key = dayKey)
            lastDayKey = dayKey
        }
        items += HistoryListItem.Ride(ride)
    }
    return items
}

internal fun dayKey(timeMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

internal fun dayLabel(timeMs: Long): String {
    val rideDay = Calendar.getInstance().apply { timeInMillis = timeMs }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        sameDay(rideDay, today) -> "Today"
        sameDay(rideDay, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(timeMs))
    }
}

internal fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

