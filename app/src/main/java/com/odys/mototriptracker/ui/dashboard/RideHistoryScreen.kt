package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.trip.TripEntity

@Composable
fun RideHistoryScreen(
    rides: List<TripEntity>,
    onBack: () -> Unit,
    onRideClick: (TripEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Top bar
        TopBar(title = "Ride History", onBack = onBack)

        Text(
            "RECENT RIDES",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rides.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No rides recorded yet", color = Color(0xFF2A2A4A), fontSize = 14.sp)
                    }
                }
            } else {
                items(rides) { ride ->
                    RideHistoryCard(ride = ride, onClick = { onRideClick(ride) })
                }
            }
        }
    }
}

@Composable
fun RideHistoryCard(ride: TripEntity, onClick: () -> Unit) {
    val gradient = Brush.horizontalGradient(listOf(NeonGreen, NeonBlue))
    val totalTime = ride.movingTime + ride.stoppedTime

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .clickable(onClick = onClick)
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(82.dp)
                .background(gradient)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BgSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DirectionsBike, contentDescription = null,
                    tint = NeonGreen, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatTimestampToDate(ride.startTime), color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${formatSecondsToTime(totalTime)} duration",
                    color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${String.format("%.1f km", ride.distanceMeters / 1000f)} km  ·  ${ride.avgSpeed.toInt()} km/h avg",
                    color = TextMuted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                tint = Color(0xFF2A2A4A), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F1A))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
    Divider(color = Color(0xFF1A1A30), thickness = 1.dp)
}
