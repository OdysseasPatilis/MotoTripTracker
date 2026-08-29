package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.odys.mototriptracker.data.fuel.FuelService
import com.odys.mototriptracker.data.petrol.PetrolPreferences
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FuelSettingsSheet(
    fuelService: FuelService,
    petrolPreferences: PetrolPreferences,
    tankCapacity: Double,
    fuelRemaining: Double,
    consumption: Double,
    onDismiss: () -> Unit,
    palette: AppPalette = LocalAppPalette.current
) {
    var capacityText by remember(tankCapacity) { mutableStateOf("%.1f".format(tankCapacity)) }
    var remainingText by remember(fuelRemaining) { mutableStateOf("%.1f".format(fuelRemaining)) }
    var consumptionText by remember(consumption) { mutableStateOf("%.1f".format(consumption)) }
    val preferredBrands by petrolPreferences.preferredBrands.collectAsStateWithLifecycle()
    val preferredOctanes by petrolPreferences.preferredOctanes.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bgDeep
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "Fuel & Range",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("Done", color = palette.neonGreen) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = capacityText,
                onValueChange = { capacityText = it },
                label = { Text("Tank capacity (L)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = remainingText,
                onValueChange = { remainingText = it },
                label = { Text("Fuel remaining (L)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = consumptionText,
                onValueChange = { consumptionText = it },
                label = { Text("Consumption (L / 100 km)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Text("Preferred petrol", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "First selected brands are ranked first (e.g. Shell, then BP).",
                color = palette.textSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PetrolPreferences.CATALOG.forEach { brand ->
                    val selected = brand in preferredBrands
                    FilterChip(
                        selected = selected,
                        onClick = { petrolPreferences.toggleBrand(brand) },
                        label = { Text(brand) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.neonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = palette.neonGreen
                        )
                    )
                }
            }
            if (preferredBrands.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Order: ${preferredBrands.joinToString(" → ")}",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Preferred octane", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(95, 98, 100).forEach { octane ->
                    val selected = octane in preferredOctanes
                    FilterChip(
                        selected = selected,
                        onClick = { petrolPreferences.toggleOctane(octane) },
                        label = { Text("$octane") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.neonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = palette.neonGreen
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { fuelService.fillUp() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Fill up tank") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    capacityText.toDoubleOrNull()?.let(fuelService::setTankCapacityLiters)
                    remainingText.toDoubleOrNull()?.let(fuelService::setFuelRemainingLiters)
                    consumptionText.toDoubleOrNull()?.let(fuelService::setConsumptionLPer100Km)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}
