package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.petrol.GooglePetrolDetails
import com.odys.mototriptracker.data.petrol.PetrolPreferences
import com.odys.mototriptracker.data.petrol.PetrolSearchPlan
import com.odys.mototriptracker.data.petrol.PetrolStationRecommendation
import com.odys.mototriptracker.data.petrol.RankedPetrolStation
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette

@OptIn(ExperimentalMaterial3Api::class)
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
        PetrolStationDetailsSheet(
            station = station,
            preferences = preferences,
            preferredOctanes = preferredOctanes,
            googleDetails = googleDetails,
            googleDetailsLoading = googleDetailsLoading,
            palette = palette,
            onDismiss = {
                detailsStation = null
                onClearDetails()
            },
            onGo = {
                onGo(station)
                detailsStation = null
                onClearDetails()
            },
        )
    }
}
