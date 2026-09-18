package com.odys.mototriptracker.ui.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.ui.components.ScreenTopBar
import com.odys.mototriptracker.ui.history.RideHistoryFilters
import com.odys.mototriptracker.ui.history.RideHistoryTab
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette


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
internal fun HistoryTabs(
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
internal fun HistoryTabChip(
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
private fun emptyMessage(
    tab: RideHistoryTab,
    query: String,
    filters: RideHistoryFilters
): String = when {
    query.isNotBlank() || filters.hasActiveFilters -> "No rides match your search"
    tab == RideHistoryTab.FAVORITES -> "No favorite rides yet"
    else -> "No rides recorded yet"
}

