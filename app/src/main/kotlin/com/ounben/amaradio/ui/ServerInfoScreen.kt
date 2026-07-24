package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation

@Composable
fun ServerInfoScreen(
    viewModel: ServerInfoViewModel, 
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Internal Back Button Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
            }
            Text(
                text = stringResource(R.string.settings_statistics),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = AmaradioAmber,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(selectedTabIndex),
                    color = AmaradioAmber
                )
            }
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Global API") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text(stringResource(R.string.database_summary_title)) }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> GlobalStatsTab(uiState, onRefresh = { viewModel.loadStatistics(true) })
                1 -> LocalDbTab(
                    uiState = uiState, 
                    onSync = { viewModel.triggerManualSync() },
                    onStationClick = onStationClick,
                    onFavoriteClick = onFavoriteClick
                )
            }
        }
    }
}

@Composable
fun GlobalStatsTab(uiState: ServerInfoViewModel.ServerInfoUiState, onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = AmaradioAmber
            )
        } else if (uiState.error != null) {
            Text(text = uiState.error, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.statistics) { item ->
                    Column {
                        Text(
                            text = item.Name, 
                            style = MaterialTheme.typography.labelMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.Value, 
                            style = MaterialTheme.typography.bodyLarge, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
                    }
                }
                item {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmaradioAmber)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh API Stats")
                    }
                }
            }
        }
    }
}

@Composable
fun LocalDbTab(
    uiState: ServerInfoViewModel.ServerInfoUiState, 
    onSync: () -> Unit,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.database_summary_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    InfoRow(stringResource(R.string.database_total_stations), uiState.localStationCount.toString())
                    InfoRow(stringResource(R.string.database_last_sync), uiState.lastSyncTime)
                }
            }
        }

        item {
            Button(
                onClick = onSync,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                enabled = !uiState.isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = AmaradioAmber)
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isSyncing) stringResource(R.string.database_syncing) else stringResource(R.string.database_update_now))
            }
        }

        item {
            Text(
                text = "Recent Updates (Local)", 
                style = MaterialTheme.typography.titleSmall, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (uiState.recentChanges.isEmpty()) {
            item {
                Text(
                    text = "No recent changes recorded.", 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(uiState.recentChanges, key = { it.StationUuid }) { station ->
                Column {
                    StationListItem(
                        station = station,
                        isFavorite = uiState.favoriteIds.contains(station.StationUuid),
                        onClick = { onStationClick(station) },
                        onFavoriteClick = { onFavoriteClick(station) },
                        onLongClick = { /* No options dialog */ }
                    )
                    Text(
                        text = "Change Time: ${station.LastChangeTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmaradioAmber,
                        modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
