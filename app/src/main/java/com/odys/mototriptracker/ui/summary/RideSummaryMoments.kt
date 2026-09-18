package com.odys.mototriptracker.ui.summary


import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.domain.RideMoment
import com.odys.mototriptracker.domain.RideMoments
import com.odys.mototriptracker.ui.theme.AppPalette

@Composable
internal fun RideMomentsSection(
    moments: RideMoments,
    palette: AppPalette
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "MOMENTS",
            color = palette.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        moments.moments.forEach { moment ->
            MomentCard(moment = moment, palette = palette)
        }
    }
}

@Composable
internal fun MomentCard(
    moment: RideMoment,
    palette: AppPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.bgCard)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.neonGreen.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = momentIcon(moment.iconKey),
                contentDescription = null,
                tint = palette.neonGreen,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = moment.title,
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = moment.value,
                    color = palette.neonBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = moment.detail,
                color = palette.textMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun momentIcon(iconKey: String): ImageVector = when (iconKey) {
    "speed" -> Icons.Rounded.Speed
    "bolt" -> Icons.Rounded.Bolt
    "terrain" -> Icons.Rounded.Terrain
    "descent" -> Icons.AutoMirrored.Rounded.TrendingDown
    "flag" -> Icons.Rounded.Flag
    "pause" -> Icons.Rounded.PauseCircle
    "wind" -> Icons.Rounded.Air
    "twisties" -> Icons.Rounded.TurnSlightRight
    "open_road" -> Icons.Rounded.Route
    "stop_go" -> Icons.Rounded.Traffic
    "dawn" -> Icons.Rounded.WbTwilight
    "night" -> Icons.Rounded.NightsStay
    "distance" -> Icons.Rounded.Straighten
    else -> Icons.Rounded.AutoAwesome
}

// ── Top bar ───────────────────────────────────────────────────────────────────
