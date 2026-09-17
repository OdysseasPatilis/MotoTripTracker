package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.ui.history.DateFilterPreset
import com.odys.mototriptracker.ui.history.RideHistoryFilters
import com.odys.mototriptracker.ui.theme.AppPalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun HistorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = palette.textMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            cursorBrush = SolidColor(palette.neonGreen),
            textStyle = TextStyle(
                color = palette.textPrimary,
                fontSize = 14.sp
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Search by name…",
                        color = palette.textMuted,
                        fontSize = 14.sp
                    )
                }
                inner()
            }
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear search",
                    tint = palette.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun FilterButton(
    active: Boolean,
    onClick: () -> Unit,
    palette: AppPalette
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) palette.neonGreen.copy(alpha = 0.18f) else palette.bgCard)
            .then(
                if (active) {
                    Modifier.border(1.dp, palette.neonGreen, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = "Filters",
            tint = if (active) palette.neonGreen else palette.textMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun ActiveFilterChips(
    filters: RideHistoryFilters,
    onClear: () -> Unit,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filterSummaryLabels(filters).forEach { label ->
            Text(
                text = label,
                color = palette.neonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.neonGreen.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Text(
            text = "Clear",
            color = palette.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClear)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryFilterSheet(
    initial: RideHistoryFilters,
    palette: AppPalette,
    onDismiss: () -> Unit,
    onApply: (RideHistoryFilters) -> Unit,
    onClear: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(initial) { mutableStateOf(initial) }
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.bgPanel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Filters",
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(18.dp))

            Text(
                text = "DATE",
                color = palette.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                datePresetOptions().forEach { (preset, label) ->
                    FilterChip(
                        label = label,
                        selected = draft.datePreset == preset,
                        onClick = {
                            draft = draft.copy(
                                datePreset = preset,
                                customFromMs = if (preset == DateFilterPreset.CUSTOM) {
                                    draft.customFromMs ?: localStartOfToday()
                                } else {
                                    null
                                },
                                customToMs = if (preset == DateFilterPreset.CUSTOM) {
                                    draft.customToMs ?: localEndOfToday()
                                } else {
                                    null
                                }
                            )
                        },
                        palette = palette
                    )
                }
            }

            if (draft.datePreset == DateFilterPreset.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DatePickField(
                        label = "From",
                        millis = draft.customFromMs,
                        onClick = { pickingFrom = true },
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                    DatePickField(
                        label = "To",
                        millis = draft.customToMs,
                        onClick = { pickingTo = true },
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear", color = palette.textMuted)
                }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.neonGreen,
                        contentColor = palette.bgDeep
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (pickingFrom) {
        HistoryDatePickerDialog(
            initialMillis = draft.customFromMs,
            onDismiss = { pickingFrom = false },
            onConfirm = { selected ->
                draft = draft.copy(customFromMs = startOfDayUtc(selected))
                pickingFrom = false
            }
        )
    }
    if (pickingTo) {
        HistoryDatePickerDialog(
            initialMillis = draft.customToMs,
            onDismiss = { pickingTo = false },
            onConfirm = { selected ->
                draft = draft.copy(customToMs = endOfDayUtc(selected))
                pickingTo = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDatePickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis?.let(::toUtcMidnight) ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let(onConfirm)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
internal fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    palette: AppPalette
) {
    Text(
        text = label,
        color = if (selected) palette.neonGreen else palette.textMuted,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) palette.neonGreen.copy(alpha = 0.16f) else palette.bgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
internal fun DatePickField(
    label: String,
    millis: Long?,
    onClick: () -> Unit,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, color = palette.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.bgCard)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(
                text = millis?.let { formatFilterDate(it) } ?: "Pick date",
                color = if (millis != null) palette.textPrimary else palette.textMuted,
                fontSize = 13.sp
            )
        }
    }
}

internal fun datePresetOptions(): List<Pair<DateFilterPreset, String>> = listOf(
    DateFilterPreset.ANY to "Any",
    DateFilterPreset.TODAY to "Today",
    DateFilterPreset.YESTERDAY to "Yesterday",
    DateFilterPreset.THIS_WEEK to "This week",
    DateFilterPreset.THIS_MONTH to "This month",
    DateFilterPreset.CUSTOM to "Custom"
)

internal fun filterSummaryLabels(filters: RideHistoryFilters): List<String> {
    val labels = mutableListOf<String>()
    when (filters.datePreset) {
        DateFilterPreset.ANY -> Unit
        DateFilterPreset.TODAY -> labels += "Today"
        DateFilterPreset.YESTERDAY -> labels += "Yesterday"
        DateFilterPreset.THIS_WEEK -> labels += "This week"
        DateFilterPreset.THIS_MONTH -> labels += "This month"
        DateFilterPreset.CUSTOM -> {
            val from = filters.customFromMs?.let(::formatFilterDate)
            val to = filters.customToMs?.let(::formatFilterDate)
            when {
                from != null && to != null -> labels += "$from – $to"
                from != null -> labels += "From $from"
                to != null -> labels += "Until $to"
            }
        }
    }
    return labels
}

internal fun formatFilterDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

/**
 * Material DatePicker uses UTC midnight for selected days.
 * Convert that UTC day into local start/end-of-day for trip filtering.
 */
internal fun startOfDayUtc(utcDayMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcDayMillis
    }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

internal fun endOfDayUtc(utcDayMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcDayMillis
    }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

internal fun toUtcMidnight(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(Calendar.YEAR, local.get(Calendar.YEAR))
        set(Calendar.MONTH, local.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, local.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

internal fun localStartOfToday(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

internal fun localEndOfToday(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
