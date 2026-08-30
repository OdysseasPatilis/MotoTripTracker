package com.odys.mototriptracker.ui.tracker

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.petrol.GooglePetrolDetails
import com.odys.mototriptracker.data.petrol.OpeningHoursEvaluator
import com.odys.mototriptracker.data.petrol.PetrolPreferences
import com.odys.mototriptracker.data.petrol.PetrolSearchPlan
import com.odys.mototriptracker.data.petrol.PetrolStationRecommendation
import com.odys.mototriptracker.data.petrol.RankedPetrolStation
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PetrolStationsSheet(
    stations: List<RankedPetrolStation>,
    plan: PetrolSearchPlan?,
    isLoading: Boolean,
    preferences: PetrolPreferences,
    preferredOctanes: Set<Int>,
    googleDetails: GooglePetrolDetails?,
    googleDetailsLoading: Boolean,
    onGo: (PetrolStationRecommendation) -> Unit,
    onRequestDetails: (PetrolStationRecommendation) -> Unit,
    onClearDetails: () -> Unit,
    onDismiss: () -> Unit,
    palette: AppPalette = LocalAppPalette.current
) {
    var detailsStation by remember { mutableStateOf<PetrolStationRecommendation?>(null) }
    val context = LocalContext.current

    LaunchedEffect(detailsStation) {
        val station = detailsStation
        if (station != null) onRequestDetails(station) else onClearDetails()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bgDeep
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(listOf(palette.neonGreen, palette.neonBlue)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LocalGasStation,
                        contentDescription = null,
                        tint = palette.bgDeep,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Fuel stops",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    plan?.let {
                        Text(
                            it.summary,
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", color = palette.neonGreen, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (plan?.prioritizeHighway == true) {
                Spacer(Modifier.height(10.dp))
                StatusChip(
                    label = "Highway priority",
                    tint = palette.neonBlue
                )
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = palette.neonGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("Scanning nearby stations…", color = palette.textSecondary)
                        }
                    }
                }

                stations.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(palette.bgPanel, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.LocalGasStation,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "No stations nearby",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp
                            )
                            Text(
                                "Need a GPS fix, or adjust Fuel preferences.",
                                color = palette.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        "Ranked for your ride",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(stations, key = { _, item -> item.recommendation.id }) { index, ranked ->
                            StationCard(
                                rank = index + 1,
                                station = ranked.recommendation,
                                preferences = preferences,
                                preferredOctanes = preferredOctanes,
                                palette = palette,
                                onGo = { onGo(ranked.recommendation) },
                                onDetails = { detailsStation = ranked.recommendation }
                            )
                        }
                        item {
                            Text(
                                "Hours and photos come from Google Places when available.",
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    detailsStation?.let { station ->
        ModalBottomSheet(
            onDismissRequest = {
                detailsStation = null
                onClearDetails()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.bgDeep
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
            ) {
                PlaceHeroImage(
                    photo = googleDetails?.photoBitmap,
                    mapPreview = googleDetails?.mapPreviewBitmap,
                    loading = googleDetailsLoading,
                    palette = palette
                )

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        googleDetails?.name ?: station.name,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip(
                            label = formatDistance(station.distanceMeters),
                            tint = palette.neonBlue
                        )
                        StatusChip(
                            label = when {
                                googleDetails?.isOpenNow == true -> "Open now"
                                googleDetails?.isOpenNow == false -> "Closed now"
                                else -> openLabel(station.openStatus)
                            },
                            tint = when {
                                googleDetails?.isOpenNow == true -> palette.neonGreen
                                googleDetails?.isOpenNow == false -> palette.neonRed
                                station.openStatus == OpeningHoursEvaluator.Status.OPEN -> palette.neonGreen
                                station.openStatus == OpeningHoursEvaluator.Status.CLOSED -> palette.neonRed
                                else -> palette.routeAmber
                            }
                        )
                        if (station.hoursFromGoogle || googleDetails != null) {
                            StatusChip(label = "Google", tint = palette.neonBlue)
                        }
                        if (preferences.isPreferredBrand(station.brand ?: station.name)) {
                            StatusChip(label = "Preferred", tint = palette.neonGreen)
                        }
                        if (station.isHighwayAccessible) {
                            StatusChip(label = "Highway", tint = palette.neonBlue)
                        }
                    }

                    DetailCard(palette = palette) {
                        DetailLine(
                            "Address",
                            googleDetails?.address ?: station.address ?: "Unknown",
                            palette
                        )
                        val phone = googleDetails?.phone ?: station.phone
                        if (!phone.isNullOrBlank()) DetailLine("Phone", phone, palette)
                        val rating = googleDetails?.rating ?: station.rating
                        if (rating != null) {
                            val count = googleDetails?.ratingCount ?: station.ratingCount
                            DetailLine(
                                "Rating",
                                if (count != null) String.format(Locale.US, "%.1f · %d reviews", rating, count)
                                else String.format(Locale.US, "%.1f", rating),
                                palette
                            )
                        }
                        DetailLine("Octane", station.displayOctanes(preferredOctanes), palette)
                        station.brand?.let { DetailLine("Brand", it, palette) }
                        if (station.isHighwayAccessible) {
                            DetailLine("Access", "Highway / service area", palette)
                        }
                    }

                    val hours = googleDetails?.weekdayHours?.takeIf { it.isNotEmpty() }
                        ?.joinToString("\n")
                        ?: station.displayHours()
                    DetailCard(palette = palette) {
                        DetailLine(
                            if (googleDetails?.weekdayHours?.isNotEmpty() == true) "Hours (Google)" else "Hours",
                            hours,
                            palette
                        )
                        googleDetails?.websiteUri?.let { DetailLine("Website", it, palette) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                detailsStation = null
                                onClearDetails()
                            },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle)
                        ) {
                            Text("Close", color = palette.textSecondary)
                        }
                        Button(
                            onClick = {
                                val mapsUri = googleDetails?.googleMapsUri
                                val intent = if (!mapsUri.isNullOrBlank()) {
                                    Intent(Intent.ACTION_VIEW, mapsUri.toUri())
                                } else {
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}(${Uri.encode(station.name)})".toUri()
                                    )
                                }
                                context.startActivity(intent)
                                detailsStation = null
                                onClearDetails()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.neonBlue)
                        ) {
                            Icon(
                                Icons.Filled.Map,
                                contentDescription = null,
                                tint = palette.bgDeep,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Maps", color = palette.bgDeep, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                onGo(station)
                                detailsStation = null
                                onClearDetails()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.neonGreen)
                        ) {
                            Icon(
                                Icons.Filled.Navigation,
                                contentDescription = null,
                                tint = palette.bgDeep,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Go", color = palette.bgDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceHeroImage(
    photo: Bitmap?,
    mapPreview: Bitmap?,
    loading: Boolean,
    palette: AppPalette
) {
    val hero = photo ?: mapPreview
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.bgPanel)
    ) {
        when {
            hero != null -> {
                Image(
                    bitmap = hero.asImageBitmap(),
                    contentDescription = if (photo != null) "Place photo" else "Map preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    palette.bgDeep.copy(alpha = 0.55f)
                                )
                            )
                        )
                )
                Text(
                    if (photo != null) "Google Places" else "Map preview",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            loading -> {
                CircularProgressIndicator(
                    color = palette.neonGreen,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Map,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No map preview", color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationCard(
    rank: Int,
    station: PetrolStationRecommendation,
    preferences: PetrolPreferences,
    preferredOctanes: Set<Int>,
    palette: AppPalette,
    onGo: () -> Unit,
    onDetails: () -> Unit
) {
    val dimmed = station.openStatus == OpeningHoursEvaluator.Status.CLOSED
    val preferred = preferences.isPreferredBrand(station.brand ?: station.name)
    val accent = if (preferred) palette.neonGreen else palette.neonBlue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.55f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.14f),
                        palette.bgCard
                    )
                )
            )
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$rank",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                station.name,
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusChip(
                    label = formatDistance(station.distanceMeters),
                    tint = palette.neonBlue,
                    compact = true
                )
                StatusChip(
                    label = openLabel(station.openStatus),
                    tint = when (station.openStatus) {
                        OpeningHoursEvaluator.Status.OPEN -> palette.neonGreen
                        OpeningHoursEvaluator.Status.CLOSED -> palette.neonRed
                        OpeningHoursEvaluator.Status.UNKNOWN -> palette.routeAmber
                    },
                    compact = true
                )
                if (station.hoursFromGoogle) {
                    StatusChip(label = "Google", tint = palette.neonBlue, compact = true)
                }
                if (preferred) {
                    StatusChip(label = "Preferred", tint = palette.neonGreen, compact = true)
                }
                if (station.isHighwayAccessible) {
                    StatusChip(label = "Highway", tint = palette.neonBlue, compact = true)
                }
            }
            station.address?.let {
                Text(
                    it,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    station.displayOctanes(preferredOctanes),
                    color = when {
                        station.availableOctanes.isEmpty() -> palette.textSecondary
                        station.availableOctanes.intersect(preferredOctanes).isNotEmpty() -> palette.neonGreen
                        else -> palette.routeAmber
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                station.rating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = palette.routeAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            String.format(Locale.US, "%.1f", rating),
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGo,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.neonGreen),
                    contentPadding = ButtonDefaults.ContentPadding,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = palette.bgDeep,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Go", color = palette.bgDeep, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDetails,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.neonBlue.copy(alpha = 0.55f))
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = palette.neonBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Details", color = palette.neonBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
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
private fun StatusChip(
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
private fun DetailLine(label: String, value: String, palette: AppPalette) {
    Column {
        Text(label, color = palette.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, color = palette.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

private fun openLabel(status: OpeningHoursEvaluator.Status): String = when (status) {
    OpeningHoursEvaluator.Status.OPEN -> "Open"
    OpeningHoursEvaluator.Status.CLOSED -> "Closed"
    OpeningHoursEvaluator.Status.UNKNOWN -> "Hours ?"
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) String.format(Locale.US, "%.1f km", meters / 1000.0)
    else "${meters.toInt()} m"
