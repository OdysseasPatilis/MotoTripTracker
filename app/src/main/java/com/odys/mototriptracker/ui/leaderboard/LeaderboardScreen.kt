package com.odys.mototriptracker.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.usecase.LeaderboardCategory
import com.odys.mototriptracker.domain.usecase.LeaderboardEntry
import com.odys.mototriptracker.ui.components.ScreenTopBar
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LeaderboardScreen(
    selectedCategory: LeaderboardCategory,
    entries: List<LeaderboardEntry>,
    onBack: () -> Unit,
    onSelectCategory: (LeaderboardCategory) -> Unit,
    onEntryClick: (Long) -> Unit
) {
    val palette = LocalAppPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgDeep)
    ) {
        ScreenTopBar(title = "Leaderboard", onBack = onBack, palette = palette)

        Spacer(Modifier.height(12.dp))

        LeaderboardTabs(
            selected = selectedCategory,
            onSelect = onSelectCategory,
            palette = palette,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emptyMessage(selectedCategory),
                            color = palette.emptyText,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(entries, key = { "${selectedCategory}-${it.tripId}" }) { entry ->
                    LeaderboardRow(
                        entry = entry,
                        onClick = { onEntryClick(entry.tripId) },
                        palette = palette
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTabs(
    selected: LeaderboardCategory,
    onSelect: (LeaderboardCategory) -> Unit,
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
        LeaderboardCategory.entries.forEach { category ->
            val isSelected = category == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) palette.bgCard else palette.bgPanel)
                    .clickable { onSelect(category) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categoryLabel(category),
                    color = if (isSelected) palette.neonGreen else palette.textMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    entry: LeaderboardEntry,
    onClick: () -> Unit,
    palette: AppPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.bgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank = entry.rank, palette = palette)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.valueLabel,
                color = palette.neonGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = entry.title,
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatEntryDate(entry.startTimeMs),
                color = palette.textMuted,
                fontSize = 11.sp
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = palette.emptyText,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun RankBadge(rank: Int, palette: AppPalette) {
    val (bg, fg, border) = when (rank) {
        1 -> Triple(Color(0x33FFD700), Color(0xFFFFD700), Color(0x66FFD700))
        2 -> Triple(Color(0x33C0C0C0), Color(0xFFC0C0C0), Color(0x66C0C0C0))
        3 -> Triple(Color(0x33CD7F32), Color(0xFFCD7F32), Color(0x66CD7F32))
        else -> Triple(palette.bgSurface, palette.textMuted, Color.Transparent)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (rank <= 3) Modifier.border(1.dp, border, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "#$rank",
            color = fg,
            fontSize = if (rank < 100) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun categoryLabel(category: LeaderboardCategory): String = when (category) {
    LeaderboardCategory.SPEED -> "Speed"
    LeaderboardCategory.DISTANCE -> "Distance"
    LeaderboardCategory.TURNS -> "Turns"
    LeaderboardCategory.TWISTINESS -> "Twist"
}

private fun emptyMessage(category: LeaderboardCategory): String = when (category) {
    LeaderboardCategory.SPEED -> "No speed records yet"
    LeaderboardCategory.DISTANCE -> "No distance records yet"
    LeaderboardCategory.TURNS -> "No turn records yet"
    LeaderboardCategory.TWISTINESS -> "No twistiness records yet"
}

private fun formatEntryDate(timeMs: Long): String {
    if (timeMs <= 0L) return ""
    return SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault()).format(Date(timeMs))
}
