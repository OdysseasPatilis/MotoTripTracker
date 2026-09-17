package com.odys.mototriptracker.ui.dashboard


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.domain.TwistinessCalculator
import com.odys.mototriptracker.ui.theme.AppPalette
import java.util.Locale

@Composable
internal fun StatsGrid(
    summary: TripEntity,
    palette: AppPalette
) {
    val totalTime = summary.movingTime + summary.stoppedTime
    val stats = listOf(
        StatItem("Distance",   "${String.format(Locale.US, "%.1f ", summary.distanceMeters / 1000f)}",  "km",    palette.textPrimary),
        StatItem("Total time", formatSecondsToTime(totalTime),"mm:ss", palette.textPrimary),
        StatItem("Moving",     formatSecondsToTime(summary.movingTime),"mm:ss", palette.mint),
        StatItem("Stopped",    formatSecondsToTime(summary.stoppedTime),"mm:ss", palette.neonRed),
        StatItem("Avg speed",  summary.avgSpeed.toInt().toString(),   "km/h",  palette.textPrimary),
        StatItem("Max speed",  summary.maxSpeed.toInt().toString(),  "km/h",  palette.neonBlue),
        StatItem("Elevation",  "+${summary.elevationGain.toInt()}",  "meters", palette.textPrimary),
        StatItem("Max G",      String.format(Locale.US, "%.2f", summary.maxGForce), "G-force", palette.purpleAccent),
        StatItem("Lateral G",  String.format(Locale.US, "%.2f", summary.maxLateralGForce), "G-force", palette.neonBlue),
        StatItem("Twistiness", TwistinessCalculator.formattedScore(TwistinessCalculator.score(summary)), "score", palette.neonBlue),
        StatItem("Corners",    summary.cornerCount.toString(), "turns", palette.mint),
    )

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { stat ->
                    StatCard(stat, palette, modifier = Modifier.weight(1f))
                }
                // fill empty slot if odd number of stats
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

internal data class StatItem(
    val label: String,
    val value: String,
    val unit: String,
    val valueColor: Color
)

@Composable
internal fun StatCard(
    stat: StatItem,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgPanel)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = stat.label.uppercase(),
            color = palette.textSecondary,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stat.value,
            color = stat.valueColor,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stat.unit,
            color = palette.textSecondary,
            fontSize = 11.sp
        )
    }
}
