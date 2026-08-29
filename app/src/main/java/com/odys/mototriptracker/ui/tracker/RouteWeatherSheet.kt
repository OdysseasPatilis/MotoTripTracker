package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.weather.RouteWeatherSegment
import com.odys.mototriptracker.data.weather.RouteWeatherState
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteWeatherSheet(
    weather: RouteWeatherState,
    onDismiss: () -> Unit,
    palette: AppPalette = LocalAppPalette.current
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bgDeep
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "Route weather",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("Done", color = palette.neonGreen) }
            }
            Spacer(Modifier.height(12.dp))
            if (weather.isLoading && weather.segments.isEmpty()) {
                CircularProgressIndicator(color = palette.neonGreen)
            } else if (weather.segments.isEmpty()) {
                Text(weather.summaryText, color = palette.textSecondary)
            } else {
                LazyColumn {
                    items(weather.segments, key = { "${it.label}-${it.etaEpochMs}" }) { segment ->
                        WeatherSegmentRow(segment, palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSegmentRow(segment: RouteWeatherSegment, palette: AppPalette) {
    val time = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT, Locale.getDefault())
        .format(Date(segment.etaEpochMs))
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(segment.label, color = palette.textPrimary, fontWeight = FontWeight.Medium)
        Text(
            "${segment.conditionLabel} · ${segment.temperatureC?.toInt() ?: "—"}°C · ETA $time",
            color = palette.textSecondary,
            fontSize = 13.sp
        )
        segment.precipitationProbability?.let { rain ->
            if (rain >= 20) {
                Text("$rain% rain chance", color = palette.neonBlue, fontSize = 12.sp)
            }
        }
    }
}
