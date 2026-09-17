package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.odys.mototriptracker.data.petrol.OpeningHoursEvaluator
import com.odys.mototriptracker.ui.theme.AppPalette
import java.util.Locale

@Composable
internal fun DetailCard(
    palette: AppPalette,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.bgCard)
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
    }
}

@Composable
internal fun StatusChip(
    label: String,
    tint: Color,
    compact: Boolean = false
) {
    Text(
        label,
        color = tint,
        fontSize = if (compact) 11.sp else 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 4.dp
            )
    )
}

@Composable
internal fun DetailLine(label: String, value: String, palette: AppPalette) {
    Column {
        Text(label, color = palette.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, color = palette.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

internal fun openLabel(status: OpeningHoursEvaluator.Status): String = when (status) {
    OpeningHoursEvaluator.Status.OPEN -> "Open"
    OpeningHoursEvaluator.Status.CLOSED -> "Closed"
    OpeningHoursEvaluator.Status.UNKNOWN -> "Hours ?"
}

internal fun formatDistance(meters: Double): String =
    if (meters >= 1000) String.format(Locale.US, "%.1f km", meters / 1000.0)
    else "${meters.toInt()} m"
