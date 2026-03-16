package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.trip.TripEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideSummaryScreen(summary: TripEntity, onBack: () -> Unit, onDelete: () -> Unit) {
    val totalTime = summary.movingTime + summary.stoppedTime
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Top bar with delete button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F1A))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .statusBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text("Ride Summary", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonRed.copy(alpha = 0.15f))
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete",
                    tint = NeonRed, modifier = Modifier.size(18.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Hero date card
            val heroGradient = Brush.horizontalGradient(listOf(NeonGreen, NeonBlue))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSurface)
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DATE & TIME", color = TextMuted, fontSize = 11.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatTimestampToDate(summary.startTime),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(brush = heroGradient)
                    )
                }
            }

            // Stats grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStatCard("DISTANCE", "${String.format("%.1f km", summary.distanceMeters / 1000f)}", "km", Modifier.weight(1f))
                SummaryStatCard("TOTAL TIME", formatSecondsToTime(totalTime), "mm:ss", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStatCard("MOVING", formatSecondsToTime(summary.movingTime), "mm:ss", Modifier.weight(1f), valueColor = NeonGreen)
                SummaryStatCard("STOPPED", formatSecondsToTime(summary.stoppedTime), "mm:ss", Modifier.weight(1f), valueColor = NeonRed)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStatCard("AVG SPEED", summary.avgSpeed.toInt().toString(), "km/h", Modifier.weight(1f))
                SummaryStatCard("MAX SPEED", summary.maxSpeed.toInt().toString(), "km/h", Modifier.weight(1f), valueColor = NeonBlue)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStatCard("ELEVATION", "+${summary.elevationGain.toInt()}", "meters", Modifier.weight(1f))
                SummaryStatCard("MAX G", String.format("%.2f", summary.maxGForce), "G-force", Modifier.weight(1f), valueColor = NeonGreen)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SummaryStatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextMuted, fontSize = 11.sp,
                letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(value, color = valueColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = TextMuted, fontSize = 12.sp)
        }
    }
}