package com.odys.mototriptracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.export.displayTitle
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.ui.components.ScreenTopBar
import com.odys.mototriptracker.ui.history.DateFilterPreset
import com.odys.mototriptracker.ui.history.RideHistoryFilters
import com.odys.mototriptracker.ui.history.RideHistoryTab
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private sealed class HistoryListItem {
    data class DayHeader(val label: String, val key: String) : HistoryListItem()
    data class Ride(val trip: TripEntity) : HistoryListItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(
    rides: List<TripEntity>,
    selectedTab: RideHistoryTab,
    searchQuery: String,
    filters: RideHistoryFilters,
    onBack: () -> Unit,
    onRideClick: (TripEntity) -> Unit,
    onToggleFavorite: (Long) -> Unit = {},
    onSelectTab: (RideHistoryTab) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onFiltersChange: (RideHistoryFilters) -> Unit = {},
    onClearFilters: () -> Unit = {}
) {
    val palette = LocalAppPalette.current
    val listItems = remember(rides) { buildHistoryListItems(rides) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgDeep)
    ) {
        ScreenTopBar(title = "Ride History", onBack = onBack, palette = palette)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HistorySearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            FilterButton(
                active = filters.hasActiveFilters,
                onClick = { showFilterSheet = true },
                palette = palette
            )
        }

        if (filters.hasActiveFilters) {
            ActiveFilterChips(
                filters = filters,
                onClear = onClearFilters,
                palette = palette,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
        }

        HistoryTabs(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            palette = palette,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (listItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emptyMessage(selectedTab, searchQuery, filters),
                            color = palette.emptyText,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(
                    items = listItems,
                    key = { item ->
                        when (item) {
                            is HistoryListItem.DayHeader -> "day-${item.key}"
                            is HistoryListItem.Ride -> "ride-${item.trip.id}"
                        }
                    }
                ) { item ->
                    when (item) {
                        is HistoryListItem.DayHeader -> DayDivider(label = item.label, palette = palette)
                        is HistoryListItem.Ride -> RideHistoryCard(
                            ride = item.trip,
                            onClick = { onRideClick(item.trip) },
                            onToggleFavorite = { onToggleFavorite(item.trip.id) },
                            palette = palette
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        HistoryFilterSheet(
            initial = filters,
            palette = palette,
            onDismiss = { showFilterSheet = false },
            onApply = { applied ->
                onFiltersChange(applied)
                showFilterSheet = false
            },
            onClear = {
                onClearFilters()
                showFilterSheet = false
            }
        )
    }
}

@Composable
private fun HistorySearchBar(
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
private fun FilterButton(
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
private fun ActiveFilterChips(
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
private fun HistoryFilterSheet(
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
private fun HistoryDatePickerDialog(
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
private fun FilterChip(
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
private fun DatePickField(
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

@Composable
private fun HistoryTabs(
    selectedTab: RideHistoryTab,
    onSelectTab: (RideHistoryTab) -> Unit,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bgPanel)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HistoryTabChip(
            label = "All",
            selected = selectedTab == RideHistoryTab.ALL,
            onClick = { onSelectTab(RideHistoryTab.ALL) },
            palette = palette,
            modifier = Modifier.weight(1f)
        )
        HistoryTabChip(
            label = "Favorites",
            selected = selectedTab == RideHistoryTab.FAVORITES,
            onClick = { onSelectTab(RideHistoryTab.FAVORITES) },
            palette = palette,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HistoryTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    palette: AppPalette,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.bgCard else palette.bgPanel)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) palette.neonGreen else palette.textMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun DayDivider(
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

private fun buildHistoryListItems(rides: List<TripEntity>): List<HistoryListItem> {
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

private fun dayKey(timeMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

private fun dayLabel(timeMs: Long): String {
    val rideDay = Calendar.getInstance().apply { timeInMillis = timeMs }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        sameDay(rideDay, today) -> "Today"
        sameDay(rideDay, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(timeMs))
    }
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun emptyMessage(
    tab: RideHistoryTab,
    query: String,
    filters: RideHistoryFilters
): String = when {
    query.isNotBlank() || filters.hasActiveFilters -> "No rides match your search"
    tab == RideHistoryTab.FAVORITES -> "No favorite rides yet"
    else -> "No rides recorded yet"
}

private fun datePresetOptions(): List<Pair<DateFilterPreset, String>> = listOf(
    DateFilterPreset.ANY to "Any",
    DateFilterPreset.TODAY to "Today",
    DateFilterPreset.YESTERDAY to "Yesterday",
    DateFilterPreset.THIS_WEEK to "This week",
    DateFilterPreset.THIS_MONTH to "This month",
    DateFilterPreset.CUSTOM to "Custom"
)

private fun filterSummaryLabels(filters: RideHistoryFilters): List<String> {
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

private fun formatFilterDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

/**
 * Material DatePicker uses UTC midnight for selected days.
 * Convert that UTC day into local start/end-of-day for trip filtering.
 */
private fun startOfDayUtc(utcDayMillis: Long): Long {
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

private fun endOfDayUtc(utcDayMillis: Long): Long {
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

private fun toUtcMidnight(localMillis: Long): Long {
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

private fun localStartOfToday(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun localEndOfToday(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
