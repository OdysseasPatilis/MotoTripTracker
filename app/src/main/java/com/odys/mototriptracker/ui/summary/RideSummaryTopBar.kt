package com.odys.mototriptracker.ui.summary


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.model.displayTitle
import com.odys.mototriptracker.domain.model.Trip
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.components.formatTimestampToDate

private val Mint = Color(0xFF5EFFC8)

@Composable
internal fun TopBar(
    isFavorite: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onToggleFavorite: () -> Unit,
    palette: AppPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.bgCard)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = palette.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = "Ride Summary",
            color = palette.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TopIconButton(
                onClick = onToggleFavorite,
                background = palette.bgCard,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                tint = if (isFavorite) palette.neonGreen else palette.textPrimary,
                icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline
            )
            TopIconButton(
                onClick = onRename,
                background = palette.bgCard,
                contentDescription = "Rename",
                tint = palette.textPrimary,
                icon = Icons.Default.Edit
            )
            TopIconButton(
                onClick = onShare,
                background = palette.bgCard,
                contentDescription = "Share",
                tint = palette.textPrimary,
                icon = Icons.Default.Share
            )
            TopIconButton(
                onClick = onDelete,
                background = palette.deleteButtonBg,
                contentDescription = "Delete",
                tint = palette.stopRed,
                icon = Icons.Default.Delete
            )
        }
    }
}

@Composable
internal fun TopIconButton(
    onClick: () -> Unit,
    background: Color,
    contentDescription: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── Date card ─────────────────────────────────────────────────────────────────
@Composable
internal fun DateCard(summary: Trip) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF5B5FEF), Color(0xFF7C4DFF))
                )
            )
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DATE & TIME",
                color = Color(0xAAFFFFFF),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = summary.displayTitle(),
                color = Mint,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary.title?.isNotBlank() == true) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatTimestampToDate(summary.startTime),
                    color = Color(0xAAFFFFFF),
                    fontSize = 12.sp
                )
            }
        }
    }
}
