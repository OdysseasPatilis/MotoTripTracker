package com.odys.mototriptracker.ui.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odys.mototriptracker.data.navigation.NavigationSearchResult
import com.odys.mototriptracker.ui.theme.AppPalette
import com.odys.mototriptracker.ui.theme.LocalAppPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationSearchSheet(
    query: String,
    results: List<NavigationSearchResult>,
    isSearching: Boolean = false,
    searchError: String? = null,
    onQueryChange: (String) -> Unit,
    onSelectResult: (NavigationSearchResult) -> Unit,
    onDismiss: () -> Unit,
    palette: AppPalette = LocalAppPalette.current
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.bgDeep
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set destination",
                    color = palette.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.neonGreen, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                placeholder = {
                    Text(
                        "Search places or address",
                        color = palette.textSecondary,
                        fontSize = 17.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = palette.neonGreen
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                    focusedBorderColor = palette.neonGreen,
                    unfocusedBorderColor = palette.bgPanel,
                    cursorColor = palette.neonGreen,
                    focusedContainerColor = palette.bgCard,
                    unfocusedContainerColor = palette.bgCard
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(16.dp))

            when {
                results.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(results, key = { "${it.placeId}-${it.title}" }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(palette.bgCard, RoundedCornerShape(14.dp))
                                    .clickable {
                                        keyboard?.hide()
                                        onSelectResult(result)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = palette.neonBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.title,
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 17.sp
                                    )
                                    if (result.subtitle.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = result.subtitle,
                                            color = palette.textSecondary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = palette.neonGreen)
                            Spacer(Modifier.height(12.dp))
                            Text("Searching…", color = palette.textSecondary, fontSize = 16.sp)
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = searchError
                                ?: if (query.isBlank()) {
                                    "Type an address, city, or place name."
                                } else {
                                    "No matches yet."
                                },
                            color = palette.textSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
