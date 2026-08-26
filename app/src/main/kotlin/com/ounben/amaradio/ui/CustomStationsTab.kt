package com.ounben.amaradio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.utils.StationIconProvider

@Composable
fun CustomStationsTab(
    viewModel: CustomStationsViewModel,
    onStationClick: (DataRadioStation) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var stationToEdit by remember { mutableStateOf<DataRadioStation?>(null) }
    var stationToDelete by remember { mutableStateOf<DataRadioStation?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.filteredStations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.searchpreference_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(uiState.filteredStations, key = { _, s -> s.StationUuid }) { index, station ->
                    StationItem(
                        station = station,
                        onClick = { onStationClick(station) },
                        onEditClick = { stationToEdit = station },
                        onDeleteClick = { stationToDelete = station },
                        onMoveUp = if (index > 0) { { viewModel.reorder(index, index - 1) } } else null,
                        onMoveDown = if (index < uiState.filteredStations.size - 1) { { viewModel.reorder(index, index + 1) } } else null
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_custom))
        }
    }

    if (showAddDialog) {
        AddEditCustomStationDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, iconUri ->
                viewModel.addCustomStation(name, url, iconUri)
                showAddDialog = false
            }
        )
    }

    stationToEdit?.let { station ->
        AddEditCustomStationDialog(
            station = station,
            onDismiss = { stationToEdit = null },
            onConfirm = { name, url, iconUri ->
                val updated = station.copy(Name = name, StreamUrl = url)
                viewModel.updateCustomStation(updated, iconUri)
                stationToEdit = null
            }
        )
    }

    stationToDelete?.let { station ->
        AlertDialog(
            onDismissRequest = { stationToDelete = null },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(station.Name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(station.StationUuid)
                    stationToDelete = null
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { stationToDelete = null }) { Text(stringResource(R.string.no)) }
            }
        )
    }
}

@Composable
fun StationItem(
    station: DataRadioStation,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(station.Name, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(station.StreamUrl, maxLines = 1) },
        leadingContent = {
            StationIcon(
                stationName = station.Name,
                stationUuid = station.StationUuid,
                iconUrl = station.IconUrl,
                modifier = Modifier.size(48.dp)
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (onMoveUp != null) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (onMoveDown != null) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun AddEditCustomStationDialog(
    station: DataRadioStation? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Uri?) -> Unit
) {
    var name by remember { mutableStateOf(station?.Name ?: "") }
    var url by remember { mutableStateOf(station?.StreamUrl ?: "") }
    var iconUri by remember { mutableStateOf<Uri?>(null) }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        iconUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (station == null) stringResource(R.string.action_add_custom) else stringResource(R.string.action_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.station_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.stream_url)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    val model = iconUri ?: station?.IconUrl?.ifEmpty { null }
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                    
                    Button(onClick = { photoPickerLauncher.launch("image/*") }) {
                        Text(stringResource(R.string.select_image))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onConfirm(name, url, iconUri) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
